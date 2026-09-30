package com.solanasignal.app.data.room

import com.solanasignal.app.data.room.entities.SignalOutcomeEntity

/** Event-driven checkpoint recorder. Sparse/missing checkpoints stay absent rather than borrowing a later quote. */
class SignalOutcomeRecorder {
    private val checkpointsSeconds = listOf(30, 60, 180, 300, 600, 900, 1800, 3600, 21600, 86400)
    private val maxLatenessMs = 60_000L

    suspend fun onLiveTrade(db: AppDatabase, mint: String, eventTimestampMs: Long, priceUsd: Double?, source: String = "pumpportal") {
        val current = priceUsd?.takeIf { it.isFinite() && it > 0.0 } ?: return
        val signals = db.signalDao().forMintSince(mint, eventTimestampMs - 24 * 60 * 60 * 1000L)
        signals.forEach signalLoop@{ signal ->
            val entry = signal.priceUsd?.takeIf { it > 0.0 } ?: return@signalLoop
            checkpointsSeconds.forEach checkpointLoop@{ checkpoint ->
                val target = signal.timestamp + checkpoint * 1_000L
                if (eventTimestampMs < target || eventTimestampMs - target > maxLatenessMs) return@checkpointLoop
                if (db.signalOutcomeDao().existsCheckpoint(signal.id, checkpoint) > 0) return@checkpointLoop
                val stored = db.featureSnapshotDao().liveObservations(signal.mint, signal.timestamp, eventTimestampMs)
                    .asSequence()
                    .filter { it.source.equals(source, ignoreCase = true) }
                    .mapNotNull { observation -> observation.priceUsd?.takeIf { value -> value > 0.0 }?.let { price -> observation.timestamp to price } }
                    .toList()
                val path = (stored + listOf(signal.timestamp to entry, eventTimestampMs to current))
                    .distinctBy { it.first }
                    .sortedBy { it.first }
                val peak = path.maxByOrNull { it.second } ?: return@checkpointLoop
                val trough = path.minOfOrNull { it.second } ?: return@checkpointLoop
                db.signalOutcomeDao().insert(
                    SignalOutcomeEntity(
                        signalId = signal.id,
                        mint = mint,
                        entryPriceUsd = entry,
                        checkTimestamp = eventTimestampMs,
                        hypotheticalChangePct = (current - entry) / entry * 100.0,
                        elapsedSeconds = checkpoint,
                        observedPriceUsd = current,
                        signalClass = signal.lifecycleState ?: signal.signalType,
                        momentumScore = signal.momentumScore,
                        riskScore = signal.manipulationRiskScore,
                        dataQualityScore = signal.dataQualityScore,
                        maxGainPct = (peak.second - entry) / entry * 100.0,
                        maxDrawdownPct = (trough - entry) / entry * 100.0,
                        timeToPeakSeconds = ((peak.first - signal.timestamp) / 1_000L).toInt().coerceAtLeast(0)
                    )
                )
            }
        }
    }
}
