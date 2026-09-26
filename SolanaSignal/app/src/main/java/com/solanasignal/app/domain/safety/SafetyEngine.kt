package com.solanasignal.app.domain.safety

import com.solanasignal.app.domain.metrics.WindowMetrics

enum class CheckStatus { PASS, WARN, FAIL, UNKNOWN }

data class SafetyCheck(
    val check: String,
    val status: CheckStatus,
    val reason: String,
    val timestamp: Long,
    val source: String
)

data class SafetyReport(val checks: List<SafetyCheck>) {
    /** Overall status is the worst individual status; never claims "safe" just because nothing failed (spec #19). */
    val overall: CheckStatus = when {
        checks.any { it.status == CheckStatus.FAIL } -> CheckStatus.FAIL
        checks.any { it.status == CheckStatus.WARN } -> CheckStatus.WARN
        checks.any { it.status == CheckStatus.UNKNOWN } -> CheckStatus.UNKNOWN
        else -> CheckStatus.PASS
    }
    val hasFailed: Boolean get() = overall == CheckStatus.FAIL
    /** 0-100 score contribution; UNKNOWN checks are excluded rather than treated as safe or unsafe. */
    fun scorePercent(): Double? {
        val relevant = checks.filter { it.status != CheckStatus.UNKNOWN }
        if (relevant.isEmpty()) return null
        val pass = relevant.count { it.status == CheckStatus.PASS }
        val warn = relevant.count { it.status == CheckStatus.WARN }
        return ((pass + warn * 0.5) / relevant.size) * 100.0
    }
}

/**
 * Evaluates only properties that can actually be verified from available data
 * (spec #19). Anything that cannot be verified is UNKNOWN, never assumed safe.
 */
class SafetyEngine {

    fun evaluate(
        mint: String,
        creatorSellVolumeUsdRecent: Double?,     // null if not observable
        totalVolumeUsdRecent: Double,
        migrationState: String?,                 // null if unknown
        holderConcentrationPct: Double?,          // null if not reliably available
        liquidityUsd: Double?,                    // null if UNKNOWN
        largestSingleTradeUsd: Double?,
        nowMs: Long
    ): SafetyReport {
        val checks = mutableListOf<SafetyCheck>()
        val source = "internal:safety-engine"

        // Creator selling
        checks += if (creatorSellVolumeUsdRecent == null) {
            SafetyCheck("creator_selling", CheckStatus.UNKNOWN, "Creator wallet activity not observable from current data", nowMs, source)
        } else {
            val ratio = if (totalVolumeUsdRecent > 0) creatorSellVolumeUsdRecent / totalVolumeUsdRecent else 0.0
            when {
                ratio > 0.25 -> SafetyCheck("creator_selling", CheckStatus.FAIL, "Creator responsible for >25% of recent sell volume", nowMs, source)
                ratio > 0.10 -> SafetyCheck("creator_selling", CheckStatus.WARN, "Creator responsible for >10% of recent sell volume", nowMs, source)
                else -> SafetyCheck("creator_selling", CheckStatus.PASS, "No significant creator selling observed", nowMs, source)
            }
        }

        // Abnormal / suspicious volume concentration (single trade dominating window)
        checks += if (largestSingleTradeUsd == null || totalVolumeUsdRecent <= 0) {
            SafetyCheck("abnormal_volume", CheckStatus.UNKNOWN, "Insufficient trade history to assess volume concentration", nowMs, source)
        } else {
            val share = largestSingleTradeUsd / totalVolumeUsdRecent
            when {
                share > 0.6 -> SafetyCheck("abnormal_volume", CheckStatus.WARN, "A single trade accounts for >60% of recent volume", nowMs, source)
                else -> SafetyCheck("abnormal_volume", CheckStatus.PASS, "Volume distributed across multiple trades", nowMs, source)
            }
        }

        // Migration state
        checks += when (migrationState) {
            null -> SafetyCheck("migration_state", CheckStatus.UNKNOWN, "Migration status not reported by source", nowMs, source)
            "MIGRATED" -> SafetyCheck("migration_state", CheckStatus.PASS, "Token has migrated", nowMs, source)
            else -> SafetyCheck("migration_state", CheckStatus.WARN, "Token has not migrated: $migrationState", nowMs, source)
        }

        // Holder concentration
        checks += if (holderConcentrationPct == null) {
            SafetyCheck("holder_concentration", CheckStatus.UNKNOWN, "Holder distribution data not reliably available", nowMs, source)
        } else when {
            holderConcentrationPct > 50 -> SafetyCheck("holder_concentration", CheckStatus.FAIL, "Top holders control >50% of supply", nowMs, source)
            holderConcentrationPct > 30 -> SafetyCheck("holder_concentration", CheckStatus.WARN, "Top holders control >30% of supply", nowMs, source)
            else -> SafetyCheck("holder_concentration", CheckStatus.PASS, "Holder concentration within normal range", nowMs, source)
        }

        // Liquidity
        checks += if (liquidityUsd == null) {
            SafetyCheck("liquidity", CheckStatus.UNKNOWN, "Liquidity not reliably reported by source", nowMs, source)
        } else when {
            liquidityUsd < 2_000 -> SafetyCheck("liquidity", CheckStatus.FAIL, "Liquidity below $2,000", nowMs, source)
            liquidityUsd < 5_000 -> SafetyCheck("liquidity", CheckStatus.WARN, "Liquidity below $5,000", nowMs, source)
            else -> SafetyCheck("liquidity", CheckStatus.PASS, "Liquidity above minimum threshold", nowMs, source)
        }

        return SafetyReport(checks)
    }
}
