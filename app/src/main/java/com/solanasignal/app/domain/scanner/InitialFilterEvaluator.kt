package com.solanasignal.app.domain.scanner

import com.solanasignal.app.data.settings.FilterConfig
import com.solanasignal.app.domain.metrics.WindowMetrics

data class InitialFilterInput(
    val ageSeconds: Long?,
    val marketCapUsd: Double?,
    val buys: Int? = null,
    val sells: Int? = null,
    val buyVolumeUsd: Double? = null,
    val sellVolumeUsd: Double? = null
)

enum class InitialFilterStage { DISCOVERY, LIVE }
enum class InitialFilterStatus { PASS, REJECT, UNKNOWN }

data class InitialFilterDetail(
    val name: String,
    val stage: InitialFilterStage,
    val actualValue: String,
    val configuredThreshold: String,
    val status: InitialFilterStatus,
    val reason: String,
    /** Unknown discovery data that cannot be safely deferred blocks tracking. */
    val blocksTracking: Boolean = false
)

data class InitialFilterDecision(
    val status: InitialFilterStatus,
    val details: List<InitialFilterDetail>,
    val canTrack: Boolean
)

/**
 * Keeps discovery eligibility separate from live trade conditions. Missing live data
 * is expected before PumpDev starts and therefore never blocks the subscription gate.
 */
object InitialFilterEvaluator {
    fun evaluate(input: InitialFilterInput, config: FilterConfig): InitialFilterDecision =
        evaluateDiscovery(input, config)

    fun evaluateDiscovery(input: InitialFilterInput, config: FilterConfig): InitialFilterDecision {
        val details = buildList {
            add(ageDetail(input.ageSeconds, config.maxTokenAgeSeconds))
            add(marketCapDetail(input.marketCapUsd, config.minMarketCapUsd))
        }
        val discoveryDetails = details.filter { it.stage == InitialFilterStage.DISCOVERY }
        val status = aggregate(discoveryDetails)
        val canTrack = discoveryDetails.none { it.status == InitialFilterStatus.REJECT } &&
            discoveryDetails.none { it.blocksTracking && it.status == InitialFilterStatus.UNKNOWN }
        return InitialFilterDecision(status, details, canTrack)
    }

    fun evaluateLive(metrics: WindowMetrics, config: FilterConfig): InitialFilterDecision {
        val details = buildList {
            if (config.requireBuyersGtSellers) add(liveBuyerDetail(metrics))
            if (config.requireBuyVolumeGtSellVolume) add(liveVolumeDetail(metrics))
        }
        return InitialFilterDecision(aggregate(details), details, canTrack = true)
    }

    private fun aggregate(details: List<InitialFilterDetail>): InitialFilterStatus = when {
        details.any { it.status == InitialFilterStatus.REJECT } -> InitialFilterStatus.REJECT
        details.any { it.status == InitialFilterStatus.UNKNOWN } -> InitialFilterStatus.UNKNOWN
        else -> InitialFilterStatus.PASS
    }

    private fun ageDetail(ageSeconds: Long?, maxAge: Int): InitialFilterDetail = when {
        ageSeconds == null -> InitialFilterDetail("maxTokenAgeSeconds", InitialFilterStage.DISCOVERY, "UNKNOWN", "<= $maxAge", InitialFilterStatus.UNKNOWN, "trusted creation timestamp is unavailable; age was not fabricated")
        ageSeconds <= maxAge -> InitialFilterDetail("maxTokenAgeSeconds", InitialFilterStage.DISCOVERY, "$ageSeconds", "<= $maxAge", InitialFilterStatus.PASS, "age is within configured limit")
        else -> InitialFilterDetail("maxTokenAgeSeconds", InitialFilterStage.DISCOVERY, "$ageSeconds", "<= $maxAge", InitialFilterStatus.REJECT, "token age exceeds configured limit")
    }

    private fun marketCapDetail(marketCapUsd: Double?, minimum: Double): InitialFilterDetail = when {
        marketCapUsd == null -> InitialFilterDetail("minMarketCapUsd", InitialFilterStage.DISCOVERY, "UNKNOWN", ">= $minimum", InitialFilterStatus.UNKNOWN, "market cap is unavailable at discovery", blocksTracking = true)
        marketCapUsd >= minimum -> InitialFilterDetail("minMarketCapUsd", InitialFilterStage.DISCOVERY, "$marketCapUsd", ">= $minimum", InitialFilterStatus.PASS, "market cap meets configured minimum")
        else -> InitialFilterDetail("minMarketCapUsd", InitialFilterStage.DISCOVERY, "$marketCapUsd", ">= $minimum", InitialFilterStatus.REJECT, "market cap is below configured minimum")
    }

    private fun liveBuyerDetail(metrics: WindowMetrics): InitialFilterDetail = when {
        metrics.totalTrades == 0 -> InitialFilterDetail("requireBuyersGtSellers", InitialFilterStage.LIVE, "UNKNOWN", "uniqueBuyers > uniqueSellers", InitialFilterStatus.UNKNOWN, "No live trade statistics yet")
        metrics.uniqueBuyers > metrics.uniqueSellers -> InitialFilterDetail("requireBuyersGtSellers", InitialFilterStage.LIVE, "${metrics.uniqueBuyers} > ${metrics.uniqueSellers}", "uniqueBuyers > uniqueSellers", InitialFilterStatus.PASS, "live buyers exceed sellers")
        else -> InitialFilterDetail("requireBuyersGtSellers", InitialFilterStage.LIVE, "${metrics.uniqueBuyers} <= ${metrics.uniqueSellers}", "uniqueBuyers > uniqueSellers", InitialFilterStatus.REJECT, "live buyers do not exceed sellers")
    }

    private fun liveVolumeDetail(metrics: WindowMetrics): InitialFilterDetail = when {
        metrics.totalTrades == 0 -> InitialFilterDetail("requireBuyVolumeGtSellVolume", InitialFilterStage.LIVE, "UNKNOWN", "buyVolumeUsd > sellVolumeUsd", InitialFilterStatus.UNKNOWN, "No live trade statistics yet")
        metrics.buyVolumeUsd > metrics.sellVolumeUsd -> InitialFilterDetail("requireBuyVolumeGtSellVolume", InitialFilterStage.LIVE, "${metrics.buyVolumeUsd} > ${metrics.sellVolumeUsd}", "buyVolumeUsd > sellVolumeUsd", InitialFilterStatus.PASS, "live buy volume exceeds sell volume")
        else -> InitialFilterDetail("requireBuyVolumeGtSellVolume", InitialFilterStage.LIVE, "${metrics.buyVolumeUsd} <= ${metrics.sellVolumeUsd}", "buyVolumeUsd > sellVolumeUsd", InitialFilterStatus.REJECT, "live buy volume does not exceed sell volume")
    }
}
