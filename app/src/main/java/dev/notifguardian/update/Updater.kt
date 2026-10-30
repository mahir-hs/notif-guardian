package dev.notifguardian.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import dev.notifguardian.BuildConfig
import dev.notifguardian.log.DiagLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A downloadable file attached to a release. */
data class Asset(
    val name: String,
    val url: String,
    val size: Long,
    /** From the release's SHA256SUMS.txt; null if the release has none. */
    val sha256: String?,
)

data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    val sha256: String?,
    /** Guardian Helper attached to the release; its file name carries the helper's own version. */
    val helper: Asset? = null,
    val helperVersion: String? = null,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val latest: String, val release: Release) : UpdateState
    data class Available(val release: Release) : UpdateState
    /** Android needs "Install unknown apps" allowed for Guardian before it can install. */
    data class NeedsInstallPermission(val release: Release) : UpdateState
    data class Downloading(val release: Release, val done: Long, val total: Long) : UpdateState
    data object Verifying : UpdateState
    data class Installing(val progress: Float) : UpdateState
    data object WaitingForConfirm : UpdateState
    data class Failed(val message: String, val release: Release?) : UpdateState
}

/**
 * Self-update from the GitHub repo's latest release. Guardian installs itself through a
 * PackageInstaller session; Guardian Helper (API 22) can't be installed that way, so
 * [HelperInstaller] hands it to the system installer instead. Android rejects any APK not signed
 * with the same key, so only the real releases install.
 */
object Updater {
    private const val APK_PREFIX = "NotifGuardian-"
    private const val HELPER_PREFIX = "GuardianHelper-"
    private const val SUMS_ASSET = "SHA256SUMS.txt"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val repo: String = BuildConfig.UPDATE_REPO
    val configured get() = repo.isNotBlank()

    fun check(scope: CoroutineScope) {
        if (!configured) return
        _state.value = UpdateState.Checking
        scope.launch(Dispatchers.IO) {
            _state.value = try {
                val release = fetchLatest()
                if (isNewer(release.version, BuildConfig.VERSION_NAME)) {
                    UpdateState.Available(release)
                } else {
                    UpdateState.UpToDate(release.version, release)
                }
            } catch (e: Exception) {
                DiagLog.w("Updater", "check failed", e)
                UpdateState.Failed("Couldn't check GitHub: ${e.message ?: e.javaClass.simpleName}", null)
            }
        }
    }

    fun downloadAndInstall(context: Context, scope: CoroutineScope, release: Release) {
        val app = context.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsInstallPermission(release)
            return
        }
        _state.value = UpdateState.Downloading(release, 0, release.apkSize)
        scope.launch(Dispatchers.IO) {
            try {
                val apk = download(app, release)
                install(app, apk)
            } catch (e: Exception) {
                DiagLog.e("Updater", "update failed", e)
                _state.value = UpdateState.Failed("Update failed: ${e.message ?: e.javaClass.simpleName}", release)
            }
        }
    }

    fun installPermissionIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    internal fun onInstallResult(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                _state.value = UpdateState.WaitingForConfirm
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // Usually never seen: Android restarts Guardian as soon as it's replaced
                DiagLog.i("Updater", "installed")
                _state.value = UpdateState.Installing(1f)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                DiagLog.w("Updater", "install cancelled")
                _state.value = UpdateState.Failed("Install cancelled. Tap Install update to try again.", lastRelease)
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                DiagLog.e("Updater", "install failed: status=$status $message")
                _state.value = UpdateState.Failed("Install failed: ${message ?: "status $status"}", lastRelease)
            }
        }
    }

    @Volatile
    private var lastRelease: Release? = null

    /** Reads the repo's latest release. Blocking: call off the main thread. */
    internal fun fetchLatest(): Release {
        val json = JSONObject(get("https://api.github.com/repos/$repo/releases/latest"))
        val version = json.getString("tag_name").removePrefix("v")
        val assets = json.getJSONArray("assets")
        var apk: JSONObject? = null
        var helper: JSONObject? = null
        var sumsUrl: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.getString("name")
            if (name.startsWith(APK_PREFIX) && name.endsWith(".apk")) apk = a
            if (name.startsWith(HELPER_PREFIX) && name.endsWith(".apk")) helper = a
            if (name == SUMS_ASSET) sumsUrl = a.getString("browser_download_url")
        }
        apk ?: throw IOException("release $version has no $APK_PREFIX*.apk")
        // Strip a UTF-8 BOM: Windows tools like to add one
        val sums = sumsUrl?.let { get(it).removePrefix("﻿").lines() }.orEmpty()
        fun shaOf(name: String) = sums.firstOrNull { it.trim().endsWith(name) }?.trim()?.substringBefore(' ')
        fun asset(a: JSONObject) = a.getString("name").let {
            Asset(it, a.getString("browser_download_url"), a.getLong("size"), shaOf(it))
        }

        val main = asset(apk)
        val helperAsset = helper?.let { asset(it) }
        return Release(
            version = version,
            notes = json.optString("body").trim(),
            apkUrl = main.url,
            apkName = main.name,
            apkSize = main.size,
            sha256 = main.sha256,
            helper = helperAsset,
            helperVersion = helperAsset?.name?.removePrefix(HELPER_PREFIX)?.removeSuffix(".apk"),
        ).also { lastRelease = it }
    }

    private fun download(context: Context, release: Release): File =
        downloadAsset(context, Asset(release.apkName, release.apkUrl, release.apkSize, release.sha256)) { done, total ->
            _state.value = UpdateState.Downloading(release, done, total)
        }

    /** Downloads [asset] into cache/updates, reporting progress, and checks its SHA-256 if known. */
    internal fun downloadAsset(context: Context, asset: Asset, onProgress: (done: Long, total: Long) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply {
            deleteRecursively()
            mkdirs()
        }
        val file = File(dir, asset.name)
        val conn = open(asset.url)
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: asset.size
        conn.inputStream.use { input ->
            file.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var lastReport = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done += n
                    if (done - lastReport > 32 * 1024) {
                        onProgress(done, total)
                        lastReport = done
                    }
                }
                onProgress(done, total)
            }
        }
        DiagLog.i("Updater", "downloaded ${asset.name} (${file.length()} bytes)")
        val expected = asset.sha256
        if (expected != null && !sha256(file).equals(expected, ignoreCase = true)) {
            file.delete()
            throw IOException("the download is damaged (checksum doesn't match)")
        }
        return file
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.registerSessionCallback(
            object : PackageInstaller.SessionCallback() {
                override fun onCreated(id: Int) = Unit
                override fun onBadgingChanged(id: Int) = Unit
                override fun onActiveChanged(id: Int, active: Boolean) = Unit
                override fun onProgressChanged(id: Int, progress: Float) {
                    // System progress: 0..0.8 is our copy (staging), the rest is the actual install
                    if (id == sessionId && _state.value !is UpdateState.WaitingForConfirm) {
                        _state.value = UpdateState.Installing(progress)
                    }
                }
                override fun onFinished(id: Int, success: Boolean) {
                    if (id == sessionId) installer.unregisterSessionCallback(this)
                }
            },
            Handler(Looper.getMainLooper()),
        )

        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val fraction = done.toFloat() / apk.length()
                        session.setStagingProgress(fraction)
                        _state.value = UpdateState.Installing(fraction * 0.8f)
                    }
                }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java),
                // Mutable: the installer fills in the status extras
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            pendingVersion(context, lastRelease?.version)
            DiagLog.i("Updater", "committing install session $sessionId")
            session.commit(callback.intentSender)
        }
    }

    /**
     * Called after Guardian is replaced. Returns the new version if this replacement was an update
     * Guardian installed itself (not, say, an adb install), and forgets it.
     */
    fun consumeCompletedUpdate(context: Context): String? {
        val prefs = context.getSharedPreferences("updater", Context.MODE_PRIVATE)
        val pending = prefs.getString("pendingVersion", null) ?: return null
        prefs.edit().remove("pendingVersion").apply()
        return pending.takeIf { it == BuildConfig.VERSION_NAME }
    }

    private fun pendingVersion(context: Context, version: String?) {
        context.getSharedPreferences("updater", Context.MODE_PRIVATE).edit().putString("pendingVersion", version).apply()
    }

    private fun get(url: String): String = open(url).inputStream.bufferedReader().use { it.readText() }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "NotifGuardian/${BuildConfig.VERSION_NAME}")
        if (conn.responseCode == 404) throw IOException("no published release found in $repo")
        if (conn.responseCode !in 200..299) throw IOException("GitHub answered ${conn.responseCode}")
        return conn
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** True if [candidate] (e.g. "0.2.0") is a higher version than [current]. */
    internal fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
