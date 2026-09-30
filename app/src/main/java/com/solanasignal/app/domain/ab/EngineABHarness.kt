package com.solanasignal.app.domain.ab

import com.solanasignal.app.domain.advanced.AdvancedSignalEngineB
import com.solanasignal.app.domain.advanced.AdvancedSignalResult
import com.solanasignal.app.domain.live.LiveMarketState
import com.solanasignal.app.domain.metrics.WindowMetrics

/** A consumer of the authoritative state. Baseline A remains supplied by existing production code. */
fun interface LiveMarketStateConsumer<T> {
    fun evaluate(state: LiveMarketState): T
}

data class EngineABResult<A>(
    val state: LiveMarketState,
    val baselineA: A,
    val advancedB: AdvancedSignalResult
)

/**
 * Offline/replay harness only. It deliberately does not replace the production SignalEngine
 * or enable Advanced B. Both consumers receive the same object reference.
 */
class EngineABHarness<A>(
    private val baselineAConsumer: LiveMarketStateConsumer<A>,
    private val advancedB: AdvancedSignalEngineB = AdvancedSignalEngineB()
) {
    fun evaluate(state: LiveMarketState, nowMs: Long): EngineABResult<A> {
        val baseline = baselineAConsumer.evaluate(state)
        val advanced = advancedB.evaluate(state, nowMs)
        return EngineABResult(state, baseline, advanced)
    }
}

/** Converts a shared window to the legacy metrics shape without inventing unavailable values. */
object BaselineAWindowProjection {
    fun project(state: LiveMarketState, seconds: Int = 300): WindowMetrics? {
        val window = state.windows[seconds] ?: return null
        val buys = window.buyCount ?: return null
        val sells = window.sellCount ?: return null
        val buyVolume = window.buyVolumeUsd ?: return null
        val sellVolume = window.sellVolumeUsd ?: return null
        return WindowMetrics(
            windowSeconds = seconds,
            totalTrades = window.observationCount,
            buys = buys,
            sells = sells,
            uniqueBuyers = window.uniqueBuyers ?: return null,
            uniqueSellers = window.uniqueSellers ?: return null,
            buyVolumeUsd = buyVolume,
            sellVolumeUsd = sellVolume,
            avgBuySizeUsd = if (buys > 0) buyVolume / buys else 0.0,
            avgSellSizeUsd = if (sells > 0) sellVolume / sells else 0.0,
            largestBuyUsd = 0.0,
            largestSellUsd = 0.0,
            latestPriceUsd = window.priceUsd,
            priceChangePct = window.priceChangePct,
            volumeVelocity = state.volumeVelocity,
            buyerVelocity = state.buyerGrowth,
            sellerVelocity = state.sellerGrowth
        )
    }
}
