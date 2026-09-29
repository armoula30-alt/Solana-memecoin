package com.solanasignal.app.domain.evidence

import com.solanasignal.app.data.settings.EngineConfig
import com.solanasignal.app.domain.metrics.WindowMetrics
import kotlin.math.abs
import kotlin.math.max

/** Independent analytical outputs. Missing inputs remain unknown and never become positive evidence. */
data class SignalEvidence(
    val momentumScore: Int?,
    val collapseRisk: Int?,
    val signalQuality: Int?,
    val dataConfidence: Int,
    val marketCapVelocityPct: Double?,
    val marketCapAccelerationPct: Double?,
    val classification: String,
    val reasons: List<String>,
    val warnings: List<String>,
    val featureValues: Map<String, Double?>
)

class SignalEvidenceEngine {
    fun evaluate(
        windows: Map<Int, WindowMetrics>,
        marketCapUsd: Double?,
        liquidityUsd: Double?,
        nowMs: Long,
        latestTradeAtMs: Long?,
        marketCapHistory: List<Pair<Long, Double>> = emptyList(),
        config: EngineConfig
    ): SignalEvidence {
        val m10 = windows[10]
        val m30 = windows[30]
        val m60 = windows[60]
        val m180 = windows[180]
        val m300 = windows[300]
        val availableWindows = windows.values.count { it.totalTrades > 0 }
        val latest = listOfNotNull(m10, m30, m60, m180, m300).maxByOrNull { it.totalTrades }

        val pressure = m60?.let { buyPressure(it) }
        val buyAcceleration = acceleration(m10?.buyVolumeUsd, m30?.buyVolumeUsd)
        val buyerGrowth = growth(m10?.uniqueBuyers, m60?.uniqueBuyers)
        val priceMomentum = m60?.priceChangePct?.let { boundedPositive(it, 25.0) }
        val liquidityQuality = liquidityScore(marketCapUsd, liquidityUsd, m60)
        val mcVelocity = marketCapVelocity(marketCapHistory, nowMs, 60_000L)
        val mcOlderVelocity = marketCapVelocity(marketCapHistory, nowMs - 60_000L, 60_000L)
        val mcAcceleration = if (mcVelocity != null && mcOlderVelocity != null) mcVelocity - mcOlderVelocity else null
        val mcMomentum = mcVelocity?.let { velocityScore(it) }
        val antiSpike = m60?.let { largestShare(it) <= config.antiSpikeLargestTradeShare && it.uniqueBuyers >= config.antiSpikeMinimumUniqueBuyers }

        val components = listOfNotNull(
            pressure,
            buyAcceleration,
            buyerGrowth,
            priceMomentum,
            liquidityQuality,
            mcMomentum
        )
        val momentum = if (components.size >= 2) weightedAverage(components) else null

        val riskFindings = mutableListOf<String>()
        var risk = 0
        if (liquidityUsd != null && liquidityUsd < config.minimumLiquidityUsd) { risk += 25; riskFindings += "Liquidity below configured minimum" }
        if (m60 != null && m60.sellVolumeUsd > m60.buyVolumeUsd && m60.sells > m60.buys) { risk += 25; riskFindings += "Sell pressure dominates the recent window" }
        if (m10 != null && m60 != null && m10.totalTrades > 0 && m60.totalTrades > 0 && m10.totalTrades > m60.totalTrades * 0.7) { risk += 15; riskFindings += "Short-lived activity spike" }
        if (antiSpike == false) { risk += 20; riskFindings += "Recent flow is concentrated or lacks independent buyers" }
        if (marketCapUsd != null && liquidityUsd != null && liquidityUsd > 0 && marketCapUsd / liquidityUsd > 100.0) { risk += 20; riskFindings += "Market-cap/liquidity imbalance" }
        val collapseRisk = if (risk == 0 && availableWindows == 0) null else risk.coerceIn(0, 100)

        val freshness = latestTradeAtMs?.let { ((nowMs - it).coerceAtLeast(0) / 1000).let { age -> (100 - age * 100 / config.dataFreshnessSeconds).coerceIn(0, 100) } }
        val completeness = (components.size * 100 / 5).coerceIn(0, 100)
        val observations = (availableWindows * 100 / 5).coerceIn(0, 100)
        val confidence = listOfNotNull(freshness, completeness, observations).average().toInt().coerceIn(0, 100)
        val quality = momentum?.let { ((it * 0.55) + (confidence * 0.30) + ((100 - (collapseRisk ?: 50)) * 0.15)).toInt().coerceIn(0, 100) }

        val reasons = buildList {
            if ((pressure ?: 0.0) >= 65) add("Buy pressure is positive")
            if ((buyAcceleration ?: 0.0) >= 60) add("Buy volume is accelerating")
            if ((buyerGrowth ?: 0.0) >= 60) add("Unique buyer growth is increasing")
            if ((priceMomentum ?: 0.0) >= 60) add("Price structure is positive")
            if ((mcVelocity ?: 0.0) > 5.0) add("Market cap is rising ${"%.1f".format(mcVelocity)}%/min")
            if ((mcAcceleration ?: 0.0) > 2.0) add("Market-cap growth is accelerating")
            if (liquidityQuality != null && liquidityQuality >= 60) add("Liquidity appears coherent with activity")
        }
        val warnings = buildList {
            addAll(riskFindings)
            if (components.size < 3) add("Data confidence limited by missing features")
            if (latestTradeAtMs == null) add("Trade freshness is UNKNOWN")
        }
        val classification = when {
            momentum == null || confidence < 25 -> "DATA INSUFFICIENT"
            collapseRisk != null && collapseRisk >= config.collapseRiskThreshold -> "COLLAPSE RISK"
            momentum >= config.strongMomentumThreshold && (collapseRisk ?: 0) >= config.highRiskThreshold -> "HIGH MOMENTUM / HIGH RISK"
            momentum >= config.strongMomentumThreshold -> "STRONG MOMENTUM"
            momentum >= config.earlyMomentumThreshold -> "EARLY MOMENTUM"
            momentum >= 40 -> "WATCH"
            else -> "NO SIGNAL"
        }
        return SignalEvidence(momentum?.toInt(), collapseRisk, quality, confidence, mcVelocity, mcAcceleration, classification, reasons, warnings,
            mapOf("buyPressure" to pressure, "buyAcceleration" to buyAcceleration, "uniqueBuyerGrowth" to buyerGrowth, "priceMomentum" to priceMomentum, "liquidityQuality" to liquidityQuality, "marketCapVelocityPctPerMinute" to mcVelocity, "marketCapAccelerationPctPerMinute" to mcAcceleration))
    }

    private fun buyPressure(m: WindowMetrics): Double = if (m.buys + m.sells == 0) 0.0 else m.buys.toDouble() / (m.buys + m.sells) * 100.0
    private fun growth(short: Int?, long: Int?): Double? = if (short != null && long != null && long > 0) (short.toDouble() / long * 100.0).coerceIn(0.0, 100.0) else null
    private fun acceleration(short: Double?, long: Double?): Double? = if (short != null && long != null && long > 0) (short / long * 100.0).coerceIn(0.0, 100.0) else null
    private fun boundedPositive(value: Double, maxPositive: Double): Double = (50.0 + value / maxPositive * 50.0).coerceIn(0.0, 100.0)
    private fun liquidityScore(mc: Double?, liquidity: Double?, m: WindowMetrics?): Double? {
        if (liquidity == null) return null
        val base = (liquidity / 20_000.0 * 100.0).coerceIn(0.0, 100.0)
        val stress = if (m != null && liquidity > 0) ((m.buyVolumeUsd + m.sellVolumeUsd) / liquidity * 20.0).coerceIn(0.0, 30.0) else 0.0
        return (base - stress).coerceIn(0.0, 100.0)
    }
    private fun largestShare(m: WindowMetrics): Double = if (m.buyVolumeUsd + m.sellVolumeUsd <= 0) 1.0 else max(m.largestBuyUsd, m.largestSellUsd) / (m.buyVolumeUsd + m.sellVolumeUsd)
    private fun weightedAverage(values: List<Double>): Double = values.average().coerceIn(0.0, 100.0)
    private fun marketCapVelocity(history: List<Pair<Long, Double>>, atMs: Long, lookbackMs: Long): Double? {
        val current = history.lastOrNull { it.first <= atMs } ?: return null
        val previous = history.lastOrNull { it.first <= atMs - lookbackMs } ?: return null
        if (previous.second <= 0.0) return null
        return ((current.second - previous.second) / previous.second) * 100.0
    }
    private fun velocityScore(value: Double): Double = (50.0 + value * 5.0).coerceIn(0.0, 100.0)
}
