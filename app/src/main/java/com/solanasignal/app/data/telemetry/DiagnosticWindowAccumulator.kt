package com.solanasignal.app.data.telemetry

import com.solanasignal.app.data.pumpportal.TradeSide
import java.util.ArrayDeque

private const val HISTORY_MS = 24 * 60 * 60_000L
private const val MAX_EVENTS_PER_MINT = 512
private const val MOVEMENT_STALE_AFTER_MS = 15_000L

private data class DiagnosticTradePoint(
    val receivedAtMs: Long,
    val side: TradeSide,
    val priceUsd: Double?,
    val marketCapUsd: Double?,
    val amountUsd: Double?,
    val trader: String?
)

data class DiagnosticWindowMetrics(
    val windowSeconds: Int,
    val covered: Boolean,
    val sampleCount: Int?,
    val buyCount: Int?,
    val sellCount: Int?,
    val buyVolumeUsd: Double?,
    val sellVolumeUsd: Double?,
    val tradeFrequencyPerSecond: Double?,
    val buyFrequencyPerSecond: Double?,
    val sellFrequencyPerSecond: Double?,
    val buyPressurePct: Double?,
    val sellPressurePct: Double?,
    val priceVelocityPct: Double?,
    val marketCapVelocityPct: Double?,
    val priceAccelerationPctPerSecond: Double?,
    val marketCapAccelerationPctPerSecond: Double?,
    val tradeFrequencyAccelerationPerSecond: Double?,
    val uniqueBuyers: Int?,
    val uniqueSellers: Int?,
    val buyerPersistencePct: Double?,
    val tradePersistencePct: Double?
) {
    fun asMap(): Map<String, Any?> = mapOf(
        "windowSeconds" to windowSeconds, "covered" to covered, "sampleCount" to sampleCount,
        "buyCount" to buyCount, "sellCount" to sellCount,
        "buyVolumeUsd" to buyVolumeUsd, "sellVolumeUsd" to sellVolumeUsd,
        "tradeFrequencyPerSecond" to tradeFrequencyPerSecond,
        "buyFrequencyPerSecond" to buyFrequencyPerSecond,
        "sellFrequencyPerSecond" to sellFrequencyPerSecond,
        "buyPressurePct" to buyPressurePct, "sellPressurePct" to sellPressurePct,
        "priceVelocityPct" to priceVelocityPct, "marketCapVelocityPct" to marketCapVelocityPct,
        "priceAccelerationPctPerSecond" to priceAccelerationPctPerSecond,
        "marketCapAccelerationPctPerSecond" to marketCapAccelerationPctPerSecond,
        "tradeFrequencyAccelerationPerSecond" to tradeFrequencyAccelerationPerSecond,
        "uniqueBuyers" to uniqueBuyers, "uniqueSellers" to uniqueSellers,
        "buyerPersistencePct" to buyerPersistencePct, "tradePersistencePct" to tradePersistencePct
    )
}

/** Diagnostic-only rolling observations. It never feeds production metrics or signal decisions. */
class DiagnosticWindowAccumulator {
    private data class TokenHistory(
        val events: ArrayDeque<DiagnosticTradePoint> = ArrayDeque(),
        var continuousCoverageStartedAtMs: Long? = null,
        var lostThroughMs: Long? = null,
        var lastSourceTimestampMs: Long? = null
    )

    private val histories = LinkedHashMap<String, TokenHistory>()

    @Synchronized
    fun beginCoverage(mints: Collection<String>, atMs: Long) {
        mints.forEach { mint ->
            histories.getOrPut(mint) { TokenHistory() }.apply {
                events.clear()
                continuousCoverageStartedAtMs = atMs
                lostThroughMs = null
                lastSourceTimestampMs = null
            }
        }
    }

    @Synchronized
    fun endCoverage(mints: Collection<String>, atMs: Long) {
        mints.forEach { mint -> histories.remove(mint) }
    }

    @Synchronized
    fun resetAllCoverage(atMs: Long) {
        histories.values.forEach {
            it.events.clear()
            it.continuousCoverageStartedAtMs = null
            it.lostThroughMs = null
            it.lastSourceTimestampMs = null
        }
    }

    @Synchronized
    fun observe(
        mint: String,
        receivedAtMs: Long,
        side: TradeSide,
        priceUsd: Double?,
        marketCapUsd: Double?,
        amountUsd: Double?,
        sourceTimestampMs: Long? = null,
        trader: String? = null
    ): String? {
        val history = histories.getOrPut(mint) { TokenHistory() }
        val priorSourceTimestamp = history.lastSourceTimestampMs
        val order = if (sourceTimestampMs != null && priorSourceTimestamp != null) {
            when {
                sourceTimestampMs < priorSourceTimestamp -> "OUT_OF_ORDER"
                sourceTimestampMs - priorSourceTimestamp > 30_000L -> "SOURCE_TIMESTAMP_GAP"
                else -> null
            }
        } else null
        if (sourceTimestampMs != null && (priorSourceTimestamp == null || sourceTimestampMs > priorSourceTimestamp)) {
            history.lastSourceTimestampMs = sourceTimestampMs
        }
        val cutoff = receivedAtMs - HISTORY_MS
        history.events.removeIf { it.receivedAtMs < cutoff }
        history.events.addLast(DiagnosticTradePoint(receivedAtMs, side, priceUsd, marketCapUsd, amountUsd, trader))
        while (history.events.size > MAX_EVENTS_PER_MINT) {
            val oldest = history.events.minByOrNull { it.receivedAtMs } ?: break
            history.events.remove(oldest)
            history.lostThroughMs = maxOf(history.lostThroughMs ?: Long.MIN_VALUE, oldest.receivedAtMs)
        }
        return order
    }

    @Synchronized
    fun snapshot(mint: String, nowMs: Long): List<DiagnosticWindowMetrics> {
        val history = histories[mint] ?: return WINDOWS.map { unknown(it) }
        return WINDOWS.map { window -> calculate(history, window, nowMs) }
    }

    private fun calculate(history: TokenHistory, windowSeconds: Int, nowMs: Long): DiagnosticWindowMetrics {
        val windowMs = windowSeconds * 1_000L
        val start = nowMs - windowMs
        val covered = history.continuousCoverageStartedAtMs?.let { it <= start } == true &&
            (history.lostThroughMs == null || history.lostThroughMs!! < start)
        val events = history.events.filter { it.receivedAtMs in start..nowMs }.sortedBy { it.receivedAtMs }
        if (!covered) return unknown(windowSeconds, events.size)
        val buys = events.filter { it.side == TradeSide.BUY }
        val sells = events.filter { it.side == TradeSide.SELL }
        val uniqueBuyers = buys.mapNotNull { it.trader }.toSet().size.takeIf { buys.all { event -> event.trader != null } }
        val uniqueSellers = sells.mapNotNull { it.trader }.toSet().size.takeIf { sells.all { event -> event.trader != null } }
        val prices = events.mapNotNull { event -> event.priceUsd?.takeIf { it > 0.0 }?.let { event.receivedAtMs to it } }
        val mc = events.mapNotNull { event -> event.marketCapUsd?.takeIf { it > 0.0 }?.let { event.receivedAtMs to it } }
        val priceFresh = prices.lastOrNull()?.first?.let { nowMs - it in 0..MOVEMENT_STALE_AFTER_MS } == true
        val marketCapFresh = mc.lastOrNull()?.first?.let { nowMs - it in 0..MOVEMENT_STALE_AFTER_MS } == true
        val half = start + windowMs / 2
        val priceVelocity = if (priceFresh) movementPct(prices) else null
        val mcVelocity = if (marketCapFresh) movementPct(mc) else null
        val firstHalfPrices = prices.filter { it.first < half }
        val secondHalfPrices = prices.filter { it.first >= half }
        val firstHalfMc = mc.filter { it.first < half }
        val secondHalfMc = mc.filter { it.first >= half }
        val firstHalfEvents = events.count { it.receivedAtMs < half }
        val secondHalfEvents = events.size - firstHalfEvents
        val firstHalfBuy = buys.count { it.receivedAtMs < half }
        val secondHalfBuy = buys.size - firstHalfBuy
        val firstHalfSell = sells.count { it.receivedAtMs < half }
        val secondHalfSell = sells.size - firstHalfSell
        val total = events.size
        val buyVol = volume(buys)
        val sellVol = volume(sells)
        return DiagnosticWindowMetrics(
            windowSeconds = windowSeconds,
            covered = true,
            sampleCount = total,
            buyCount = buys.size,
            sellCount = sells.size,
            buyVolumeUsd = buyVol,
            sellVolumeUsd = sellVol,
            tradeFrequencyPerSecond = total.toDouble() / windowSeconds,
            buyFrequencyPerSecond = buys.size.toDouble() / windowSeconds,
            sellFrequencyPerSecond = sells.size.toDouble() / windowSeconds,
            buyPressurePct = total.takeIf { it > 0 }?.let { buys.size * 100.0 / it },
            sellPressurePct = total.takeIf { it > 0 }?.let { sells.size * 100.0 / it },
            priceVelocityPct = priceVelocity,
            marketCapVelocityPct = mcVelocity,
            priceAccelerationPctPerSecond = if (priceFresh) accelerationPct(firstHalfPrices, secondHalfPrices, windowSeconds / 2.0) else null,
            marketCapAccelerationPctPerSecond = if (marketCapFresh) accelerationPct(firstHalfMc, secondHalfMc, windowSeconds / 2.0) else null,
            tradeFrequencyAccelerationPerSecond = ((secondHalfEvents - firstHalfEvents).toDouble() / (windowSeconds / 2.0)),
            uniqueBuyers = uniqueBuyers,
            uniqueSellers = uniqueSellers,
            buyerPersistencePct = persistence(buys.map { it.receivedAtMs }, start, windowMs),
            tradePersistencePct = persistence(events.map { it.receivedAtMs }, start, windowMs),
        )
    }

    private fun unknown(windowSeconds: Int, @Suppress("UNUSED_PARAMETER") observedSamples: Int? = null) = DiagnosticWindowMetrics(
        windowSeconds = windowSeconds, covered = false, sampleCount = null, buyCount = null, sellCount = null,
        buyVolumeUsd = null, sellVolumeUsd = null, tradeFrequencyPerSecond = null,
        buyFrequencyPerSecond = null, sellFrequencyPerSecond = null, buyPressurePct = null,
        sellPressurePct = null, priceVelocityPct = null, marketCapVelocityPct = null,
        priceAccelerationPctPerSecond = null, marketCapAccelerationPctPerSecond = null,
        tradeFrequencyAccelerationPerSecond = null, uniqueBuyers = null, uniqueSellers = null,
        buyerPersistencePct = null, tradePersistencePct = null
    )

    private fun persistence(times: List<Long>, start: Long, windowMs: Long): Double {
        val buckets = 4
        val bucketMs = windowMs / buckets
        val active = (0 until buckets).count { bucket ->
            times.any { time -> time >= start + bucket * bucketMs && time < start + (bucket + 1) * bucketMs }
        }
        return active * 100.0 / buckets
    }

    private fun volume(events: List<DiagnosticTradePoint>): Double? = when {
        events.isEmpty() -> 0.0
        events.any { it.amountUsd == null || !it.amountUsd.isFinite() } -> null
        else -> events.sumOf { it.amountUsd!! }
    }

    private fun pctChange(first: Double?, last: Double?): Double? =
        if (first != null && last != null && first > 0.0) (last - first) / first * 100.0 else null

    private fun movementPct(points: List<Pair<Long, Double>>): Double? {
        val first = points.firstOrNull() ?: return null
        val last = points.lastOrNull() ?: return null
        if (first.first >= last.first) return null
        return pctChange(first.second, last.second)
    }

    private fun accelerationPct(first: List<Pair<Long, Double>>, second: List<Pair<Long, Double>>, halfSeconds: Double): Double? {
        if (first.size < 2 || second.size < 2) return null
        val firstVelocity = movementPct(first) ?: return null
        val secondVelocity = movementPct(second) ?: return null
        return (secondVelocity - firstVelocity) / halfSeconds
    }

    companion object { val WINDOWS = listOf(5, 10, 15, 30, 60, 120, 300) }
}
