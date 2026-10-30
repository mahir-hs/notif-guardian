package dev.notifguardian.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.notifguardian.R
import dev.notifguardian.TargetApp
import dev.notifguardian.ui.MainActivity

object Notifier {
    private const val CH_ALERTS = "alerts"
    private const val CH_KEEPALIVE = "keepalive"
    const val KEEPALIVE_ID = 1
    private const val DELAY_ALERT_COOLDOWN_MS = 30 * 60 * 1000L

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Problems with protected apps", NotificationManager.IMPORTANCE_HIGH),
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_KEEPALIVE, "Guardian running", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            },
        )
    }

    fun keepAlive(context: Context): Notification =
        Notification.Builder(context, CH_KEEPALIVE)
            .setSmallIcon(R.drawable.ic_stat_guardian)
            .setContentTitle("Watching your messaging apps")
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .build()

    /** The app was force-stopped: tapping the alert opens it, which brings its push back. */
    fun alertStopped(context: Context, target: TargetApp) {
        val revive = context.packageManager.getLaunchIntentForPackage(target.pkg)
            ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", target.pkg, null))
        val pi = PendingIntent.getActivity(
            context, target.pkg.hashCode(), revive.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        notify(
            context, stoppedId(target),
            Notification.Builder(context, CH_ALERTS)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("${target.label} was killed by the system")
                .setContentText("You won't get its notifications. Tap to revive it.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun cancelStopped(context: Context, target: TargetApp) =
        context.getSystemService(NotificationManager::class.java).cancel(stoppedId(target))

    fun alertDelay(context: Context, target: TargetApp, delayMs: Long) {
        val prefs = context.getSharedPreferences("notifier", Context.MODE_PRIVATE)
        val key = "lastDelayAlert_${target.pkg}"
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(key, 0) < DELAY_ALERT_COOLDOWN_MS) return
        prefs.edit().putLong(key, now).apply()

        val minutes = delayMs / 60_000
        notify(
            context, target.pkg.hashCode() + 2,
            Notification.Builder(context, CH_ALERTS)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("${target.label} notification arrived $minutes min late")
                .setContentText("The system was holding it back. Tap to check its settings.")
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .build(),
        )
    }

    fun updated(context: Context, version: String) = notify(
        context, 3,
        Notification.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_guardian)
            .setContentTitle("Notif Guardian updated to $version")
            .setContentText("Tap to open it.")
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build(),
    )

    private fun stoppedId(target: TargetApp) = target.pkg.hashCode() + 1

    private fun notify(context: Context, id: Int, n: Notification) {
        try {
            context.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted yet
        }
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )
}
