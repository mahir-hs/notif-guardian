package dev.notifguardian.diag

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import dev.notifguardian.BuildConfig
import dev.notifguardian.TargetApp
import dev.notifguardian.ProtectedApps
import dev.notifguardian.log.DiagLog
import dev.notifguardian.monitor.EventStore
import dev.notifguardian.monitor.KeepAliveService
import dev.notifguardian.status.AppStatusChecker
import dev.notifguardian.xiaomi.Millet
import dev.notifguardian.xiaomi.XiaomiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a single zip with everything needed to diagnose a problem remotely:
 * report.txt (device, permissions, per-app status, which Xiaomi screens resolve, delay stats),
 * the app log, the event log, our own logcat and recent process-exit reasons.
 * Contains no notification text.
 */
object Diagnostics {
    private val stampFmt = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    suspend fun export(context: Context): File = withContext(Dispatchers.IO) {
        DiagLog.i("Diag", "exporting diagnostics")
        val dir = File(context.cacheDir, "diagnostics").apply {
            deleteRecursively()
            mkdirs()
        }
        val zip = File(dir, "notifguardian-diag-${stampFmt.format(Date())}.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putText("report.txt", report(context))
            z.putText("exit-reasons.txt", exitReasons(context))
            z.putText("logcat.txt", ownLogcat())
            DiagLog.logFiles().forEach { z.putFile("logs/${it.name}", it) }
            EventStore.file(context).takeIf { it.exists() }?.let { z.putFile("events.jsonl", it) }
        }
        zip
    }

    fun share(context: Context, zip: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", zip)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Notif Guardian diagnostics ${zip.name}")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share diagnostics").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun report(context: Context): String = buildString {
        val pm = context.packageManager
        val power = context.getSystemService(PowerManager::class.java)
        line("== Notif Guardian ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ==")
        line("generated: ${timeFmt.format(Date())}")
        line()
        line("== Device ==")
        line("manufacturer=${Build.MANUFACTURER} brand=${Build.BRAND} model=${Build.MODEL} device=${Build.DEVICE}")
        line("market name=${prop("ro.product.marketname")}")
        line("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT} security patch=${Build.VERSION.SECURITY_PATCH}")
        line("hyperos=${prop("ro.mi.os.version.name")} ${prop("ro.mi.os.version.incremental")} miui=${prop("ro.miui.ui.version.name")}")
        line("build=${Build.DISPLAY}")
        line("power save mode=${power.isPowerSaveMode} device idle=${power.isDeviceIdleMode}")
        line("${Millet.KEY}=${Millet.noRestrictList(context)?.joinToString() ?: "(unreadable)"}")
        line()
        line("== Guardian setup ==")
        line("notification access=${NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)}")
        line("post notifications=${NotificationManagerCompat.from(context).areNotificationsEnabled()}")
        line("battery unrestricted=${power.isIgnoringBatteryOptimizations(context.packageName)}")
        line("keep-alive enabled=${KeepAliveService.isEnabled(context)} running=${KeepAliveService.running}")
        AppStatusChecker.check(context, TargetApp(context.packageName, "Guardian"))?.let {
            line("guardian autostart=${it.autostart} background=${it.backgroundAllowed}")
        }
        line()
        line("== Protected apps ==")
        val weekAgo = System.currentTimeMillis() - 7 * 24 * 3600 * 1000L
        val events = EventStore.read(context, weekAgo)
        line("protected=${ProtectedApps.packages.value.sorted().joinToString()}")
        for (t in ProtectedApps.list(context)) {
            val s = AppStatusChecker.check(context, t) ?: continue
            val version = runCatching { pm.getPackageInfo(t.pkg, 0).versionName }.getOrNull()
            val posted = events.filter { it.optString("type") == EventStore.POSTED && it.optString("pkg") == t.pkg }
            val delays = posted.map { it.optLong("delayMs") }
            val late = delays.count { it >= 3 * 60_000 }
            val stops = events.count { it.optString("type") == EventStore.STOPPED && it.optString("pkg") == t.pkg }
            line("${t.pkg} v$version")
            line(
                "  stopped=${s.stopped} batteryUnrestricted=${s.batteryUnrestricted} autostart=${s.autostart} " +
                    "background=${s.backgroundAllowed} milletExempt=${s.milletExempt}",
            )
            line("  7d: posted=${posted.size} late(>3m)=$late maxDelay=${(delays.maxOrNull() ?: 0) / 1000}s stoppedEvents=$stops")
        }
        line()
        line("== Xiaomi screen candidates (resolves?) ==")
        XiaomiSettings.allCandidates("com.whatsapp", "WhatsApp").forEach { (screen, list) ->
            list.forEach { (name, intent) ->
                val resolved = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                line("$screen / $name -> ${resolved?.activityInfo?.let { "${it.packageName}/${it.name} exported=${it.exported}" } ?: "NOT FOUND"}")
            }
        }
    }

    fun describeExit(e: ApplicationExitInfo): String {
        val reason = when (e.reason) {
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_CRASH -> "CRASH"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
            ApplicationExitInfo.REASON_OTHER -> "OTHER"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
            ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
            ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
            ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
            ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
            else -> "code ${e.reason}"
        }
        return "${timeFmt.format(Date(e.timestamp))} reason=$reason status=${e.status} " +
            "importance=${e.importance} desc=${e.description}"
    }

    private fun exitReasons(context: Context): String =
        context.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(context.packageName, 0, 30)
            .joinToString("\n") { describeExit(it) }
            .ifEmpty { "(none recorded)" }

    private fun ownLogcat(): String = runCatching {
        val p = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "5000", "--pid=${android.os.Process.myPid()}")
            .redirectErrorStream(true)
            .start()
        p.inputStream.bufferedReader().readText()
    }.getOrElse { "logcat failed: $it" }

    private fun prop(key: String): String = runCatching {
        ProcessBuilder("getprop", key).start().inputStream.bufferedReader().readText().trim()
    }.getOrDefault("")

    private fun StringBuilder.line(s: String = "") = append(s).append('\n')

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.putFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }
}
