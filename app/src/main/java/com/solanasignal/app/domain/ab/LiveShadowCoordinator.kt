package com.solanasignal.app.domain.ab

import com.solanasignal.app.data.room.AppDatabase
import com.solanasignal.app.data.room.entities.*
import com.solanasignal.app.domain.advanced.AdvancedSignalEngineB
import com.solanasignal.app.domain.advanced.AdvancedSignalResult
import com.solanasignal.app.domain.live.LiveMarketState
import com.solanasignal.app.domain.live.LiveWindowState
import com.solanasignal.app.domain.metrics.WindowMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Shadow-only bridge. It never calls PaperTradingEngine and never changes Baseline A. */
class LiveShadowCoordinator(private val db: AppDatabase) {
    private val engineB = AdvancedSignalEngineB()
    private val previousBState = mutableMapOf<String, String>()
    private val _bObservations = MutableStateFlow(0)
    private val _bShadowSignals = MutableStateFlow(0)
    private val _shadowEntries = MutableStateFlow(0)
    val bObservations: StateFlow<Int> = _bObservations.asStateFlow()
    val bShadowSignals: StateFlow<Int> = _bShadowSignals.asStateFlow()
    val shadowEntries: StateFlow<Int> = _shadowEntries.asStateFlow()

    suspend fun evaluate(
        token: TokenEntity,
        windows: Map<Int, WindowMetrics>,
        eventId: String,
        stateTimestamp: Long,
        receiveTimestamp: Long,
        baselineState: String,
        baselineSignal: String,
        baselineScore: Int?,
        baselineReasons: List<String>
    ): AdvancedSignalResult {
        val state = buildState(token, windows, stateTimestamp, receiveTimestamp)
        val evaluationId = UUID.randomUUID().toString()
        val opportunityId = "opportunity:${token.mint}"
        val b = engineB.evaluate(state, receiveTimestamp)
        val bSignal = if (b.state.name == "STRONG_SIGNAL") "B_SHADOW_SIGNAL" else "B_SHADOW_WATCH"
        val previous = previousBState.put(token.mint, b.state.name)
        val bReasons = b.reasons + b.warnings
        val now = receiveTimestamp

        db.abObservationDao().insert(toObservation(evaluationId, eventId, opportunityId, token, state, "A", "baseline-a", baselineState, baselineSignal, baselineScore, baselineReasons, now, null))
        db.abObservationDao().insert(toObservation("$evaluationId:b", eventId, opportunityId, token, state, "B", "advanced-b-1", b.state.name, bSignal, null, bReasons, now, b))
        _bObservations.value += 1
        if (bSignal == "B_SHADOW_SIGNAL") _bShadowSignals.value += 1
        if (previous != b.state.name) {
            db.systemEventDao().insert(SystemEventEntity(timestamp = now, category = "B_STATE_CHANGE", message = "evaluationId=$evaluationId opportunityId=$opportunityId mint=${token.mint} from=${previous ?: "UNKNOWN"} to=${b.state.name} reason=${bReasons.joinToString("|")}"))
        }
        if (bSignal == "B_SHADOW_SIGNAL" && state.priceUsd != null && !state.isStale(now)) {
            db.shadowPaperDao().insert(
                ShadowPaperEntryEntity(
                    entryId = "shadow-entry:$evaluationId",
                    evaluationId = "$evaluationId:b",
                    opportunityId = opportunityId,
                    mint = token.mint,
                    symbol = token.symbol,
                    timestamp = now,
                    entryPriceUsd = state.priceUsd,
                    entryMarketCapUsd = state.marketCapUsd,
                    entryLiquidityUsd = state.liquidityUsd,
                    state = b.state.name,
                    reason = bReasons.joinToString("|")
                )
            )
            _shadowEntries.value += 1
            db.systemEventDao().insert(SystemEventEntity(timestamp = now, category = "PAPER_SHADOW_ENTRY", message = "evaluationId=$evaluationId opportunityId=$opportunityId mint=${token.mint} entryPriceUsd=${state.priceUsd}"))
        }
        db.systemEventDao().insert(SystemEventEntity(timestamp = now, category = "B_EVALUATION", message = "evaluationId=$evaluationId eventId=$eventId opportunityId=$opportunityId mint=${token.mint} state=${b.state.name} signal=$bSignal quality=${b.signalQuality ?: "UNKNOWN"} confidence=${b.dataConfidence}"))
        db.systemEventDao().insert(SystemEventEntity(timestamp = now, category = bSignal, message = "evaluationId=$evaluationId eventId=$eventId opportunityId=$opportunityId mint=${token.mint} state=${b.state.name} reason=${bReasons.joinToString("|")}"))
        return b
    }

    private fun buildState(token: TokenEntity, windows: Map<Int, WindowMetrics>, stateTimestamp: Long, receiveTimestamp: Long): LiveMarketState {
        val m5 = windows[300]
        val m1 = windows[60]
        val latest = m5?.latestPriceUsd ?: token.lastPriceUsd
        val windowsState = windows.mapValues { (seconds, m) ->
            LiveWindowState(
                seconds = seconds,
                observationCount = m.totalTrades,
                priceUsd = m.latestPriceUsd,
                marketCapUsd = token.marketCapUsd,
                liquidityUsd = token.liquidityUsd,
                volumeUsd = m.buyVolumeUsd + m.sellVolumeUsd,
                buyCount = m.buys,
                sellCount = m.sells,
                buyVolumeUsd = m.buyVolumeUsd,
                sellVolumeUsd = m.sellVolumeUsd,
                uniqueBuyers = m.uniqueBuyers,
                uniqueSellers = m.uniqueSellers,
                eventTimestampMs = stateTimestamp,
                receiveTimestampMs = receiveTimestamp,
                source = token.source
            )
        }
        val dataAge = (receiveTimestamp - stateTimestamp).coerceAtLeast(0L)
        return LiveMarketState(
            mint = token.mint,
            symbol = token.symbol,
            name = token.name,
            ageSeconds = ((stateTimestamp - token.firstSeenAtEpochMs) / 1000L).coerceAtLeast(0L),
            source = token.source,
            firstObservedAtMs = token.firstSeenAtEpochMs,
            lastEventTimestampMs = stateTimestamp,
            lastReceivedAtMs = receiveTimestamp,
            priceUsd = latest,
            marketCapUsd = token.marketCapUsd,
            liquidityUsd = token.liquidityUsd,
            liquidityToMarketCap = if (token.marketCapUsd != null && token.marketCapUsd > 0.0) token.liquidityUsd?.div(token.marketCapUsd) else null,
            volumeUsd = m5?.let { it.buyVolumeUsd + it.sellVolumeUsd },
            volumeVelocity = m1?.volumeVelocity,
            buyCount = m5?.buys,
            sellCount = m5?.sells,
            buyVolumeUsd = m5?.buyVolumeUsd,
            sellVolumeUsd = m5?.sellVolumeUsd,
            uniqueBuyers = m5?.uniqueBuyers,
            uniqueSellers = m5?.uniqueSellers,
            buyerGrowth = m1?.buyerVelocity,
            sellerGrowth = m1?.sellerVelocity,
            buyerSellerRatio = m5?.buyerSellerRatio,
            priceVelocity = token.marketCapVelocityPct,
            marketCapVelocity = token.marketCapVelocityPct,
            marketCapAcceleration = token.marketCapAccelerationPct,
            momentum = token.momentumScore?.toDouble(),
            windows = windowsState,
            dataAgeMs = dataAge,
            sourceLatencyMs = dataAge,
            stale = dataAge > 10_000L,
            confidence = token.dataConfidenceScore?.toDouble()
        )
    }

    private fun toObservation(
        evaluationId: String, eventId: String, opportunityId: String, token: TokenEntity, state: LiveMarketState,
        engine: String, version: String, decisionState: String, signal: String, score: Int?, reasons: List<String>, now: Long,
        b: AdvancedSignalResult?
    ) = ABObservationEntity(
        evaluationId = evaluationId, eventId = eventId, opportunityId = opportunityId, mint = token.mint, symbol = token.symbol,
        engine = engine, engineVersion = version, timestamp = now, stateTimestamp = state.lastEventTimestampMs ?: now,
        receiveTimestamp = state.lastReceivedAtMs ?: now, priceUsd = state.priceUsd, marketCapUsd = state.marketCapUsd,
        liquidityUsd = state.liquidityUsd, volumeUsd = state.volumeUsd, buyVolumeUsd = state.buyVolumeUsd, sellVolumeUsd = state.sellVolumeUsd,
        buyCount = state.buyCount, sellCount = state.sellCount, uniqueBuyers = state.uniqueBuyers, uniqueSellers = state.uniqueSellers,
        priceVelocity = state.priceVelocity, marketCapVelocity = state.marketCapVelocity, acceleration = state.marketCapAcceleration,
        momentum = b?.momentum?.momentum ?: state.momentum, buyPressure = b?.buyPressure?.imbalance,
        sellPressure = b?.buyPressure?.sellVolume, liquidityMetricsJson = b?.liquidity?.let { JSONObject(mapOf("liquidity" to it.liquidity, "stability" to it.stability)).toString() },
        pumpPotential = b?.pumpPotential, collapseRisk = b?.collapseRisk, dataConfidence = b?.dataConfidence,
        signalQuality = b?.signalQuality, rank = null, score = score, state = decisionState, signal = signal,
        decisionReasonsJson = JSONArray(reasons).toString(), rejectionReasonsJson = if (engine == "A") JSONArray(reasons).toString() else null,
        dataAgeMs = state.dataAgeMs, latencyMs = state.sourceLatencyMs, source = state.source,
        freshness = state.freshness(now).name, stale = state.isStale(now)
    )
}
