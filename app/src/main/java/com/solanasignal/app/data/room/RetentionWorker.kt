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
        val days = SettingsRepository.get(applicationContext).retentionPolicy.value.days ?: return Result.success()
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val db = AppDatabase.get(applicationContext)
        db.tokenDao().deleteOlderThan(cutoff)
        db.tradeDao().deleteOlderThan(cutoff)
        db.metricsDao().deleteOlderThan(cutoff)
        db.scoreDao().deleteOlderThan(cutoff)
        db.signalDao().deleteOlderThan(cutoff)
        db.signalOutcomeDao().deleteOlderThan(cutoff)
        db.signalTransitionDao().deleteOlderThan(cutoff)
        db.systemEventDao().deleteOlderThan(cutoff)
        return Result.success()
    }
}
