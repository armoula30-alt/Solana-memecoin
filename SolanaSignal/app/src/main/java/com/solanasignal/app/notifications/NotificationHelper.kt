package com.solanasignal.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.solanasignal.app.MainActivity
import com.solanasignal.app.domain.metrics.WindowMetrics
import com.solanasignal.app.domain.signals.SignalType
import com.solanasignal.app.photon.PhotonLauncher

object NotificationHelper {
    const val CHANNEL_BUY = "buy_signals"
    const val CHANNEL_SELL = "sell_signals"
    const val CHANNEL_SAFETY = "safety_alerts"
    const val CHANNEL_SYSTEM = "system_alerts"
    const val CHANNEL_SCANNER = "scanner_status" // low-importance foreground-service channel

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(CHANNEL_BUY, "Buy Signals", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_SELL, "Sell Signals", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_SAFETY, "Safety Alerts", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_SYSTEM, "System Alerts", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CHANNEL_SCANNER, "Scanner Status", NotificationManager.IMPORTANCE_LOW)
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    fun showSignalNotification(
        context: Context,
        signalId: Long,
        mint: String,
        symbol: String,
        type: SignalType,
        score: Int,
        m5: WindowMetrics,
        reasons: List<String>
    ) {
        val channel = if (type == SignalType.BUY) CHANNEL_BUY else CHANNEL_SELL
        val emoji = if (type == SignalType.BUY) "\uD83D\uDEA8" else "\uD83D\uDD34"
        val title = "$emoji ${if (type == SignalType.BUY) "NEW SOLANA SIGNAL" else "SELL SIGNAL"}"

        val body = buildString {
            append("$$symbol\n")
            append("Buyers: ${m5.uniqueBuyers}  Sellers: ${m5.uniqueSellers}\n")
            append("Buy/Sell Vol: %.2fx  ".format(m5.buySellVolumeRatio))
            m5.volumeVelocity?.let { append("Velocity: %.1fx\n".format(it)) } ?: append("Velocity: N/A\n")
            append("Score: $score/100\n")
            append("PAPER / SIGNAL ONLY")
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to_signal_id", signalId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context, signalId.toInt(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val photonIntent = PhotonLauncher.buildOpenIntent(context, mint)
        val photonPending = PendingIntent.getActivity(
            context, (signalId + 1_000_000).toInt(), photonIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_notify_sync) // replace with app icon asset
            .setContentTitle(title)
            .setContentText("$$symbol \u2022 Score $score/100")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .addAction(0, "VIEW", tapPending)
            .addAction(0, "OPEN PHOTON", photonPending)

        androidx.core.app.NotificationManagerCompat.from(context).notify(signalId.toInt(), builder.build())
    }
}
