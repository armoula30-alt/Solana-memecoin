package com.solanasignal.app.domain.momentum

import com.solanasignal.app.domain.metrics.WindowMetrics
import kotlin.math.abs
import kotlin.math.max


enum class MomentumState { DORMANT, BUILDING, EARLY, STRONG, EXTREME, WEAKENING, COLLAPSING }

data class MomentumComponent(val name: String, val value: Double?, val explanation: String)

data class AdvancedMomentumResult(
    val score: Int,
    val state: MomentumState,
    val persistence: Double?,
    val components: List<MomentumComponent>,
    val reasons: List<String>
)

/**
 * Deterministic momentum model. It never turns missing observations into zero:
 * unavailable components are excluded and the result records what was missing.
 */
class MomentumEngine {
    fun evaluate(windows: Map<Int, WindowMetrics>, liquidityUsd: Double?): AdvancedMomentumResult {
        val m30 = windows[30]
        val m1 = windows[60]
        val m3 = windows[180]
        val m5 = windows[300]
        val candidates = listOfNotNull(m30, m1, m3, m5)
        if (candidates.isEmpty()) return AdvancedMomentumResult(0, MomentumState.DORMANT, null, emptyList(), listOf("No trade windows available"))

        val components = listOf(
            component("Buy pressure", m5?.let { pressure(it) }, "Share of 5m transaction flow from buys"),
            component("Buyer growth", m1?.buyerVelocity?.let(::growthScore), "Change in unique buyers versus the previous comparable window"),
            component("Transaction velocity", m1?.let { velocityScore(it, m5) }, "Recent transaction activity relative to the 5m baseline"),
            component("Volume velocity", m1?.volumeVelocity?.let(::velocityScore), "Recent volume acceleration proxy"),
            component("Volume acceleration", acceleration(m1?.volumeVelocity, m5?.volumeVelocity), "Change in volume velocity across windows"),
            component("Price momentum", m5?.priceChangePct?.let(::priceScore), "5m price change, penalizing extreme extension"),
            component("Price acceleration", acceleration(priceScore(m1?.priceChangePct), priceScore(m5?.priceChangePct)), "Short-window price impulse versus 5m impulse"),
            component("Liquidity", liquidityUsd?.let { (it / 20_000.0 * 100.0).coerceIn(0.0, 100.0) }, "Observed liquidity relative to the baseline"),
            component("Trade-size balance", m5?.let(::tradeSizeScore), "Balance between average buy and sell size"),
            component("Sell pressure", m5?.let { 100.0 - pressure(it) }, "Lower sell pressure supports continuation")
        )
        val available = components.mapNotNull { it.value }
        val score = if (available.isEmpty()) 0 else available.average().toInt().coerceIn(0, 100)
        val persistence = candidates.takeIf { it.size >= 2 }?.let { ws ->
            ws.count { pressure(it) >= 50.0 }.toDouble() / ws.size * 100.0
        }
        val state = stateFor(score, m1, m5, persistence)
        val reasons = buildList {
            components.filter { (it.value ?: 0.0) >= 70.0 }.take(4).forEach { add("${it.name}: ${it.explanation}") }
            components.filter { (it.value ?: 100.0) <= 30.0 }.take(4).forEach { add("Weak ${it.name}: ${it.explanation}") }
            if (persistence != null) add("Momentum persistence: ${persistence.toInt()}% of available windows")
            if (available.size < components.size / 2) add("Momentum confidence limited by missing observations")
        }
        return AdvancedMomentumResult(score, state, persistence, components, reasons)
    }

    private fun component(name: String, value: Double?, explanation: String) = MomentumComponent(name, value?.coerceIn(0.0, 100.0), explanation)

    private fun pressure(m: WindowMetrics): Double {
        val total = m.buys + m.sells
        return if (total == 0) 0.0 else m.buys.toDouble() / total * 100.0
    }

    private fun growthScore(ratio: Double): Double = ((ratio - 0.5) * 100.0).coerceIn(0.0, 100.0)

    private fun velocityScore(m1: WindowMetrics, m5: WindowMetrics?): Double {
        val baseline = m5?.totalTrades?.div(5.0) ?: return 50.0
        return if (baseline <= 0.0) 0.0 else (m1.totalTrades / baseline * 50.0).coerceIn(0.0, 100.0)
    }

    private fun velocityScore(ratio: Double): Double = (ratio * 50.0).coerceIn(0.0, 100.0)

    private fun priceScore(change: Double?): Double? = change?.let {
        when {
            it <= -20.0 -> 0.0
            it < 0.0 -> (50.0 + it * 2.0).coerceIn(0.0, 50.0)
            it <= 20.0 -> (50.0 + it * 2.5).coerceIn(0.0, 100.0)
            else -> (100.0 - (it - 20.0) * 1.5).coerceIn(0.0, 100.0)
        }
    }

    private fun acceleration(current: Double?, previous: Double?): Double? = if (current != null && previous != null) {
        (50.0 + (current - previous) * 25.0).coerceIn(0.0, 100.0)
    } else null

    private fun tradeSizeScore(m: WindowMetrics): Double? {
        if (m.avgBuySizeUsd <= 0.0 && m.avgSellSizeUsd <= 0.0) return null
        val largest = max(m.avgBuySizeUsd, m.avgSellSizeUsd)
        val imbalance = abs(m.avgBuySizeUsd - m.avgSellSizeUsd) / max(largest, 1.0)
        return (100.0 - imbalance * 100.0).coerceIn(0.0, 100.0)
    }

    private fun stateFor(score: Int, m1: WindowMetrics?, m5: WindowMetrics?, persistence: Double?): MomentumState {
        if (m5?.totalTrades == 0) return MomentumState.DORMANT
        val shortPriceChange = m1?.priceChangePct
        val longPriceChange = m5?.priceChangePct
        if (shortPriceChange != null && longPriceChange != null && shortPriceChange < longPriceChange - 10.0) return MomentumState.COLLAPSING
        if (m1?.volumeVelocity != null && m1.volumeVelocity < 0.7) return MomentumState.WEAKENING
        return when {
            score >= 90 && (persistence ?: 0.0) >= 75.0 -> MomentumState.EXTREME
            score >= 75 -> MomentumState.STRONG
            score >= 60 -> MomentumState.EARLY
            score >= 40 -> MomentumState.BUILDING
            else -> MomentumState.DORMANT
        }
    }
}
