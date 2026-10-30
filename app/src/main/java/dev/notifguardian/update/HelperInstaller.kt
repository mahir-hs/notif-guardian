package dev.notifguardian.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.notifguardian.log.DiagLog
import dev.notifguardian.xiaomi.MilletKeeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface HelperInstallState {
    data object Idle : HelperInstallState
    data object Checking : HelperInstallState
    /** Android needs "Install unknown apps" allowed for Guardian before it can open the installer. */
    data object NeedsInstallPermission : HelperInstallState
    data class Downloading(val version: String, val done: Long, val total: Long) : HelperInstallState
    /** The system install screen is open; the user finishes there. */
    data class Handed(val version: String) : HelperInstallState
    data class Failed(val message: String) : HelperInstallState
}

/**
 * Installs or updates Guardian Helper from the latest release. The helper targets API 22, which
 * Android refuses for PackageInstaller sessions from ordinary apps, so it's handed to the system
 * installer as a file, just like opening a downloaded APK (HyperOS's installer accepts it).
 */
object HelperInstaller {
    private val _state = MutableStateFlow<HelperInstallState>(HelperInstallState.Idle)
    val state: StateFlow<HelperInstallState> = _state.asStateFlow()

    fun installedVersion(context: Context): String? = try {
        context.packageManager.getPackageInfo(MilletKeeper.HELPER_PKG, 0).versionName
    } catch (_: Exception) {
        null
    }

    /** True if [release] carries a helper newer than the installed one (or none is installed). */
    fun isUpdate(context: Context, release: Release): Boolean {
        val latest = release.helperVersion ?: return false
        val installed = installedVersion(context) ?: return true
        return Updater.isNewer(latest, installed)
    }

    fun install(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            _state.value = HelperInstallState.NeedsInstallPermission
            return
        }
        _state.value = HelperInstallState.Checking
        scope.launch(Dispatchers.IO) {
            try {
                val release = Updater.fetchLatest()
                val asset = release.helper ?: error("the latest release has no Guardian Helper")
                val version = release.helperVersion.orEmpty()
                val file = Updater.downloadAsset(app, asset) { done, total ->
                    _state.value = HelperInstallState.Downloading(version, done, total)
                }
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                withContext(Dispatchers.Main) {
                    app.startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                DiagLog.i("HelperInstaller", "opened system installer for ${asset.name}")
                _state.value = HelperInstallState.Handed(version)
            } catch (e: Exception) {
                DiagLog.e("HelperInstaller", "helper install failed", e)
                _state.value = HelperInstallState.Failed("Couldn't get Guardian Helper: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Back on Guardian's screen: forget a finished hand-off once the helper is installed. */
    fun refresh(context: Context) {
        val s = _state.value
        if (s is HelperInstallState.Handed && installedVersion(context) == s.version) {
            _state.value = HelperInstallState.Idle
        }
    }
}
