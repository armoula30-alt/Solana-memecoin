package com.solanasignal.app.data.room.entities

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Normalized token record. Fields are nullable/UNKNOWN when the source
 * (PumpPortal) does not reliably provide them - never fabricated (spec #41).
 */
@Entity(
    tableName = "tokens",
    indices = [Index(value = ["mint"], unique = true)]
)
data class TokenEntity(
    @PrimaryKey val mint: String,
    val name: String?,
    val symbol: String?,
    val creator: String?,
    val uri: String?,
    val poolAddress: String?,       // prefers DexScreener's pairAddress once indexed; falls back to PumpPortal's bondingCurveKey
    val dexId: String? = null,      // "pumpfun", "raydium", "pumpswap"... from DexScreener, once indexed
    val createdAtEpochMs: Long?,
    val firstSeenAtEpochMs: Long,
    val marketCapSol: Double?,      // raw, as reported by PumpPortal
    val liquiditySol: Double?,      // vSolInBondingCurve - SOL reserve, used as a liquidity proxy
    val marketCapUsd: Double?,      // converted using live SOL/USD price; null = price not available yet
    val liquidityUsd: Double?,      // converted; null = price not available yet
    val lastPriceUsd: Double?,
    // Cached live 5-minute metrics, updated on every trade so the Scanner list shows
    // current numbers instead of a stale snapshot from the last emitted signal.
    val buyers5m: Int = 0,
    val sellers5m: Int = 0,
    val buyVolume5mUsd: Double = 0.0,
    val sellVolume5mUsd: Double = 0.0,
    // DexScreener enrichment. Nullable means the token has not been indexed yet.
    val dexUrl: String? = null,
    val dexPairCreatedAtEpochMs: Long? = null,
    val dexVolume5mUsd: Double? = null,
    val dexVolume1hUsd: Double? = null,
    val dexVolume6hUsd: Double? = null,
    val dexVolume24hUsd: Double? = null,
    val dexBuys5m: Int? = null,
    val dexSells5m: Int? = null,
    val dexBuys1h: Int? = null,
    val dexSells1h: Int? = null,
    val dexPriceChange5mPct: Double? = null,
    val dexPriceChange1hPct: Double? = null,
    val dexPriceChange6hPct: Double? = null,
    val dexPriceChange24hPct: Double? = null,
    val dexFdVUsd: Double? = null,
    val dexLiquidityBase: Double? = null,
    val dexLiquidityQuote: Double? = null,
    val dexActiveBoosts: Int? = null,
    val dexImageUrl: String? = null,
    val dexDescription: String? = null,
    val dexWebsitesJson: String? = null,
    val dexSocialsJson: String? = null,
    val dataQualityScore: Int? = null,
    val dataQualityLabel: String? = null,
    val aiDecision: String? = null,
    val aiConfidence: Int? = null,
    val aiRisk: String? = null,
    val aiSummary: String? = null,
    val aiReasonsJson: String? = null,
    val aiNegativeFactorsJson: String? = null,
    val aiRedFlagsJson: String? = null,
    val aiContradictionsJson: String? = null,
    val aiMissingDataJson: String? = null,
    val aiRecommendedMonitoringJson: String? = null,
    val aiShouldNotify: Boolean? = null,
    val aiProvider: String? = null,
    val aiAnalyzedAtEpochMs: Long? = null,
    val momentumScore: Int? = null,
    val momentumState: String? = null,
    val momentumPersistencePct: Double? = null,
    val manipulationRiskScore: Int? = null,
    val manipulationRiskLevel: String? = null,
    val manipulationFindingsJson: String? = null,
    val lifecycle: String,          // e.g. NEW, MIGRATED, TRACKING, STALE, REJECTED
    val source: String,              // "pumpportal" or "mock"
    val opportunityScore: Int? = null,
    val qualityScore: Int? = null,
    val dataConfidenceScore: Int? = null,
    val evidenceJson: String? = null,
    val marketCapVelocityPct: Double? = null,
    val marketCapAccelerationPct: Double? = null
)

@Entity(
    tableName = "trades",
    indices = [Index(value = ["mint"]), Index(value = ["timestamp"]), Index(value = ["dedupeKey"], unique = true)]
)
data class TradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mint: String,
    val dedupeKey: String,       // real signature if provided, else documented deterministic fallback
    val side: String,            // BUY / SELL
    val trader: String?,
    val amountUsd: Double?,
    val priceUsd: Double?,
    val timestamp: Long,         // epoch ms UTC
    @ColumnInfo(defaultValue = "'UNKNOWN'") val source: String = "UNKNOWN"
)

@Entity(
    tableName = "metrics",
    indices = [Index(value = ["mint"]), Index(value = ["timestamp"])]
)
data class MetricsSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mint: String,
    val timestamp: Long,
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
    val priceChangePct: Double?,   // null = INSUFFICIENT DATA
    val volumeVelocity: Double?,   // null = N/A
    val buyerVelocity: Double?,
    val sellerVelocity: Double?
)

@Entity(
    tableName = "scores",
    indices = [Index(value = ["mint"]), Index(value = ["timestamp"]), Index(value = ["score"])]
)
data class ScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mint: String,
    val timestamp: Long,
    val score: Int,
    val buyerPressure: Double?,
    val volumePressure: Double?,
    val volumeVelocity: Double?,
    val priceMomentum: Double?,
    val liquidity: Double?,
    val holderDistribution: Double?,
    val safety: Double?,
    val advancedMomentum: Double? = null,
    val manipulationRisk: Double? = null,
    val dataQuality: Double? = null,
    val opportunity: Double? = null,
    val quality: Double? = null,
    val dataConfidence: Double? = null
)

@Entity(
    tableName = "signals",
    indices = [Index(value = ["mint"]), Index(value = ["timestamp"]), Index(value = ["signalType"]), Index(value = ["score"])]
)
data class SignalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mint: String,
    val symbol: String?,
    val timestamp: Long,
    val signalType: String,     // BUY, SELL, WATCH, REJECTED
    val score: Int,
    val reasonsJson: String,    // JSON array of human-readable reasons ("WHY THIS SIGNAL?")
    val marketCapUsd: Double?,
    val liquidityUsd: Double?,
    val buyers: Int,
    val sellers: Int,
    val buyVolumeUsd: Double,
    val sellVolumeUsd: Double,
    val priceUsd: Double?,
    val lifecycleState: String? = null,
    val momentumScore: Int? = null,
    val manipulationRiskScore: Int? = null,
    val dataQualityScore: Int? = null,
    val opportunityScore: Int? = null,
    val qualityScore: Int? = null,
    val dataConfidenceScore: Int? = null
)

/** High-frequency raw observation used for reproducible walk-forward evaluation. */
@Entity(
    tableName = "token_observations",
    indices = [Index(value = ["mint", "timestamp"])]
)
data class TokenObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mint: String,
    val timestamp: Long,
    val priceUsd: Double?,
    val marketCapUsd: Double?,
    val liquidityUsd: Double?,
    val buyVolumeUsd: Double,
    val sellVolumeUsd: Double,
    val buyers: Int,
    val sellers: Int,
    val source: String
)

/** Immutable feature vector and independent scores at one evaluation point. */
@Entity(
    tableName = "token_feature_snapshots",
    indices = [Index(value = ["mint", "timestamp"])]
)
data class TokenFeatureSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mint: String,
    val timestamp: Long,
    val opportunityScore: Int?,
    val momentumScore: Int?,
    val riskScore: Int?,
    val qualityScore: Int?,
    val dataConfidenceScore: Int,
    val featuresJson: String,
    val classification: String,
    val mcDelta: Double? = null,
    val mcVelocity: Double? = null,
    val mcAcceleration: Double? = null,
    val mcDirectionalPressure: Double? = null,
    val mcPersistence: Double? = null,
    val mcNetChange: Double? = null,
    val mcNetChangePct: Double? = null,
    val mcRecentHigh: Double? = null,
    val mcDrawdownPct: Double? = null,
    val mcHigherHighCount: Int? = null,
    val mcLowerHighCount: Int? = null,
    val mcTrendClassification: String? = null
)

@Entity(
    tableName = "signal_outcomes",
    indices = [Index(value = ["signalId"]), Index(value = ["signalId", "elapsedSeconds"], unique = true)]
)
data class SignalOutcomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val signalId: Long,
    val mint: String,
    val entryPriceUsd: Double,
    val checkTimestamp: Long,
    val hypotheticalChangePct: Double,   // ALWAYS labeled HYPOTHETICAL in the UI - never "profit"
    val elapsedSeconds: Int,
    val observedPriceUsd: Double? = null,
    val signalClass: String? = null,
    val momentumScore: Int? = null,
    val riskScore: Int? = null,
    val dataQualityScore: Int? = null,
    val maxGainPct: Double? = null,
    val maxDrawdownPct: Double? = null,
    val timeToPeakSeconds: Int? = null
)

@Entity(
    tableName = "signal_transitions",
    indices = [Index(value = ["mint"]), Index(value = ["timestamp"]), Index(value = ["newState"])]
)
data class SignalTransitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mint: String,
    val timestamp: Long,
    val previousState: String?,
    val newState: String,
    val score: Int,
    val reasonsJson: String,
    val manipulationRisk: Int?,
    val dataQualityScore: Int?
)

@Entity(tableName = "system_events", indices = [Index(value = ["timestamp"])])
data class SystemEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val category: String,   // CONNECTION, PARSER_ERROR, RATE_LIMIT, DB_ERROR, SERVICE
    val message: String
)
