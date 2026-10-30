package dev.notifguardian

import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dev.notifguardian.diag.Diagnostics
import dev.notifguardian.log.DiagLog
import dev.notifguardian.monitor.KeepAliveService
import dev.notifguardian.monitor.Notifier
import dev.notifguardian.monitor.Watchdog
import dev.notifguardian.xiaomi.MilletKeeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        DiagLog.init(this)
        installCrashLogger()
        DiagLog.i("App", "process start v${BuildConfig.VERSION_NAME} pid=${android.os.Process.myPid()}")
        logNewExitReasons()

        ProtectedApps.init(this)
        Notifier.createChannels(this)
        MilletKeeper.start(this)
        Watchdog.start(this, scope)
        KeepAliveService.start(this, "app-start")
        registerScreenReceiver()
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            DiagLog.e("Crash", "uncaught on ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
    }

    /** Logs how our previous process died (e.g. killed by HyperOS) — once per death. */
    private fun logNewExitReasons() {
        val prefs = getSharedPreferences("app", Context.MODE_PRIVATE)
        val lastSeen = prefs.getLong("lastExitTs", 0)
        val reasons = getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(packageName, 0, 10)
            .filter { it.timestamp > lastSeen }
        reasons.reversed().forEach { DiagLog.w("App", "previous process exit: ${Diagnostics.describeExit(it)}") }
        reasons.maxOfOrNull { it.timestamp }?.let { prefs.edit().putLong("lastExitTs", it).apply() }
    }

    /** Unlocking the phone is when users notice missing notifications, so check right then. */
    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    DiagLog.i("Screen", intent.action?.substringAfterLast('.') ?: "?")
                    if (intent.action != Intent.ACTION_SCREEN_OFF) Watchdog.check(context, "screen")
                }
            },
            filter,
        )
    }
}
