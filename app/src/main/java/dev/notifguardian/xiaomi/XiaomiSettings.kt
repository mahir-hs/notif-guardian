package dev.notifguardian.xiaomi

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.notifguardian.log.DiagLog

/**
 * Deep links into Xiaomi (MIUI / HyperOS) settings screens. Xiaomi moves these around between
 * versions, so each screen has an ordered list of candidates; the first one that launches wins and
 * the result is logged so diagnostics show which ones work on a given HyperOS build.
 */
object XiaomiSettings {
    enum class Screen { AUTOSTART, BATTERY_SAVER, NOTIFICATIONS, OTHER_PERMISSIONS, APP_INFO }

    private const val SECURITY = "com.miui.securitycenter"
    private const val POWERKEEPER = "com.miui.powerkeeper"

    fun open(context: Context, pkg: String, label: String, screen: Screen): Boolean {
        for ((name, intent) in candidates(pkg, label, screen)) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                DiagLog.i("Xiaomi", "open $screen for $pkg via '$name': ok")
                return true
            } catch (e: Exception) {
                DiagLog.w("Xiaomi", "open $screen for $pkg via '$name' failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        DiagLog.e("Xiaomi", "open $screen for $pkg: no candidate worked")
        return false
    }

    /** Every candidate for every screen, for the diagnostics report. */
    fun allCandidates(pkg: String, label: String) =
        Screen.entries.associateWith { candidates(pkg, label, it) }

    fun candidates(pkg: String, label: String, screen: Screen): List<Pair<String, Intent>> = when (screen) {
        Screen.AUTOSTART -> listOf(
            "security-autostart-list" to Intent().setComponent(
                ComponentName(SECURITY, "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ),
            "security-app-details" to securityAppDetails(pkg, label),
            "std-app-details" to stdAppDetails(pkg),
        )
        Screen.BATTERY_SAVER -> listOf(
            "powerkeeper-app-config" to Intent()
                .setComponent(ComponentName(POWERKEEPER, "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                .putExtra("package_name", pkg)
                .putExtra("package_label", label),
            "security-app-details" to securityAppDetails(pkg, label),
            "std-battery-opt-list" to Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        Screen.NOTIFICATIONS -> listOf(
            "std-app-notifications" to Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
            "std-app-details" to stdAppDetails(pkg),
        )
        Screen.OTHER_PERMISSIONS -> listOf(
            "security-perm-editor" to Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName(SECURITY, "com.miui.permcenter.permissions.PermissionsEditorActivity")
                .putExtra("extra_pkgname", pkg),
            "security-perm-editor-action" to Intent("miui.intent.action.APP_PERM_EDITOR")
                .putExtra("extra_pkgname", pkg),
            "security-app-details" to securityAppDetails(pkg, label),
        )
        Screen.APP_INFO -> listOf(
            "security-app-details" to securityAppDetails(pkg, label),
            "std-app-details" to stdAppDetails(pkg),
        )
    }

    private fun securityAppDetails(pkg: String, label: String) = Intent()
        .setComponent(ComponentName(SECURITY, "com.miui.appmanager.ApplicationsDetailsActivity"))
        .putExtra("package_name", pkg)
        .putExtra("package_label", label)

    private fun stdAppDetails(pkg: String) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
}
