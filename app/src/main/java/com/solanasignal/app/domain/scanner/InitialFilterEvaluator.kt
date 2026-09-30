package com.solanasignal.app.domain.scanner

import com.solanasignal.app.data.settings.FilterConfig

/** Discovery-time inputs only; missing values remain null and are never treated as zero. */
data class InitialFilterInput(
    val ageSeconds: Long?,
    val marketCapUsd: Double?,
    val buys: Int?,
    val sells: Int?,
    val buyVolumeUsd: Double?,
    val sellVolumeUsd: Double?
)

enum class InitialFilterStatus { PASS, REJECT, UNKNOWN }

data class InitialFilterDetail(
    val name: String,
    val actualValue: String,
    val configuredThreshold: String,
    val status: InitialFilterStatus,
    val reason: String
)

data class InitialFilterDecision(
    val status: InitialFilterStatus,
    val details: List<InitialFilterDetail>
) {
    val canTrack: Boolean get() = status == InitialFilterStatus.PASS
}

/** Applies only the existing Initial Filter configuration; it does not score or emit signals. */
object InitialFilterEvaluator {
    fun evaluate(input: InitialFilterInput, config: FilterConfig): InitialFilterDecision {
        val details = buildList {
            add(ageDetail(input.ageSeconds, config.maxTokenAgeSeconds))
            add(marketCapDetail(input.marketCapUsd, config.minMarketCapUsd))
            if (config.requireBuyersGtSellers) add(buyerDetail(input.buys, input.sells))
            if (config.requireBuyVolumeGtSellVolume) add(volumeDetail(input.buyVolumeUsd, input.sellVolumeUsd))
        }
        val status = when {
            details.any { it.status == InitialFilterStatus.REJECT } -> InitialFilterStatus.REJECT
            details.any { it.status == InitialFilterStatus.UNKNOWN } -> InitialFilterStatus.UNKNOWN
            else -> InitialFilterStatus.PASS
        }
        return InitialFilterDecision(status, details)
    }

    private fun ageDetail(ageSeconds: Long?, maxAge: Int): InitialFilterDetail = when {
        ageSeconds == null -> InitialFilterDetail("maxTokenAgeSeconds", "UNKNOWN", "<= $maxAge", InitialFilterStatus.UNKNOWN, "createdAt is missing")
        ageSeconds <= maxAge -> InitialFilterDetail("maxTokenAgeSeconds", "$ageSeconds", "<= $maxAge", InitialFilterStatus.PASS, "age is within configured limit")
        else -> InitialFilterDetail("maxTokenAgeSeconds", "$ageSeconds", "<= $maxAge", InitialFilterStatus.REJECT, "token age exceeds configured limit")
    }

    private fun marketCapDetail(marketCapUsd: Double?, minimum: Double): InitialFilterDetail = when {
        marketCapUsd == null -> InitialFilterDetail("minMarketCapUsd", "UNKNOWN", ">= $minimum", InitialFilterStatus.UNKNOWN, "market cap is unavailable at discovery")
        marketCapUsd >= minimum -> InitialFilterDetail("minMarketCapUsd", "$marketCapUsd", ">= $minimum", InitialFilterStatus.PASS, "market cap meets configured minimum")
        else -> InitialFilterDetail("minMarketCapUsd", "$marketCapUsd", ">= $minimum", InitialFilterStatus.REJECT, "market cap is below configured minimum")
    }

    private fun buyerDetail(buys: Int?, sells: Int?): InitialFilterDetail = when {
        buys == null || sells == null -> InitialFilterDetail("requireBuyersGtSellers", "UNKNOWN", "buys > sells", InitialFilterStatus.UNKNOWN, "buy/sell counts are unavailable at discovery")
        buys > sells -> InitialFilterDetail("requireBuyersGtSellers", "$buys > $sells", "buys > sells", InitialFilterStatus.PASS, "buyers exceed sellers")
        else -> InitialFilterDetail("requireBuyersGtSellers", "$buys <= $sells", "buys > sells", InitialFilterStatus.REJECT, "buyers do not exceed sellers")
    }

    private fun volumeDetail(buyVolume: Double?, sellVolume: Double?): InitialFilterDetail = when {
        buyVolume == null || sellVolume == null -> InitialFilterDetail("requireBuyVolumeGtSellVolume", "UNKNOWN", "buyVolumeUsd > sellVolumeUsd", InitialFilterStatus.UNKNOWN, "buy/sell volume is unavailable at discovery")
        buyVolume > sellVolume -> InitialFilterDetail("requireBuyVolumeGtSellVolume", "$buyVolume > $sellVolume", "buyVolumeUsd > sellVolumeUsd", InitialFilterStatus.PASS, "buy volume exceeds sell volume")
        else -> InitialFilterDetail("requireBuyVolumeGtSellVolume", "$buyVolume <= $sellVolume", "buyVolumeUsd > sellVolumeUsd", InitialFilterStatus.REJECT, "buy volume does not exceed sell volume")
    }
}
