package com.solanasignal.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.solanasignal.app.MainActivity
import com.solanasignal.app.di.ServiceLocator
import com.solanasignal.app.notifications.NotificationHelper
import kotlinx.coroutines.*

/**
 * Foreground service required for continuous background scanning (spec #25).
 * Shows a persistent notification while active and never falsely claims to be
 * scanning if Android has stopped it - the notification only reflects state the
 * service actually holds.
 */
class ScannerForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    companion object {
        const val ACTION_START = "com.solanasignal.app.action.START_SCANNER"
        const val ACTION_STOP = "com.solanasignal.app.action.STOP_SCANNER"
        const val NOTIFICATION_ID = 42
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                ServiceLocator.orchestrator(applicationContext).stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification(tracked = 0, connected = false))
                ServiceLocator.orchestrator(applicationContext).start()
                observeAndUpdateNotification()
            }
        }
        return START_STICKY
    }

    private fun observeAndUpdateNotification() {
        monitorJob?.cancel()
        val orchestrator = ServiceLocator.orchestrator(applicationContext)
        monitorJob = serviceScope.launch {
            while (isActive) {
                delay(3000)
                val connected = orchestrator.connectionState.value.name == "CONNECTED"
                val notif = buildNotification(tracked = 0, connected = connected)
                (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
                    .notify(NOTIFICATION_ID, notif)
            }
        }
    }

    private fun buildNotification(tracked: Int, connected: Boolean): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pending = android.app.PendingIntent.getActivity(
            this, 0, openAppIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_SCANNER)
            .setContentTitle("Solana Signal")
            .setContentText("Scanning PumpPortal... Connection: ${if (connected) "Connected" else "Connecting"}")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        super.onDestroy()
    }
}
