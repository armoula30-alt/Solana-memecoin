package com.solanasignal.app.domain.metrics

import com.solanasignal.app.data.pumpportal.TradeSide
import java.util.concurrent.ConcurrentHashMap

data class WindowMetrics(
    val windowSeconds: Int,
    val totalTrades: Int,
    val buys: Int,
    val sells: Int,
    val uniqueBuyers: Int,
    val uniqueSellers: Int,
    val buyVolumeUsd: Double,
    val sellVolumeUsd: Double,
    val avgBuySizeUsd: Double,
    val avgSellSizeUsd: Double,
    val largestBuyUsd: Double,
    val largestSellUsd: Double,
    val latestPriceUsd: Double?,
    val priceChangePct: Double?,      // null = INSUFFICIENT DATA (spec #15)
    val volumeVelocity: Double?,      // null = N/A when no prior comparable window
    val buyerVelocity: Double?,
    val sellerVelocity: Double?
) {
    val buyerSellerRatio: Double get() = uniqueBuyers.toDouble() / maxOf(uniqueSellers, 1)          // spec #13
    val buySellVolumeRatio: Double get() = buyVolumeUsd / maxOf(sellVolumeUsd, 1.0)                  // spec #14
}

private const val WINDOW_10S = 10
private const val WINDOW_30S = 30
private const val WINDOW_1M = 60
private const val WINDOW_3M = 180
private const val WINDOW_5M = 300
private const val WINDOW_10M = 600
val TRACKED_WINDOWS = listOf(WINDOW_10S, WINDOW_30S, WINDOW_1M, WINDOW_3M, WINDOW_5M, WINDOW_10M)

/**
 * Maintains an in-memory rolling trade buffer per token and computes the metrics
 * required by spec #11/#13/#14/#15/#16. All numbers come only from trades actually
 * received; velocity/price-change are null ("N/A" / "INSUFFICIENT DATA") when there
 * isn't yet a comparable prior window - never fabricated (spec #15).
 */
class MetricsEngine {

    private data class TradePoint(val side: TradeSide, val trader: String?, val amountUsd: Double?, val priceUsd: Double?, val ts: Long)

    private val buffers = ConcurrentHashMap<String, MutableList<TradePoint>>()
    // Keep a short history of computed 1m volumes to derive velocity (current/previous window).
    private val previousWindowVolume = ConcurrentHashMap<String, MutableMap<Int, Double>>()
    private val previousWindowBuyers = ConcurrentHashMap<String, MutableMap<Int, Int>>()
    private val previousWindowSellers = ConcurrentHashMap<String, MutableMap<Int, Int>>()
    private val firstPriceInWindow = ConcurrentHashMap<String, MutableMap<Int, Double>>()

    fun record(mint: String, side: TradeSide, trader: String?, amountUsd: Double?, priceUsd: Double?, timestampEpochMs: Long) {
        val list = buffers.getOrPut(mint) { mutableListOf() }
        synchronized(list) {
            list.add(TradePoint(side, trader, amountUsd, priceUsd, timestampEpochMs))
            // Trim anything older than the largest tracked window to bound memory.
            val cutoff = timestampEpochMs - WINDOW_10M * 1000L
            list.removeAll { it.ts < cutoff }
        }
    }

    fun dropToken(mint: String) {
        buffers.remove(mint)
        previousWindowVolume.remove(mint)
        previousWindowBuyers.remove(mint)
        previousWindowSellers.remove(mint)
        firstPriceInWindow.remove(mint)
    }

    /** Computes metrics for every tracked window for one token, at time [nowMs]. */
    fun computeAll(mint: String, nowMs: Long): Map<Int, WindowMetrics> {
        val list = buffers[mint] ?: return emptyMap()
        val snapshot = synchronized(list) { list.toList() }
        return TRACKED_WINDOWS.associateWith { windowSec -> compute(mint, snapshot, windowSec, nowMs) }
    }

    private fun compute(mint: String, all: List<TradePoint>, windowSeconds: Int, nowMs: Long): WindowMetrics {
        val since = nowMs - windowSeconds * 1000L
        val inWindow = all.filter { it.ts >= since }

        val buys = inWindow.filter { it.side == TradeSide.BUY }
        val sells = inWindow.filter { it.side == TradeSide.SELL }
        val buyVolume = buys.sumOf { it.amountUsd ?: 0.0 }
        val sellVolume = sells.sumOf { it.amountUsd ?: 0.0 }
        val uniqueBuyers = buys.mapNotNull { it.trader }.toSet().size
        val uniqueSellers = sells.mapNotNull { it.trader }.toSet().size

        val latestPrice = inWindow.lastOrNull { it.priceUsd != null }?.priceUsd
        val firstPrice = inWindow.firstOrNull { it.priceUsd != null }?.priceUsd
        val priceChangePct = if (firstPrice != null && latestPrice != null && firstPrice != 0.0) {
            ((latestPrice - firstPrice) / firstPrice) * 100.0
        } else null // spec #15/#16: insufficient data -> null, never fabricated

        // Velocity = current window volume / previous comparable window volume.
        val prevVolMap = previousWindowVolume.getOrPut(mint) { mutableMapOf() }
        val prevVol = prevVolMap[windowSeconds]
        val volumeVelocity = if (prevVol != null && prevVol > 0.0) (buyVolume + sellVolume) / prevVol else null
        prevVolMap[windowSeconds] = buyVolume + sellVolume

        val prevBuyerMap = previousWindowBuyers.getOrPut(mint) { mutableMapOf() }
        val prevBuyers = prevBuyerMap[windowSeconds]
        val buyerVelocity = if (prevBuyers != null && prevBuyers > 0) uniqueBuyers.toDouble() / prevBuyers else null
        prevBuyerMap[windowSeconds] = uniqueBuyers

        val prevSellerMap = previousWindowSellers.getOrPut(mint) { mutableMapOf() }
        val prevSellers = prevSellerMap[windowSeconds]
        val sellerVelocity = if (prevSellers != null && prevSellers > 0) uniqueSellers.toDouble() / prevSellers else null
        prevSellerMap[windowSeconds] = uniqueSellers

        return WindowMetrics(
            windowSeconds = windowSeconds,
            totalTrades = inWindow.size,
            buys = buys.size,
            sells = sells.size,
            uniqueBuyers = uniqueBuyers,
            uniqueSellers = uniqueSellers,
            buyVolumeUsd = buyVolume,
            sellVolumeUsd = sellVolume,
            avgBuySizeUsd = if (buys.isNotEmpty()) buyVolume / buys.size else 0.0,
            avgSellSizeUsd = if (sells.isNotEmpty()) sellVolume / sells.size else 0.0,
            largestBuyUsd = buys.mapNotNull { it.amountUsd }.maxOrNull() ?: 0.0,
            largestSellUsd = sells.mapNotNull { it.amountUsd }.maxOrNull() ?: 0.0,
            latestPriceUsd = latestPrice,
            priceChangePct = priceChangePct,
            volumeVelocity = volumeVelocity,
            buyerVelocity = buyerVelocity,
            sellerVelocity = sellerVelocity
        )
    }
}
