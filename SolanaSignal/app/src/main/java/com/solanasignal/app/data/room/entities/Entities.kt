package com.solanasignal.app.data.room.entities

import androidx.room.Entity
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
    val createdAtEpochMs: Long?,
    val firstSeenAtEpochMs: Long,
    val marketCapUsd: Double?,      // null = UNKNOWN
    val liquidityUsd: Double?,      // null = UNKNOWN
    val lastPriceUsd: Double?,
    val lifecycle: String,          // e.g. NEW, MIGRATED, TRACKING, STALE, REJECTED
    val source: String              // "pumpportal"
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
    val timestamp: Long          // epoch ms UTC
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
    val safety: Double?
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
    val priceUsd: Double?
)

@Entity(
    tableName = "signal_outcomes",
    indices = [Index(value = ["signalId"])]
)
data class SignalOutcomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val signalId: Long,
    val mint: String,
    val entryPriceUsd: Double,
    val checkTimestamp: Long,
    val hypotheticalChangePct: Double,   // ALWAYS labeled HYPOTHETICAL in the UI - never "profit"
    val elapsedSeconds: Int
)

@Entity(tableName = "system_events", indices = [Index(value = ["timestamp"])])
data class SystemEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val category: String,   // CONNECTION, PARSER_ERROR, RATE_LIMIT, DB_ERROR, SERVICE
    val message: String
)
