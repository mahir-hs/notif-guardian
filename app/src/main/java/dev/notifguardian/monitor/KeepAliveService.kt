package dev.notifguardian.monitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.notifguardian.log.DiagLog

/** Keeps our process (and so the Watchdog ticker) alive with a silent foreground notification. */
class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this, Notifier.KEEPALIVE_ID, Notifier.keepAlive(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        running = true
        DiagLog.i("KeepAlive", "started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Watchdog.check(this, "keepalive-start")
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        DiagLog.w("KeepAlive", "destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile
        var running = false
            private set

        fun isEnabled(context: Context) = prefs(context).getBoolean("enabled", true)

        fun setEnabled(context: Context, enabled: Boolean) {
            prefs(context).edit().putBoolean("enabled", enabled).apply()
            if (enabled) start(context, "user") else context.stopService(Intent(context, KeepAliveService::class.java))
        }

        fun start(context: Context, reason: String) {
            if (!isEnabled(context)) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, KeepAliveService::class.java))
            } catch (e: Exception) {
                DiagLog.e("KeepAlive", "start failed ($reason)", e)
            }
        }

        private fun prefs(context: Context) = context.getSharedPreferences("keepalive", Context.MODE_PRIVATE)
    }
}
