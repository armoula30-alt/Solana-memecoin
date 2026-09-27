package com.solanasignal.app.data.room

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.solanasignal.app.di.ServiceLocator
import kotlin.math.roundToInt

/**
 * Records observed forward price changes for historical signals. Values are
 * hypothetical observations, not executed trades and not guaranteed returns.
 */
class SignalOutcomeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = ServiceLocator.database(applicationContext)
        val now = System.currentTimeMillis()
        val checkpoints = listOf(30, 60, 180, 300, 600, 1800, 3600, 21600, 86400)
        val signals = db.signalDao().since(now - 24 * 60 * 60 * 1000L)
        signals.forEach { signal ->
            val entry = signal.priceUsd ?: return@forEach
            if (entry <= 0.0) return@forEach
            val token = db.tokenDao().getByMint(signal.mint) ?: return@forEach
            val current = token.lastPriceUsd ?: return@forEach
            val elapsed = ((now - signal.timestamp) / 1000L).toInt()
            checkpoints.filter { it <= elapsed }.forEach { checkpoint ->
                if (db.signalOutcomeDao().existsCheckpoint(signal.id, checkpoint) > 0) return@forEach
                val change = ((current - entry) / entry) * 100.0
                db.signalOutcomeDao().insert(
                    SignalOutcomeEntity(
                        signalId = signal.id,
                        mint = signal.mint,
                        entryPriceUsd = entry,
                        checkTimestamp = now,
                        hypotheticalChangePct = change,
                        elapsedSeconds = checkpoint,
                        observedPriceUsd = current,
                        signalClass = signal.lifecycleState ?: signal.signalType,
                        momentumScore = signal.momentumScore,
                        riskScore = signal.manipulationRiskScore,
                        dataQualityScore = signal.dataQualityScore
                    )
                )
            }
        }
        return Result.success()
    }
}
