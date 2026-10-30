package dev.notifguardian.monitor

import android.content.Context
import dev.notifguardian.ProtectedApps
import dev.notifguardian.log.DiagLog
import dev.notifguardian.status.AppStatusChecker
import dev.notifguardian.xiaomi.Millet
import dev.notifguardian.xiaomi.MilletKeeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Detects protected apps entering the force-stopped state (which is what HyperOS does when it
 * "cleans" them) and alerts the user so one tap brings their push delivery back.
 */
object Watchdog {
    private const val INTERVAL_MS = 60_000L
    private val lastStopped = mutableMapOf<String, Boolean>()
    private var ticker: Job? = null
    private var lastMillet: List<String>? = null

    fun start(context: Context, scope: CoroutineScope) {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                check(context, "tick")
                delay(INTERVAL_MS)
            }
        }
    }

    @Synchronized
    fun check(context: Context, reason: String) {
        val millet = Millet.noRestrictList(context)?.sorted()
        if (millet != lastMillet) {
            DiagLog.i("Watchdog", "${Millet.KEY} ${if (lastMillet == null) "is" else "CHANGED to"}: ${millet?.joinToString()}")
            lastMillet = millet
        }
        MilletKeeper.ensure(context, reason)
        for (target in ProtectedApps.list(context)) {
            val stopped = AppStatusChecker.isStopped(context, target.pkg)
            val previous = lastStopped.put(target.pkg, stopped)
            if (stopped && previous != true) {
                DiagLog.w("Watchdog", "${target.pkg} is STOPPED (check=$reason, previous=$previous)")
                EventStore.add(context, EventStore.STOPPED, target.pkg, extra = mapOf("check" to reason))
                Notifier.alertStopped(context, target)
            } else if (!stopped && previous == true) {
                DiagLog.i("Watchdog", "${target.pkg} is running again (check=$reason)")
                EventStore.add(context, EventStore.REVIVED, target.pkg, extra = mapOf("check" to reason))
                Notifier.cancelStopped(context, target)
            }
        }
    }
}
