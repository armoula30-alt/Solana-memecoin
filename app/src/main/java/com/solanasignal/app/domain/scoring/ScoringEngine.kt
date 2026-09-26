package com.solanasignal.app.domain.scoring

import com.solanasignal.app.data.settings.ScoreWeights
import com.solanasignal.app.domain.metrics.WindowMetrics
import com.solanasignal.app.domain.safety.SafetyReport
import kotlin.math.min

data class ComponentScore(val label: String, val value: Double?)  // null = component unavailable, excluded (not faked)

data class MomentumScore(
    val total: Int,
    val components: List<ComponentScore>
)

/**
 * Produces an explainable 0-100 score from weighted components (spec #20).
 * A component that cannot be computed from available data is left null and
 * excluded from the weighted sum (its weight is redistributed proportionally
 * across the remaining available components) - this is the "documented
 * handling method for unavailable components" the spec requires, rather than
 * silently substituting a fake value.
 */
class ScoringEngine {

    fun scoreDex(
        buys5m: Int?,
        sells5m: Int?,
        buyVolume5mUsd: Double?,
        sellVolume5mUsd: Double?,
        volume5mUsd: Double?,
        volume1hUsd: Double?,
        priceChange5mPct: Double?,
        liquidityUsd: Double?,
        safety: SafetyReport,
        weights: ScoreWeights
    ): MomentumScore {
        val buyerPressure = if (buys5m != null && sells5m != null) {
            normalizeRatio(buys5m.toDouble() / maxOf(sells5m, 1).toDouble())
        } else null
        val volumePressure = if (buyVolume5mUsd != null && sellVolume5mUsd != null) {
            normalizeRatio(buyVolume5mUsd / maxOf(sellVolume5mUsd, 1.0))
        } else null
        val velocity = if (volume5mUsd != null && volume1hUsd != null && volume1hUsd > 0.0) {
            normalizeRatio((volume5mUsd * 12.0) / volume1hUsd)
        } else null
        val priceMomentum = priceChange5mPct?.let { momentumCurve(it) }
        val liquidityScore = liquidityUsd?.let { min(100.0, (it / 20_000.0) * 100.0) }
        val safetyScore = safety.scorePercent()
        val components = listOf(
            ComponentScore("Buyer Pressure", buyerPressure) to weights.buyerPressure,
            ComponentScore("Volume Pressure", volumePressure) to weights.volumePressure,
            ComponentScore("Volume Velocity", velocity) to weights.volumeVelocity,
            ComponentScore("Price Momentum", priceMomentum) to weights.priceMomentum,
            ComponentScore("Liquidity", liquidityScore) to weights.liquidity,
            ComponentScore("Holder Distribution", null) to weights.holderDistribution,
            ComponentScore("Safety", safetyScore) to weights.safety
        )
        val available = components.filter { it.first.value != null }
        val totalWeight = available.sumOf { it.second }
        val total = if (totalWeight > 0.0) {
            available.sumOf { (component, weight) -> component.value!! * weight / totalWeight }.toInt()
        } else 0
        return MomentumScore(total.coerceIn(0, 100), components.map { it.first })
    }

    fun score(
        metrics5m: WindowMetrics,
        metrics1m: WindowMetrics,
        holderConcentrationPct: Double?,
        liquidityUsd: Double?,
        safety: SafetyReport,
        weights: ScoreWeights
    ): MomentumScore {

        // Buyer Pressure: buyer/seller ratio compressed to 0-100 (ratio of 3x -> 100)
        val buyerPressure = normalizeRatio(metrics5m.buyerSellerRatio)

        // Volume Pressure: buy/sell volume ratio compressed to 0-100
        val volumePressure = normalizeRatio(metrics5m.buySellVolumeRatio)

        // Volume Velocity: null -> unavailable; otherwise compressed (velocity of 3x -> 100)
        val volumeVelocity = metrics1m.volumeVelocity?.let { normalizeRatio(it) }

        // Price Momentum: rewards positive momentum but does NOT reward extreme
        // extension - a bell-shaped preference peaking around +15-25% (spec #16).
        val priceMomentum = metrics5m.priceChangePct?.let { pct -> momentumCurve(pct) }

        // Liquidity: unavailable -> null; otherwise simple thresholded curve.
        val liquidityScore = liquidityUsd?.let { min(100.0, (it / 20_000.0) * 100.0) }

        // Holder distribution: unavailable -> null; lower concentration is better.
        val holderScore = holderConcentrationPct?.let { pct -> min(100.0, maxOf(0.0, 100.0 - pct * 2)) }

        // Safety: from SafetyReport; UNKNOWN-only reports -> null (not assumed safe).
        val safetyScore = safety.scorePercent()

        val components = listOf(
            ComponentScore("Buyer Pressure", buyerPressure) to weights.buyerPressure,
            ComponentScore("Volume Pressure", volumePressure) to weights.volumePressure,
            ComponentScore("Volume Velocity", volumeVelocity) to weights.volumeVelocity,
            ComponentScore("Price Momentum", priceMomentum) to weights.priceMomentum,
            ComponentScore("Liquidity", liquidityScore) to weights.liquidity,
            ComponentScore("Holder Distribution", holderScore) to weights.holderDistribution,
            ComponentScore("Safety", safetyScore) to weights.safety
        )

        val available = components.filter { it.first.value != null }
        val totalAvailableWeight = available.sumOf { it.second }
        val weighted = if (totalAvailableWeight > 0.0) {
            available.sumOf { (comp, weight) -> comp.value!! * (weight / totalAvailableWeight) }
        } else 0.0

        return MomentumScore(
            total = weighted.toInt().coerceIn(0, 100),
            components = components.map { it.first }
        )
    }

    private fun normalizeRatio(ratio: Double): Double = min(100.0, (ratio / 3.0) * 100.0)

    /** Peaks around +15-25%, declines for both weak and extremely extended moves. */
    private fun momentumCurve(pctChange: Double): Double {
        if (pctChange <= 0) return maxOf(0.0, 40.0 + pctChange) // negative momentum penalized
        val distanceFromIdeal = kotlin.math.abs(pctChange - 20.0)
        return (100.0 - distanceFromIdeal * 1.5).coerceIn(0.0, 100.0)
    }
}
