package com.solanasignal.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.solanasignal.app.data.room.RetentionWorker
import com.solanasignal.app.di.ServiceLocator
import com.solanasignal.app.notifications.NotificationHelper
import java.util.concurrent.TimeUnit

class SolanaSignalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        ServiceLocator.init(this)
        scheduleRetentionCleanup()
    }

    private fun scheduleRetentionCleanup() {
        val request = PeriodicWorkRequestBuilder<RetentionWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "retention_cleanup", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }
}
