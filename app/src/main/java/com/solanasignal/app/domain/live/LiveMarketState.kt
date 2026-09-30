package com.solanasignal.app.domain.live

/** A real observation window. Null means the provider did not supply enough evidence. */
data class LiveWindowState(
    val seconds: Int,
    val observationCount: Int,
    val priceUsd: Double? = null,
    val marketCapUsd: Double? = null,
    val liquidityUsd: Double? = null,
    val volumeUsd: Double? = null,
    val buyCount: Int? = null,
    val sellCount: Int? = null,
    val buyVolumeUsd: Double? = null,
    val sellVolumeUsd: Double? = null,
    val uniqueBuyers: Int? = null,
    val uniqueSellers: Int? = null,
    val firstPriceUsd: Double? = null,
    val firstMarketCapUsd: Double? = null,
    val eventTimestampMs: Long? = null,
    val receiveTimestampMs: Long? = null,
    val source: String? = null
) {
    val hasRealObservation: Boolean get() = observationCount > 0 && eventTimestampMs != null

    val priceChangePct: Double?
        get() = if (firstPriceUsd != null && priceUsd != null && firstPriceUsd != 0.0) {
            (priceUsd - firstPriceUsd) / firstPriceUsd * 100.0
        } else null

    val marketCapChangePct: Double?
        get() = if (firstMarketCapUsd != null && marketCapUsd != null && firstMarketCapUsd != 0.0) {
            (marketCapUsd - firstMarketCapUsd) / firstMarketCapUsd * 100.0
        } else null
}

data class FreshnessThresholds(
    val freshBelowMs: Long = 3_000L,
    val agingBelowMs: Long = 10_000L
)

enum class LiveFreshness { FRESH, AGING, STALE, UNKNOWN }

/**
 * The one market state shared by scanners, both signal engines, paper trading, charts,
 * P/L, outcome evaluation, and diagnostics. It contains observations only; it does not
 * contain a signal decision.
 */
data class LiveMarketState(
    val mint: String,
    val symbol: String? = null,
    val name: String? = null,
    val ageSeconds: Long? = null,
    val source: String? = null,
    val firstObservedAtMs: Long? = null,
    val lastEventTimestampMs: Long? = null,
    val lastReceivedAtMs: Long? = null,
    val priceUsd: Double? = null,
    val marketCapUsd: Double? = null,
    val liquidityUsd: Double? = null,
    val liquidityToMarketCap: Double? = null,
    val volumeUsd: Double? = null,
    val volumeVelocity: Double? = null,
    val volumeAcceleration: Double? = null,
    val buyCount: Int? = null,
    val sellCount: Int? = null,
    val buyVolumeUsd: Double? = null,
    val sellVolumeUsd: Double? = null,
    val buyVelocity: Double? = null,
    val sellVelocity: Double? = null,
    val buyAcceleration: Double? = null,
    val sellAcceleration: Double? = null,
    val tradeFrequency: Double? = null,
    val tradeFrequencyAcceleration: Double? = null,
    val uniqueBuyers: Int? = null,
    val uniqueSellers: Int? = null,
    val buyerGrowth: Double? = null,
    val sellerGrowth: Double? = null,
    val buyerSellerRatio: Double? = null,
    val repeatedWalletRatio: Double? = null,
    val walletConcentration: Double? = null,
    val clusteringIndicator: Double? = null,
    val deployerRisk: Double? = null,
    val priceVelocity: Double? = null,
    val priceAcceleration: Double? = null,
    val marketCapVelocity: Double? = null,
    val marketCapAcceleration: Double? = null,
    val momentum: Double? = null,
    val momentumConsistency: Double? = null,
    val pullbackDepth: Double? = null,
    val recoverySpeed: Double? = null,
    val liquidityRisk: Double? = null,
    val sellPressureRisk: Double? = null,
    val holderConcentrationRisk: Double? = null,
    val abnormalActivityRisk: Double? = null,
    val collapseRisk: Double? = null,
    val windows: Map<Int, LiveWindowState> = emptyMap(),
    val sourceLatencyMs: Long? = null,
    val dataAgeMs: Long? = null,
    val stale: Boolean? = null,
    val confidence: Double? = null
) {
    fun freshness(nowMs: Long, thresholds: FreshnessThresholds = FreshnessThresholds()): LiveFreshness {
        val age = dataAgeMs ?: lastReceivedAtMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: return LiveFreshness.UNKNOWN
        return when {
            age < thresholds.freshBelowMs -> LiveFreshness.FRESH
            age < thresholds.agingBelowMs -> LiveFreshness.AGING
            else -> LiveFreshness.STALE
        }
    }

    fun isStale(nowMs: Long, thresholds: FreshnessThresholds = FreshnessThresholds()): Boolean =
        freshness(nowMs, thresholds) == LiveFreshness.STALE || stale == true
}
