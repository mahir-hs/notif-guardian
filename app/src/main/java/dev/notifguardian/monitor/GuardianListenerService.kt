package dev.notifguardian.monitor

import android.app.Notification
import android.content.ComponentName
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.notifguardian.ProtectedApps
import dev.notifguardian.TargetApp
import dev.notifguardian.log.DiagLog

/**
 * Measures delivery delay: messaging apps set Notification.when to the message's send time, so
 * postTime - when tells us how long the OS held the push back. Only metadata is recorded,
 * never notification text.
 */
class GuardianListenerService : NotificationListenerService() {

    /** Notification key -> last seen `when`, so re-posts of the same message aren't counted twice. */
    private val seenWhen = HashMap<String, Long>()

    override fun onListenerConnected() {
        DiagLog.i("Listener", "connected")
        Watchdog.check(this, "listener-connected")
    }

    override fun onListenerDisconnected() {
        DiagLog.w("Listener", "disconnected, requesting rebind")
        requestRebind(ComponentName(this, GuardianListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!ProtectedApps.isProtected(sbn.packageName)) return
        val target = TargetApp(sbn.packageName, ProtectedApps.label(this, sbn.packageName))
        val n = sbn.notification
        val isSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0
        val isUpdate = seenWhen.put(sbn.key, n.`when`) == n.`when`
        val delayMs = if (n.`when` > 0) sbn.postTime - n.`when` else -1L
        val screenOn = getSystemService(PowerManager::class.java).isInteractive

        DiagLog.i(
            "Listener",
            "posted pkg=${sbn.packageName} id=${sbn.id} cat=${n.category} summary=$isSummary " +
                "ongoing=${sbn.isOngoing} update=$isUpdate delay=${delayMs}ms screenOn=$screenOn",
        )
        if (isSummary || isUpdate || sbn.isOngoing) return

        EventStore.add(
            this, EventStore.POSTED, sbn.packageName, delayMs,
            mapOf("category" to n.category, "screenOn" to screenOn),
        )
        if (delayMs >= DELAY_THRESHOLD_MS) {
            DiagLog.w("Listener", "LATE ${sbn.packageName} by ${delayMs / 1000}s")
            Notifier.alertDelay(this, target, delayMs)
        }
        // Any sign of life from a protected app is a good moment to re-check the others
        Watchdog.check(this, "posted")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        seenWhen.remove(sbn.key)
    }

    companion object {
        const val DELAY_THRESHOLD_MS = 3 * 60 * 1000L
    }
}
