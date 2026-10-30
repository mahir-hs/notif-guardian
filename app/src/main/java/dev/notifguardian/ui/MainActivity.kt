package dev.notifguardian.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.notifguardian.App
import dev.notifguardian.ProtectedApps
import dev.notifguardian.TargetApp
import dev.notifguardian.diag.Diagnostics
import dev.notifguardian.log.DiagLog
import dev.notifguardian.monitor.EventStore
import dev.notifguardian.monitor.GuardianListenerService
import dev.notifguardian.monitor.KeepAliveService
import dev.notifguardian.status.AppStatus
import dev.notifguardian.status.AppStatusChecker
import dev.notifguardian.status.Tri
import dev.notifguardian.update.HelperInstaller
import dev.notifguardian.update.Updater
import dev.notifguardian.xiaomi.MilletKeeper
import dev.notifguardian.xiaomi.XiaomiSettings
import dev.notifguardian.xiaomi.XiaomiSettings.Screen
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GuardianTheme { MainScreen() } }
    }
}

private const val WEEK_MS = 7 * 24 * 3600_000L

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val p = LocalPalette.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val protectedPkgs by ProtectedApps.packages.collectAsState()

    val self = remember(refresh) { AppStatusChecker.check(context, TargetApp(context.packageName, "Notif Guardian")) }
    val statuses = remember(refresh, protectedPkgs) {
        ProtectedApps.list(context).mapNotNull { AppStatusChecker.check(context, it) }
    }
    val events = remember(refresh) { EventStore.read(context, System.currentTimeMillis() - WEEK_MS) }
    val listenerOn = remember(refresh) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val alertsOn = remember(refresh) { NotificationManagerCompat.from(context).areNotificationsEnabled() }

    var picking by remember { mutableStateOf(false) }
    var detailPkg by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(p.background).windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { Hero(statuses, listenerOn, onFix = { detailPkg = it }) }
        item { SetupSection(self, listenerOn, alertsOn, onChanged = { refresh++ }) }
        item { HelperSection(refresh, onChanged = { refresh++ }) }
        item {
            ProtectedSection(statuses, events, onOpen = { detailPkg = it }, onAdd = { picking = true })
        }
        item { LateSection(events) }
        item { UpdateSection() }
        item { SupportSection() }
        item { DiagnosticsSection() }
    }

    if (picking) {
        AppPickerSheet(onDismiss = {
            picking = false
            refresh++
        })
    }
    val detail = statuses.firstOrNull { it.target.pkg == detailPkg }
    if (detail != null) {
        AppDetailSheet(detail, statsFor(detail.target.pkg, events), onDismiss = {
            detailPkg = null
            refresh++
        })
    }
}

/** The one thing to know: can your apps reach you right now, and what to fix first. */
@Composable
private fun Hero(statuses: List<AppStatus>, listenerOn: Boolean, onFix: (String) -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val failing = statuses.filter { it.problems().isNotEmpty() }
    val total = statuses.size

    val title: String
    val detail: String
    var action: Pair<String, () -> Unit>? = null
    when {
        !listenerOn -> {
            title = "Guardian can't see your notifications yet"
            detail = "Give it notification access so it can tell when messages arrive late."
            action = "Give access" to {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        failing.isEmpty() -> {
            title = if (total == 1) "Your app can reach you" else "All $total apps can reach you"
            detail = "Nothing is blocking their notifications right now."
        }
        else -> {
            val first = failing.first()
            title = "${total - failing.size} of $total apps can reach you"
            val more = if (failing.size > 1) " ${failing.size - 1} more need attention." else ""
            detail = "${first.target.label}: ${first.problems().first().kind.broken.lowercase()}.$more"
            action = "Fix ${first.target.label}" to { onFix(first.target.pkg) }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(p.heroBackground)
            .padding(24.dp)
            .animateContentSize(),
    ) {
        Text("Notif Guardian", style = MaterialTheme.typography.labelLarge, color = p.heroMuted)
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            color = p.heroText,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(detail, style = MaterialTheme.typography.bodyLarge, color = p.heroMuted, modifier = Modifier.padding(top = 8.dp))
        if (statuses.isNotEmpty() && listenerOn) {
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                statuses.forEach {
                    Box(
                        Modifier.size(width = 22.dp, height = 6.dp).clip(CircleShape)
                            .background(if (it.problems().isEmpty()) p.clear else p.broken),
                    )
                }
            }
        }
        action?.let { (label, onClick) ->
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = p.heroText, contentColor = p.heroBackground),
                modifier = Modifier.padding(top = 20.dp),
            ) { Text(label) }
        }
    }
}

@Composable
private fun SetupSection(self: AppStatus?, listenerOn: Boolean, alertsOn: Boolean, onChanged: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }
    val batteryOk = self?.batteryUnrestricted == true
    val autostart = self?.autostart ?: Tri.UNKNOWN
    val frozen = self?.milletExempt ?: Tri.UNKNOWN
    val complete = listenerOn && alertsOn && batteryOk && autostart != Tri.NO && frozen != Tri.NO
    var expanded by remember(complete) { mutableStateOf(!complete) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (complete) "Guardian is set up" else "Finish setting up Guardian",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (complete) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Show") }
            }
        }
        if (!complete) {
            Text(
                "Guardian needs these so HyperOS doesn't stop it too.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.inkMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (expanded) {
            Group(Modifier.padding(top = 12.dp)) {
                SetupRow("Notification access", "Lets Guardian see when notifications arrive.", listenerOn, "Give access") {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                if (!listenerOn) RestrictedSettingsHint()
                Divider()
                SetupRow("Guardian's alerts", "So it can tell you when an app is stopped.", alertsOn, "Allow") {
                    if (Build.VERSION.SDK_INT >= 33) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                }
                Divider()
                SetupRow("No battery restrictions", "Keeps Doze from pausing Guardian.", batteryOk, "Allow") {
                    requestIgnoreBatteryOptimizations(context)
                }
                Divider()
                SetupRow("Autostart", "Lets Guardian restart after a reboot.", autostart.toOk(), "Turn on") {
                    XiaomiSettings.open(context, context.packageName, "Notif Guardian", Screen.AUTOSTART)
                }
                Divider()
                SetupRow(
                    "Not frozen when the screen is off",
                    "Set Guardian's battery saver to \"No restrictions\".",
                    frozen.toOk(),
                    "Open battery saver",
                ) {
                    XiaomiSettings.open(context, context.packageName, "Notif Guardian", Screen.BATTERY_SAVER)
                }
                Divider()
                var keepAlive by remember { mutableStateOf(KeepAliveService.isEnabled(context)) }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Stay running", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Shows a silent notification so Guardian can keep checking your apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.inkMuted,
                        )
                    }
                    Switch(checked = keepAlive, onCheckedChange = {
                        keepAlive = it
                        KeepAliveService.setEnabled(context, it)
                    })
                }
            }
        }
    }
}

/**
 * Android 13+ blocks notification access for apps installed from a downloaded file until the user
 * allows "restricted settings" once in App info. Nothing in the app can avoid it.
 */
@Composable
private fun RestrictedSettingsHint() {
    val context = LocalContext.current
    val p = LocalPalette.current
    Column(Modifier.padding(start = 40.dp, end = 16.dp, bottom = 12.dp)) {
        Text(
            "If Android says it's blocked because the app comes from an unknown source: open App info, " +
                "tap ⋮ at the top right, choose \"Allow restricted settings\", then tap Give access again. " +
                "This is needed once for apps installed from a downloaded file.",
            style = MaterialTheme.typography.bodySmall,
            color = p.inkMuted,
        )
        TextButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        }) { Text("Open App info") }
    }
}

/**
 * Google Play services can't be exempted from HyperOS's freezer in Settings, and HyperOS drops it
 * from the exemption list on every battery-saver change. Guardian Helper puts it back.
 */
@Composable
private fun HelperSection(refresh: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val status = remember(refresh) { MilletKeeper.status(context) }
    var expanded by remember(status.ready) { mutableStateOf(!status.ready) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Keep Google Play services awake",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (status.ready) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Show") }
            }
        }
        Text(
            if (status.ready) {
                "Guardian Helper puts it back on HyperOS's exemption list whenever it's removed."
            } else {
                "HyperOS freezes it when the screen is off and offers no setting to stop that. " +
                    "Guardian Helper can keep it exempt."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = p.inkMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (!expanded) return@Column
        Group(Modifier.padding(top = 12.dp)) {
            SetupRow(
                "Guardian Helper installed",
                "Guardian downloads it from GitHub and opens the install screen. It has no icon; it works in the background.",
                status.installed,
                if (Updater.configured) "Install" else null,
            ) { HelperInstaller.install(context, (context.applicationContext as App).scope) }
            if (!status.installed) HelperInstallProgress(Modifier.padding(start = 40.dp, end = 16.dp, bottom = 12.dp))
            if (status.installed) {
                Divider()
                SetupRow(
                    "Helper can change system settings",
                    "Turn on \"Modify system settings\" for Guardian Helper.",
                    status.canWriteSettings,
                    "Allow",
                ) { context.startActivity(MilletKeeper.writeSettingsIntent()) }
                Divider()
                SetupRow(
                    "Helper autostart",
                    "Lets Guardian wake the helper when the list changes.",
                    status.autostart.toOk(),
                    "Turn on",
                ) { XiaomiSettings.open(context, MilletKeeper.HELPER_PKG, "Guardian Helper", Screen.AUTOSTART) }
                Divider()
                SetupRow(
                    "Helper never frozen",
                    "Set Guardian Helper's battery saver to \"No restrictions\", or HyperOS freezes it right when it's needed.",
                    status.exempt.toOk(),
                    "Open battery saver",
                ) { XiaomiSettings.open(context, MilletKeeper.HELPER_PKG, "Guardian Helper", Screen.BATTERY_SAVER) }
                Divider()
                SetupRow(
                    "Helper is responding",
                    status.lastResult?.let { "Last reply: $it. Open the helper and confirm its permissions, then test again." }
                        ?: "Open the helper and confirm its permissions, then test it.",
                    status.responding,
                    "Open helper",
                ) { MilletKeeper.openHelper(context) }
                if (!status.responding) {
                    TextButton(
                        onClick = { MilletKeeper.ping(context) { onChanged() } },
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                    ) { Text("Test helper") }
                }
            }
        }
    }
}

@Composable
private fun SetupRow(title: String, detail: String, ok: Boolean?, action: String?, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(10.dp).clip(CircleShape).background(
                when (ok) {
                    true -> p.clear
                    false -> p.broken
                    null -> p.unknown
                },
            ),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (ok != true) Text(detail, style = MaterialTheme.typography.bodySmall, color = p.inkMuted)
        }
        if (ok != true && action != null) {
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun ProtectedSection(
    statuses: List<AppStatus>,
    events: List<JSONObject>,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val p = LocalPalette.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Protected apps", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onAdd) { Text("Add apps") }
        }
        Group(Modifier.padding(top = 8.dp)) {
            statuses.forEachIndexed { i, status ->
                key(status.target.pkg) {
                    if (i > 0) Divider()
                    AppRow(status, statsFor(status.target.pkg, events)) { onOpen(status.target.pkg) }
                }
            }
        }
        if (statuses.size <= 1) {
            Text(
                "Add the apps whose notifications matter to you, like WhatsApp or Telegram.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.inkMuted,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun AppRow(status: AppStatus, stats: AppStats, onClick: () -> Unit) {
    val p = LocalPalette.current
    val problems = status.problems()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(status.target.pkg)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(status.target.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (problems.isNotEmpty()) {
                Text(
                    problems.first().kind.broken + if (problems.size > 1) " and ${problems.size - 1} more" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.broken,
                )
            } else {
                Text(
                    when {
                        status.target.pkg == ProtectedApps.GMS -> "Delivers push for your other apps"
                        stats.late > 0 -> "${stats.late} late this week"
                        else -> plural(stats.posted, "notification") + " this week"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = p.inkMuted,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        SignalStrip(status.checks())
    }
}

@Composable
private fun LateSection(events: List<JSONObject>) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val late = remember(events) {
        events
            .filter { it.optString("type") == EventStore.POSTED && it.optLong("delayMs") >= GuardianListenerService.DELAY_THRESHOLD_MS }
            .takeLast(10)
            .reversed()
    }
    val fmt = remember { SimpleDateFormat("EEE HH:mm", Locale.getDefault()) }
    Column {
        Text("Late notifications", style = MaterialTheme.typography.titleLarge)
        Text(
            "Arrived more than ${GuardianListenerService.DELAY_THRESHOLD_MS / 60_000} minutes after they were sent, in the last 7 days.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.inkMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (late.isEmpty()) {
            Text(
                "None so far.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            return@Column
        }
        Group(Modifier.padding(top = 12.dp)) {
            late.forEachIndexed { i, e ->
                if (i > 0) Divider()
                val pkg = e.optString("pkg")
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(pkg, 28.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ProtectedApps.label(context, pkg), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${formatDelay(e.optLong("delayMs"))} late" + if (e.optBoolean("screenOn")) "" else ", screen was off",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.broken,
                        )
                    }
                    Text(fmt.format(Date(e.optLong("ts"))), style = MaterialTheme.typography.bodySmall, color = p.inkMuted)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsSection() {
    val context = LocalContext.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    Column {
        Text("Diagnostics", style = MaterialTheme.typography.titleLarge)
        Text(
            "Packs Guardian's logs and your app settings into one file to send to the developer. " +
                "Message content is never included.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.inkMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        OutlinedButton(
            enabled = !exporting,
            onClick = {
                scope.launch {
                    exporting = true
                    try {
                        Diagnostics.share(context, Diagnostics.export(context))
                    } catch (e: Exception) {
                        DiagLog.e("UI", "diagnostics export failed", e)
                        Toast.makeText(context, "Couldn't create the file: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        exporting = false
                    }
                }
            },
        ) { Text(if (exporting) "Collecting…" else "Share diagnostics") }
    }
}

@Composable
private fun Group(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LocalPalette.current.surface),
    ) { content() }
}

@Composable
private fun Divider() = HorizontalDivider(color = LocalPalette.current.line, modifier = Modifier.padding(start = 16.dp))

private fun statsFor(pkg: String, events: List<JSONObject>): AppStats {
    val mine = events.filter { it.optString("pkg") == pkg }
    val delays = mine.filter { it.optString("type") == EventStore.POSTED }.map { it.optLong("delayMs") }
    return AppStats(
        posted = delays.size,
        late = delays.count { it >= GuardianListenerService.DELAY_THRESHOLD_MS },
        maxDelayMs = delays.maxOrNull() ?: 0,
        kills = mine.count { it.optString("type") == EventStore.STOPPED },
    )
}

private fun Tri.toOk(): Boolean? = when (this) {
    Tri.YES -> true
    Tri.NO -> false
    Tri.UNKNOWN -> null
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    val power = context.getSystemService(PowerManager::class.java)
    if (power.isIgnoringBatteryOptimizations(context.packageName)) return
    try {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        )
    } catch (e: Exception) {
        DiagLog.w("UI", "battery optimization request failed", e)
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}
