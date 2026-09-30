package com.solanasignal.app.data.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "market_events", indices = [Index(value = ["mint", "timestamp"]), Index(value = ["source"])] )
data class MarketEventEntity(
    @PrimaryKey val eventId: String,
    val timestamp: Long,
    val receiveTimestamp: Long,
    val source: String,
    val mint: String,
    val eventType: String,
    val normalizedPayload: String,
    val schemaVersion: Int = 1
)

@Entity(tableName = "ab_observations", indices = [Index(value = ["opportunityId", "timestamp"]), Index(value = ["engine"]), Index(value = ["mint"])])
data class ABObservationEntity(
    @PrimaryKey val evaluationId: String,
    val eventId: String,
    val opportunityId: String,
    val mint: String,
    val symbol: String?,
    val engine: String,
    val engineVersion: String,
    val timestamp: Long,
    val stateTimestamp: Long,
    val receiveTimestamp: Long,
    val priceUsd: Double?,
    val marketCapUsd: Double?,
    val liquidityUsd: Double?,
    val volumeUsd: Double?,
    val buyVolumeUsd: Double?,
    val sellVolumeUsd: Double?,
    val buyCount: Int?,
    val sellCount: Int?,
    val uniqueBuyers: Int?,
    val uniqueSellers: Int?,
    val priceVelocity: Double?,
    val marketCapVelocity: Double?,
    val acceleration: Double?,
    val momentum: Double?,
    val buyPressure: Double?,
    val sellPressure: Double?,
    val liquidityMetricsJson: String?,
    val pumpPotential: Double?,
    val collapseRisk: Double?,
    val dataConfidence: Double?,
    val signalQuality: Double?,
    val rank: Int?,
    val score: Int?,
    val state: String,
    val signal: String,
    val decisionReasonsJson: String,
    val rejectionReasonsJson: String?,
    val dataAgeMs: Long?,
    val latencyMs: Long?,
    val source: String?,
    val freshness: String,
    val stale: Boolean
)

@Entity(tableName = "ab_outcomes", indices = [Index(value = ["evaluationId", "horizonSeconds"], unique = true), Index(value = ["mint", "checkTimestamp"])])
data class ABOutcomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val evaluationId: String,
    val mint: String,
    val engine: String,
    val horizonSeconds: Int,
    val signalTimestamp: Long,
    val checkTimestamp: Long,
    val entryPriceUsd: Double?,
    val observedPriceUsd: Double?,
    val observedMarketCapUsd: Double?,
    val observedLiquidityUsd: Double?,
    val returnPct: Double?,
    val maxGainPct: Double?,
    val maxDrawdownPct: Double?,
    val timeToPeakSeconds: Int?,
    val timeToMaxDrawdownSeconds: Int?,
    val classification: String?
)

@Entity(tableName = "shadow_paper_entries", indices = [Index(value = ["mint"]), Index(value = ["timestamp"])])
data class ShadowPaperEntryEntity(
    @PrimaryKey val entryId: String,
    val evaluationId: String,
    val opportunityId: String,
    val mint: String,
    val symbol: String?,
    val timestamp: Long,
    val entryPriceUsd: Double,
    val entryMarketCapUsd: Double?,
    val entryLiquidityUsd: Double?,
    val state: String,
    val reason: String,
    val status: String = "OPEN"
)

@Entity(tableName = "data_quality", indices = [Index(value = ["timestamp"]), Index(value = ["source"])])
data class DataQualityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val source: String?,
    val totalEvents: Int,
    val validEvents: Int,
    val invalidEvents: Int,
    val duplicateEvents: Int,
    val outOfOrderEvents: Int,
    val staleStates: Int,
    val missingFields: Int,
    val unknownFields: Int,
    val providerLatencyMs: Long?,
    val eventLatencyMs: Long?,
    val stateUpdateLatencyMs: Long?,
    val sourceCoverage: Double?
)
