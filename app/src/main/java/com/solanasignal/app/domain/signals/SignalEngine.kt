package com.solanasignal.app.domain.signals

import com.solanasignal.app.data.settings.FilterConfig
import com.solanasignal.app.domain.metrics.WindowMetrics
import com.solanasignal.app.domain.safety.SafetyReport
import com.solanasignal.app.domain.safety.CheckStatus
import com.solanasignal.app.domain.scoring.MomentumScore
import java.util.concurrent.ConcurrentHashMap

enum class SignalType { BUY, SELL, WATCH, REJECTED }

data class FilterResult(val name: String, val passed: Boolean)

/**
 * [type] is the classification this evaluation produced. [shouldNotify] tells the
 * caller whether a *fresh* notification/DB write should happen for it right now -
 * false means cooldown/dedupe (spec #24) suppressed a repeat of the same
 * classification, not that the classification itself changed.
 */
data class SignalDecision(
    val type: SignalType,
    val score: Int,
    val filters: List<FilterResult>,
    val reasons: List<String>,
    val shouldNotify: Boolean
)

private data class SignalState(var lastType: SignalType?, var lastScore: Int, var lastEmittedAtMs: Long)

/**
 * Applies hard filters + score thresholds (spec #21/#22), generates BUY/SELL/WATCH/
 * REJECTED classifications with visible reasons, and deduplicates/cools down repeat
 * notifications for the same token (spec #24). This produces signals only - it never
 * executes anything.
 */
class SignalEngine {

    private val stateByMint = ConcurrentHashMap<String, SignalState>()

    fun evaluateDex(
        mint: String,
        ageSeconds: Long?,
        marketCapUsd: Double?,
        buys5m: Int?,
        sells5m: Int?,
        buyVolume5mUsd: Double?,
        sellVolume5mUsd: Double?,
        volumeVelocity: Double?,
        priceChange5mPct: Double?,
        score: MomentumScore,
        safety: SafetyReport,
        config: FilterConfig,
        nowMs: Long
    ): SignalDecision {
        val ageOk = ageSeconds != null && ageSeconds <= config.maxTokenAgeSeconds
        val mcOk = marketCapUsd != null && marketCapUsd >= config.minMarketCapUsd
        val buyersOk = !config.requireBuyersGtSellers ||
            (buys5m != null && sells5m != null && buys5m > sells5m)
        // DexScreener exposes aggregate volume and buy/sell counts, not buy/sell
        // volume amounts. Do not reject a token for a metric this source cannot provide.
        val volOk = !config.requireBuyVolumeGtSellVolume ||
            buyVolume5mUsd == null || sellVolume5mUsd == null || buyVolume5mUsd > sellVolume5mUsd
        val safetyOk = safety.overall != CheckStatus.FAIL
        val scoreOk = score.total >= config.minScoreForBuy
        val reasons = buildList {
            if (ageOk) add("DexScreener pair is within the token-age window")
            else add("Rejected: token age exceeds ${config.maxTokenAgeSeconds}s or age is unavailable")
            if (mcOk) add("Market cap above minimum")
            else add("Rejected: market cap is below \$${config.minMarketCapUsd.toInt()} or unavailable")
            if (buyersOk) add("DexScreener 5m buys > sells")
            else add("Rejected: 5m buys are not greater than sells")
            if (buyVolume5mUsd != null && sellVolume5mUsd != null && buyVolume5mUsd > sellVolume5mUsd) {
                add("DexScreener 5m buy volume > sell volume")
            } else if (buyVolume5mUsd == null || sellVolume5mUsd == null) {
                add("Buy/sell volume split unavailable from DexScreener; aggregate volume used")
            }
            if ((volumeVelocity ?: 0.0) > 1.5) add("5m volume is accelerating versus the 1h baseline")
            if ((priceChange5mPct ?: 0.0) > 0) add("Positive 5m price momentum")
            if (safetyOk) add("Available safety checks did not fail")
            else add("Rejected: a safety check failed")
            if (!scoreOk) add("Watch only: score ${score.total} is below BUY threshold ${config.minScoreForBuy}")
        }
        val rawType = when {
            ageOk && mcOk && buyersOk && volOk && safetyOk && scoreOk -> SignalType.BUY
            score.total >= config.watchScoreFloor -> SignalType.WATCH
            else -> SignalType.REJECTED
        }
        return SignalDecision(
            rawType, score.total, emptyList(), reasons,
            shouldEmit(mint, rawType, score.total, config, nowMs)
        )
    }

    fun evaluate(
        mint: String,
        ageSeconds: Long?,
        marketCapUsd: Double?,
        metrics5m: WindowMetrics,
        score: MomentumScore,
        safety: SafetyReport,
        config: FilterConfig,
        nowMs: Long
    ): SignalDecision {
        val filters = mutableListOf<FilterResult>()
        val reasons = mutableListOf<String>()

        val ageOk = ageSeconds != null && ageSeconds <= config.maxTokenAgeSeconds
        filters += FilterResult("Age <= ${config.maxTokenAgeSeconds}s", ageOk)
        if (ageOk) reasons += "Token younger than ${config.maxTokenAgeSeconds / 60} minutes"

        val mcOk = marketCapUsd != null && marketCapUsd >= config.minMarketCapUsd
        filters += FilterResult("Market Cap >= \$${config.minMarketCapUsd.toInt()}", mcOk)
        if (mcOk) reasons += "Market cap above minimum"

        val buyersOk = !config.requireBuyersGtSellers || metrics5m.uniqueBuyers > metrics5m.uniqueSellers
        filters += FilterResult("Buyers > Sellers", buyersOk)
        if (buyersOk && config.requireBuyersGtSellers) reasons += "Buyers > Sellers"

        val volOk = !config.requireBuyVolumeGtSellVolume || metrics5m.buyVolumeUsd > metrics5m.sellVolumeUsd
        filters += FilterResult("Buy Volume > Sell Volume", volOk)
        if (volOk && config.requireBuyVolumeGtSellVolume) reasons += "Buy volume > Sell volume"

        val safetyOk = safety.overall != CheckStatus.FAIL
        filters += FilterResult("Safety not FAILED", safetyOk)
        if (safetyOk) reasons += "Required safety checks passed"

        if ((metrics5m.volumeVelocity ?: 0.0) > 1.5) reasons += "Volume accelerating"
        if ((metrics5m.priceChangePct ?: 0.0) > 0) reasons += "Positive momentum"

        val scoreOk = score.total >= config.minScoreForBuy
        filters += FilterResult("Score >= ${config.minScoreForBuy}", scoreOk)

        val allHardFiltersPass = ageOk && mcOk && buyersOk && volOk && safetyOk

        val rawType = when {
            allHardFiltersPass && scoreOk -> SignalType.BUY
            score.total >= config.watchScoreFloor -> SignalType.WATCH
            else -> SignalType.REJECTED
        }

        val shouldNotify = shouldEmit(mint, rawType, score.total, config, nowMs)

        return SignalDecision(rawType, score.total, filters, reasons, shouldNotify)
    }

    /** Sell-side evaluation given deteriorating conditions (spec #23). Returns null if no sell trigger fired at all. */
    fun evaluateSellTrigger(
        mint: String,
        previousScore: Int?,
        currentScore: Int,
        metrics5m: WindowMetrics,
        safety: SafetyReport,
        config: FilterConfig,
        nowMs: Long
    ): SignalDecision? {
        val reasons = mutableListOf<String>()
        var trigger = false

        if (previousScore != null && previousScore - currentScore >= 15) {
            reasons += "Momentum deteriorating (score $previousScore -> $currentScore)"
            trigger = true
        }
        if (metrics5m.sellVolumeUsd > metrics5m.buyVolumeUsd) {
            reasons += "Sell pressure increased"
            trigger = true
        }
        if (metrics5m.uniqueSellers > metrics5m.uniqueBuyers) {
            reasons += "Buyers/Sellers reversal"
            trigger = true
        }
        if ((metrics5m.priceChangePct ?: 0.0) < 0) {
            reasons += "Price momentum reversal"
            trigger = true
        }
        if (safety.overall == CheckStatus.FAIL) {
            reasons += "Safety check failed"
            trigger = true
        }

        if (!trigger) return null

        val shouldNotify = shouldEmit(mint, SignalType.SELL, currentScore, config, nowMs)
        return SignalDecision(SignalType.SELL, currentScore, emptyList(), reasons, shouldNotify)
    }

    /** Cooldown + dedupe (spec #24): decides whether this classification should produce a fresh notification/record. */
    private fun shouldEmit(mint: String, proposed: SignalType, score: Int, config: FilterConfig, nowMs: Long): Boolean {
        val state = stateByMint[mint]
        if (state == null) {
            stateByMint[mint] = SignalState(proposed, score, nowMs)
            return true
        }

        val cooldownMs = when (proposed) {
            SignalType.BUY -> config.buySignalCooldownSeconds * 1000L
            SignalType.SELL -> config.sellSignalCooldownSeconds * 1000L
            else -> 0L
        }

        val sinceLast = nowMs - state.lastEmittedAtMs
        val sameTypeAsLast = state.lastType == proposed
        val scoreJumped = kotlin.math.abs(score - state.lastScore) >= config.resignalScoreDelta

        val emit = when {
            !sameTypeAsLast -> true
            proposed == SignalType.WATCH || proposed == SignalType.REJECTED -> false // no repeat noise for non-actionable states
            sinceLast >= cooldownMs -> true
            scoreJumped -> true
            else -> false
        }

        if (emit) {
            state.lastType = proposed
            state.lastScore = score
            state.lastEmittedAtMs = nowMs
        }
        return emit
    }
}
