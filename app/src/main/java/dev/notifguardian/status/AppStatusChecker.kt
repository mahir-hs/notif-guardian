package dev.notifguardian.status

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.PowerManager
import dev.notifguardian.TargetApp
import dev.notifguardian.xiaomi.Millet

enum class Tri { YES, NO, UNKNOWN }

data class AppStatus(
    val target: TargetApp,
    /** Force-stopped: the app receives no FCM / broadcasts until it is opened again. */
    val stopped: Boolean,
    /** Exempt from Android battery optimization (Doze). */
    val batteryUnrestricted: Boolean,
    /** Xiaomi autostart permission (hidden MIUI app-op 10008). */
    val autostart: Tri,
    /** Android "background restricted" (RUN_ANY_IN_BACKGROUND). YES = allowed. */
    val backgroundAllowed: Tri,
    /** On HyperOS's Millet no-restrict list, i.e. not frozen when the screen is off. */
    val milletExempt: Tri,
)

object AppStatusChecker {
    private const val MIUI_OP_AUTO_START = 10008

    fun check(context: Context, target: TargetApp): AppStatus? {
        val ai = try {
            context.packageManager.getApplicationInfo(target.pkg, 0)
        } catch (_: Exception) {
            return null
        }
        val power = context.getSystemService(PowerManager::class.java)
        return AppStatus(
            target = target,
            stopped = ai.flags and ApplicationInfo.FLAG_STOPPED != 0,
            batteryUnrestricted = power.isIgnoringBatteryOptimizations(target.pkg),
            autostart = miuiAutostart(context, ai),
            backgroundAllowed = runAnyInBackground(context, ai),
            milletExempt = Millet.isExempt(context, target.pkg),
        )
    }

    fun isStopped(context: Context, pkg: String): Boolean = try {
        context.packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_STOPPED != 0
    } catch (_: Exception) {
        false
    }

    private fun miuiAutostart(context: Context, ai: ApplicationInfo): Tri = try {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val method = AppOpsManager::class.java.getMethod(
            "checkOpNoThrow",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
        )
        modeToTri(method.invoke(ops, MIUI_OP_AUTO_START, ai.uid, ai.packageName) as Int)
    } catch (_: Throwable) {
        Tri.UNKNOWN
    }

    @Suppress("DEPRECATION")
    private fun runAnyInBackground(context: Context, ai: ApplicationInfo): Tri = try {
        val ops = context.getSystemService(AppOpsManager::class.java)
        modeToTri(ops.unsafeCheckOpNoThrow("android:run_any_in_background", ai.uid, ai.packageName))
    } catch (_: Throwable) {
        Tri.UNKNOWN
    }

    private fun modeToTri(mode: Int) = when (mode) {
        AppOpsManager.MODE_ALLOWED -> Tri.YES
        AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> Tri.NO
        else -> Tri.UNKNOWN
    }
}
