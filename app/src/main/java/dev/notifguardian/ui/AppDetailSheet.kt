package dev.notifguardian.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.notifguardian.ProtectedApps
import dev.notifguardian.TargetApp
import dev.notifguardian.log.DiagLog
import dev.notifguardian.status.AppStatus
import android.widget.Toast
import dev.notifguardian.xiaomi.MilletKeeper
import dev.notifguardian.xiaomi.XiaomiSettings
import dev.notifguardian.xiaomi.XiaomiSettings.Screen

data class AppStats(val posted: Int, val late: Int, val maxDelayMs: Long, val kills: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailSheet(status: AppStatus, stats: AppStats, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val t = status.target

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.surface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(t.pkg, 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.label, style = MaterialTheme.typography.titleLarge)
                    Text(t.pkg, style = MaterialTheme.typography.bodySmall, color = p.inkMuted)
                }
            }
            if (t.pkg == ProtectedApps.GMS) {
                Text(
                    "Delivers push notifications for most of your apps. If it's frozen or limited, every app is late.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.inkMuted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Text(
                statsLine(stats),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            HorizontalDivider(color = p.line)

            status.checks().forEach { check ->
                val viaHelper = t.pkg == ProtectedApps.GMS && check.kind == CheckKind.FREEZE
                CheckRow(check, if (viaHelper) "Re-add with Guardian Helper" else check.kind.fixLabel) {
                    if (viaHelper) reAddWithHelper(context) else fix(context, t, check.kind)
                }
                HorizontalDivider(color = p.line)
            }

            Text(
                "Also lock it in Recents: open Recents, long-press its card and tap the lock. " +
                    "Locked apps survive \"Clear all\".",
                style = MaterialTheme.typography.bodySmall,
                color = p.inkMuted,
                modifier = Modifier.padding(top = 16.dp),
            )

            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { XiaomiSettings.open(context, t.pkg, t.label, Screen.NOTIFICATIONS) }) {
                    Text("Notification settings")
                }
                OutlinedButton(onClick = { XiaomiSettings.open(context, t.pkg, t.label, Screen.APP_INFO) }) {
                    Text("App info")
                }
            }
            if (t.pkg != ProtectedApps.GMS) {
                TextButton(
                    onClick = {
                        ProtectedApps.remove(context, t.pkg)
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = p.broken),
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Stop protecting ${t.label}") }
            }
        }
    }
}

@Composable
private fun CheckRow(check: Check, fixLabel: String, onFix: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(stateColor(check.state, p)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(check.kind.text(check.state), style = MaterialTheme.typography.bodyLarge)
            if (check.state != CheckState.CLEAR) {
                Text(
                    check.kind.why,
                    style = MaterialTheme.typography.bodySmall,
                    color = p.inkMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(8.dp))
                if (check.state == CheckState.BROKEN) {
                    Button(onClick = onFix) { Text(fixLabel) }
                } else {
                    OutlinedButton(onClick = onFix) { Text("Check it myself") }
                }
            }
        }
    }
}

fun statsLine(s: AppStats): String = when {
    s.posted == 0 && s.kills == 0 -> "No notifications seen in the last 7 days."
    else -> buildString {
        append(plural(s.posted, "notification") + " in the last 7 days")
        append(if (s.late == 0) ", none late." else ", ${s.late} late (worst ${formatDelay(s.maxDelayMs)}).")
        if (s.kills > 0) append(" Stopped by the system ${plural(s.kills, "time")}.")
    }
}

private fun reAddWithHelper(context: Context) {
    if (!MilletKeeper.status(context).installed) {
        Toast.makeText(context, "Install Guardian Helper first. See \"Keep Google Play services awake\".", Toast.LENGTH_LONG).show()
        return
    }
    MilletKeeper.ping(context) {
        val result = MilletKeeper.status(context).lastResult.orEmpty()
        Toast.makeText(context, if (result.startsWith("ok")) "Google Play services re-added" else "Helper: $result", Toast.LENGTH_LONG).show()
    }
}

/** Runs the fix for a failing check: deep link into the right Xiaomi screen, or reopen the app. */
fun fix(context: Context, t: TargetApp, kind: CheckKind) {
    val screen = kind.fixScreen
    if (screen != null) {
        XiaomiSettings.open(context, t.pkg, t.label, screen)
        return
    }
    val launch = context.packageManager.getLaunchIntentForPackage(t.pkg)
    DiagLog.i("UI", "revive ${t.pkg} (launchable=${launch != null})")
    if (launch != null) context.startActivity(launch) else XiaomiSettings.open(context, t.pkg, t.label, Screen.APP_INFO)
}
