package com.solanasignal.app.domain.backtest

import com.solanasignal.app.data.room.entities.TokenFeatureSnapshotEntity
import com.solanasignal.app.data.room.entities.TokenObservationEntity

/**
 * Deterministic evaluation: each snapshot can only see observations after its timestamp
 * as the outcome. It never uses future features to decide the entry.
 */
data class WalkForwardConfig(
    val horizonsSeconds: List<Int> = listOf(10, 30, 60, 300, 600),
    val minimumConfidence: Int = 50,
    val minimumOpportunity: Int = 60,
    val maxRisk: Int = 60
)

data class BacktestTrade(
    val mint: String,
    val signalTimestamp: Long,
    val horizonSeconds: Int,
    val entryPriceUsd: Double,
    val exitPriceUsd: Double?,
    val changePct: Double?,
    val confidence: Int,
    val risk: Int?
)

data class WalkForwardReport(
    val evaluated: Int,
    val resolved: Int,
    val hitRate: Double?,
    val averageChangePct: Double?,
    val maxChangePct: Double?,
    val minChangePct: Double?,
    val trades: List<BacktestTrade>
)

class WalkForwardBacktestEngine {
    fun evaluate(
        snapshots: List<TokenFeatureSnapshotEntity>,
        observations: List<TokenObservationEntity>,
        config: WalkForwardConfig = WalkForwardConfig()
    ): WalkForwardReport {
        val byMint = observations.groupBy { it.mint }
        val trades = snapshots.asSequence()
            .filter { (it.opportunityScore ?: 0) >= config.minimumOpportunity }
            .filter { it.dataConfidenceScore >= config.minimumConfidence }
            .filter { (it.riskScore ?: 0) <= config.maxRisk }
            .flatMap { snapshot ->
                val entry = byMint[snapshot.mint].orEmpty().lastOrNull { it.timestamp <= snapshot.timestamp && it.priceUsd != null }
                if (entry?.priceUsd == null) emptySequence()
                else config.horizonsSeconds.asSequence().map { horizon ->
                    val exit = byMint[snapshot.mint].orEmpty().firstOrNull {
                        it.timestamp >= snapshot.timestamp + horizon * 1000L && it.priceUsd != null
                    }
                    val change = if (exit?.priceUsd != null && entry.priceUsd != 0.0) (exit.priceUsd - entry.priceUsd) / entry.priceUsd * 100.0 else null
                    BacktestTrade(snapshot.mint, snapshot.timestamp, horizon, entry.priceUsd, exit?.priceUsd, change, snapshot.dataConfidenceScore, snapshot.riskScore)
                }
            }.toList()
        val resolved = trades.mapNotNull { it.changePct }
        return WalkForwardReport(
            evaluated = trades.size,
            resolved = resolved.size,
            hitRate = resolved.takeIf { it.isNotEmpty() }?.count { it > 0.0 }?.toDouble()?.div(resolved.size),
            averageChangePct = resolved.takeIf { it.isNotEmpty() }?.average(),
            maxChangePct = resolved.maxOrNull(),
            minChangePct = resolved.minOrNull(),
            trades = trades
        )
    }
}
