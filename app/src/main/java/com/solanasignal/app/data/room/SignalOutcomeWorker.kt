package com.solanasignal.app.data.room

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.solanasignal.app.di.ServiceLocator
import com.solanasignal.app.data.room.entities.SignalOutcomeEntity

/** Records measured, time-aligned forward observations; missing checkpoints remain missing. */
class SignalOutcomeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = ServiceLocator.database(applicationContext)
        val now = System.currentTimeMillis()
        val checkpoints = listOf(30, 60, 180, 300, 600, 900, 1800, 3600, 21600, 86400)
        val maxObservationLatenessMs = 60_000L
        val signals = db.signalDao().since(now - 24 * 60 * 60 * 1000L)

        signals.forEach { signal ->
            val entry = signal.priceUsd?.takeIf { it > 0.0 } ?: return@forEach
            checkpoints.forEach checkpointLoop@{ checkpoint ->
                val target = signal.timestamp + checkpoint * 1_000L
                if (target > now || db.signalOutcomeDao().existsCheckpoint(signal.id, checkpoint) > 0) return@checkpointLoop
                val observation = db.featureSnapshotDao().firstObservationAtOrAfter(
                    signal.mint, target, minOf(now, target + maxObservationLatenessMs)
                ) ?: return@checkpointLoop
                val observed = observation.priceUsd?.takeIf { it > 0.0 } ?: return@checkpointLoop
                val path = db.featureSnapshotDao().liveObservations(signal.mint, signal.timestamp, observation.timestamp)
                    .mapNotNull { point -> point.priceUsd?.takeIf { it > 0.0 }?.let { point.timestamp to it } }
                    .plus(signal.timestamp to entry)
                    .plus(observation.timestamp to observed)
                    .distinctBy { it.first }
                    .sortedBy { it.first }
                val peak = path.maxByOrNull { it.second } ?: return@checkpointLoop
                val trough = path.minOfOrNull { it.second } ?: return@checkpointLoop
                val change = ((observed - entry) / entry) * 100.0
                db.signalOutcomeDao().insert(
                    SignalOutcomeEntity(
                        signalId = signal.id,
                        mint = signal.mint,
                        entryPriceUsd = entry,
                        checkTimestamp = observation.timestamp,
                        hypotheticalChangePct = change,
                        elapsedSeconds = checkpoint,
                        observedPriceUsd = observed,
                        signalClass = signal.lifecycleState ?: signal.signalType,
                        momentumScore = signal.momentumScore,
                        riskScore = signal.manipulationRiskScore,
                        dataQualityScore = signal.dataQualityScore,
                        maxGainPct = ((peak.second - entry) / entry) * 100.0,
                        maxDrawdownPct = ((trough - entry) / entry) * 100.0,
                        timeToPeakSeconds = ((peak.first - signal.timestamp) / 1_000L).toInt().coerceAtLeast(0)
                    )
                )
            }
        }
        return Result.success()
    }
}
