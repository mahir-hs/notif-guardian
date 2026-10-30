package dev.notifguardian.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.notifguardian.App
import dev.notifguardian.update.HelperInstallState
import dev.notifguardian.update.HelperInstaller
import dev.notifguardian.update.Updater
import java.util.Locale

/**
 * Progress and next step for installing Guardian Helper. Shows nothing while idle, so callers put
 * their own "Install" button next to it.
 */
@Composable
fun HelperInstallProgress(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val scope = (context.applicationContext as App).scope
    val state by HelperInstaller.state.collectAsState()

    LifecycleResumeEffect(state) {
        // Back from Settings or the installer: carry on, or clear a finished install
        if (state is HelperInstallState.NeedsInstallPermission && context.packageManager.canRequestPackageInstalls()) {
            HelperInstaller.install(context, scope)
        }
        HelperInstaller.refresh(context)
        onPauseOrDispose { }
    }

    Column(modifier) {
        when (val s = state) {
            HelperInstallState.Idle -> Unit
            HelperInstallState.Checking -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Finding the latest Guardian Helper…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            HelperInstallState.NeedsInstallPermission -> {
                Text(
                    "Android needs your permission for Guardian to install apps. Turn on \"Allow from this source\", then come back.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = { context.startActivity(Updater.installPermissionIntent(context)) },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Allow installs") }
            }
            is HelperInstallState.Downloading -> {
                val fraction = if (s.total > 0) s.done.toFloat() / s.total else 0f
                LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(
                    "Downloading Guardian Helper ${s.version}: ${(fraction * 100).toInt()}% " +
                        "(${mb(s.done)} of ${mb(s.total)})",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            is HelperInstallState.Handed -> Text(
                "Tap Install on the screen that opened. HyperOS may warn that the helper is built for an " +
                    "older Android version; that's expected. Then come back here and turn every row green.",
                style = MaterialTheme.typography.bodySmall,
            )
            is HelperInstallState.Failed -> Text(s.message, style = MaterialTheme.typography.bodySmall, color = p.broken)
        }
    }
}

private fun mb(bytes: Long) = String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
