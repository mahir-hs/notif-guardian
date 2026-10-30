package dev.notifguardian.xiaomi

import android.app.Activity
import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import dev.notifguardian.ProtectedApps
import dev.notifguardian.TargetApp
import dev.notifguardian.log.DiagLog
import dev.notifguardian.status.AppStatusChecker
import dev.notifguardian.status.Tri

/**
 * HyperOS rebuilds MILLET_NO_RESTRICT_APP from its own battery-saver database whenever any app's
 * battery saver changes, dropping entries it doesn't know (and system apps like Google Play
 * services can't be set to "No restrictions" in the UI at all). This watches the list and asks
 * Guardian Helper (an API-22 app that may write it) to put protected apps back.
 */
object MilletKeeper {
    const val HELPER_PKG = "dev.notifguardian.helper"
    private const val ACTION_APPLY = "dev.notifguardian.helper.APPLY"
    private const val PREFS = "millet_keeper"

    /** Retry delays when the helper doesn't answer, e.g. because the freezer still has it frozen. */
    private val RETRY_DELAYS_MS = longArrayOf(1_500, 4_000, 10_000)

    data class HelperStatus(
        val installed: Boolean,
        val canWriteSettings: Boolean,
        val autostart: Tri,
        /**
         * Helper's own battery saver set to "No restrictions". Without it, the same HyperOS rebuild
         * that drops Google Play services also freezes the helper, and it can't answer.
         */
        val exempt: Tri,
        /** Last reply from the helper; null if it never answered. */
        val lastResult: String?,
    ) {
        val responding get() = lastResult?.startsWith("ok") == true
        val ready get() = installed && canWriteSettings && autostart != Tri.NO && exempt != Tri.NO && responding
    }

    private val handler = Handler(Looper.getMainLooper())

    fun start(context: Context) {
        val app = context.applicationContext
        app.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Millet.KEY),
            false,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = ensure(app, "list-changed")
            },
        )
        ensure(app, "start")
    }

    fun status(context: Context): HelperStatus {
        val ai = try {
            context.packageManager.getApplicationInfo(HELPER_PKG, 0)
        } catch (_: Exception) {
            return HelperStatus(
                installed = false, canWriteSettings = false, autostart = Tri.UNKNOWN, exempt = Tri.UNKNOWN, lastResult = null,
            )
        }
        @Suppress("DEPRECATION")
        val write = context.getSystemService(AppOpsManager::class.java)
            .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_WRITE_SETTINGS, ai.uid, HELPER_PKG)
        return HelperStatus(
            installed = true,
            canWriteSettings = write == AppOpsManager.MODE_ALLOWED,
            autostart = AppStatusChecker.check(context, TargetApp(HELPER_PKG, "Guardian Helper"))?.autostart ?: Tri.UNKNOWN,
            exempt = Millet.isExempt(context, HELPER_PKG),
            lastResult = prefs(context).getString("lastResult", null),
        )
    }

    /** Re-adds any protected app (plus Guardian and the helper) missing from the list. */
    fun ensure(context: Context, reason: String, attempt: Int = 0) {
        val list = Millet.noRestrictList(context) ?: return
        val missing = wanted(context).filter { it !in list && ProtectedApps.isInstalled(context, it) }
        if (missing.isEmpty()) return
        if (!status(context).installed) {
            DiagLog.w("MilletKeeper", "missing from ${Millet.KEY}: ${missing.joinToString()} (helper not installed, $reason)")
            return
        }
        DiagLog.i("MilletKeeper", "re-adding ${missing.joinToString()} ($reason${if (attempt > 0) ", retry $attempt" else ""})")
        send(context, missing) { ok ->
            if (!ok && attempt < RETRY_DELAYS_MS.size) {
                handler.postDelayed({ ensure(context, reason, attempt + 1) }, RETRY_DELAYS_MS[attempt])
            }
        }
    }

    private fun wanted(context: Context) = ProtectedApps.packages.value + context.packageName + HELPER_PKG

    /** Asks the helper to respond even if nothing is missing, to check it works. */
    fun ping(context: Context, onDone: () -> Unit = {}) {
        val list = Millet.noRestrictList(context).orEmpty()
        val missing = wanted(context).filter { it !in list && ProtectedApps.isInstalled(context, it) }
        send(context, missing) { onDone() }
    }

    /** Opens the helper's invisible screen: Android shows its one-time permission review first. */
    fun openHelper(context: Context) {
        try {
            context.startActivity(
                Intent().setComponent(ComponentName(HELPER_PKG, "$HELPER_PKG.ReviewActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            DiagLog.e("MilletKeeper", "can't open helper", e)
        }
    }

    fun writeSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$HELPER_PKG"))

    private fun send(context: Context, packages: List<String>, onDone: (ok: Boolean) -> Unit) {
        context.sendOrderedBroadcast(
            Intent(ACTION_APPLY)
                .setPackage(HELPER_PKG)
                // The helper has no UI, so it is never launched and stays in the "stopped" state
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtra("packages", packages.toTypedArray()),
            null,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    // No reply means Android skipped the helper (permission review pending, or blocked)
                    val result = resultData ?: "error: helper didn't respond"
                    prefs(c).edit().putString("lastResult", result).apply()
                    val ok = result.startsWith("ok")
                    if (ok) DiagLog.i("MilletKeeper", "helper: $result") else DiagLog.e("MilletKeeper", "helper: $result")
                    onDone(ok)
                }
            },
            null,
            Activity.RESULT_OK,
            null,
            null,
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
