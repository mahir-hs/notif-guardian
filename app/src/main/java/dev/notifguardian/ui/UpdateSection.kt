package dev.notifguardian.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.notifguardian.App
import dev.notifguardian.BuildConfig
import dev.notifguardian.update.HelperInstallState
import dev.notifguardian.update.HelperInstaller
import dev.notifguardian.update.Release
import dev.notifguardian.update.UpdateState
import dev.notifguardian.update.Updater
import java.util.Locale

@Composable
fun UpdateSection() {
    val context = LocalContext.current
    val p = LocalPalette.current
    // App-wide scope: the download keeps going if the screen is rotated or recomposed
    val scope = (context.applicationContext as App).scope
    val state by Updater.state.collectAsState()

    // Coming back from "Install unknown apps": carry on if it was allowed
    LifecycleResumeEffect(state) {
        val s = state
        if (s is UpdateState.NeedsInstallPermission && context.packageManager.canRequestPackageInstalls()) {
            Updater.downloadAndInstall(context, scope, s.release)
        }
        onPauseOrDispose { }
    }

    Column {
        Text("Updates", style = MaterialTheme.typography.titleLarge)
        Text(
            "You have version ${BuildConfig.VERSION_NAME}.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.inkMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        if (!Updater.configured) {
            Text("This build doesn't check for updates.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        when (val s = state) {
            UpdateState.Idle -> OutlinedButton(onClick = { Updater.check(scope) }) { Text("Check for updates") }

            UpdateState.Checking -> Progress(null, "Checking GitHub for a new version…")

            is UpdateState.UpToDate -> {
                Text("You have the latest version.", style = MaterialTheme.typography.bodyLarge)
                HelperUpdate(s.release)
                TextButton(onClick = { Updater.check(scope) }) { Text("Check again") }
            }

            is UpdateState.Available -> {
                Text(
                    "Version ${s.release.version} is available (${mb(s.release.apkSize)}).",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (s.release.notes.isNotBlank()) {
                    Text(
                        s.release.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = p.inkMuted,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Button(
                    onClick = { Updater.downloadAndInstall(context, scope, s.release) },
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text("Download and install") }
                HelperUpdate(s.release)
            }

            is UpdateState.NeedsInstallPermission -> {
                Text(
                    "Android needs your permission for Guardian to install its own updates. " +
                        "Turn on \"Allow from this source\", then come back.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { context.startActivity(Updater.installPermissionIntent(context)) },
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text("Allow installs") }
            }

            is UpdateState.Downloading -> {
                val fraction = if (s.total > 0) s.done.toFloat() / s.total else 0f
                Progress(
                    fraction,
                    "Downloading ${s.release.version}: ${percent(fraction)} (${mb(s.done)} of ${mb(s.total)})",
                )
            }

            UpdateState.Verifying -> Progress(null, "Checking the download…")

            is UpdateState.Installing -> Progress(
                s.progress,
                "Installing: ${percent(s.progress)}. Guardian closes and reopens when it's done.",
            )

            UpdateState.WaitingForConfirm -> Progress(1f, "Confirm the update on the screen Android just opened.")

            is UpdateState.Failed -> {
                Text(s.message, style = MaterialTheme.typography.bodyMedium, color = p.broken)
                Row(Modifier.padding(top = 12.dp)) {
                    val release = s.release
                    Button(onClick = {
                        if (release != null) Updater.downloadAndInstall(context, scope, release) else Updater.check(scope)
                    }) { Text(if (release != null) "Install update" else "Try again") }
                }
            }
        }
    }
}

/** Offers a newer Guardian Helper from [release], if it has one and a helper is installed. */
@Composable
private fun HelperUpdate(release: Release) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val installed = HelperInstaller.installedVersion(context) ?: return // not installed: set up in its own section
    if (!HelperInstaller.isUpdate(context, release)) return
    Column(Modifier.padding(top = 16.dp)) {
        Text(
            "Guardian Helper ${release.helperVersion} is available (you have $installed).",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "After it installs, HyperOS turns the helper's autostart off. Guardian shows you where to turn it back on.",
            style = MaterialTheme.typography.bodySmall,
            color = p.inkMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
        val state by HelperInstaller.state.collectAsState()
        if (state == HelperInstallState.Idle || state is HelperInstallState.Failed) {
            OutlinedButton(
                onClick = { HelperInstaller.install(context, (context.applicationContext as App).scope) },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Install helper update") }
        }
        HelperInstallProgress(Modifier.padding(top = 8.dp))
    }
}

/** fraction null = indeterminate. */
@Composable
private fun Progress(fraction: Float?, label: String) {
    Column(Modifier.fillMaxWidth()) {
        if (fraction == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun percent(f: Float) = "${(f.coerceIn(0f, 1f) * 100).toInt()}%"

private fun mb(bytes: Long) = String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
