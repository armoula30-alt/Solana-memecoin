package com.solanasignal.app.data.room

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.solanasignal.app.data.settings.SettingsRepository

/**
 * Periodic cleanup honoring the configured retention policy (spec #38).
 * "Unlimited" retention (days == null) is a no-op.
 */
class RetentionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val now = System.currentTimeMillis()
        val days = SettingsRepository.get(applicationContext).retentionPolicy.value.days
        if (days != null) {
            val cutoff = now - days * 24L * 60 * 60 * 1000
            db.tokenDao().deleteOlderThan(cutoff)
            db.tradeDao().deleteOlderThan(cutoff)
            db.metricsDao().deleteOlderThan(cutoff)
            db.scoreDao().deleteOlderThan(cutoff)
            db.signalDao().deleteOlderThan(cutoff)
            db.signalOutcomeDao().deleteOlderThan(cutoff)
            db.signalTransitionDao().deleteOlderThan(cutoff)
            db.systemEventDao().deleteOlderThan(cutoff)
        }
        val diagnosticCutoff = now - 7L * 24 * 60 * 60 * 1000
        db.diagnosticDao().deleteEventsBefore(diagnosticCutoff)
        db.diagnosticDao().retainNewestEvents(25_000)
        db.diagnosticDao().deleteSessionsBefore(diagnosticCutoff)
        return Result.success()
    }
}
