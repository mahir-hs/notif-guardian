package dev.notifguardian.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.notifguardian.ProtectedApps
import dev.notifguardian.TargetApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val selected by ProtectedApps.packages.collectAsState()
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<TargetApp>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != context.packageName }
                .map { TargetApp(it, ProtectedApps.label(context, it)) }
                .sortedBy { it.label.lowercase() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // The list does all the scrolling; letting the sheet take leftover drags made it bounce
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = p.surface,
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Choose apps to protect", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            Text(
                "Guardian watches these apps and tells you when something stops their notifications.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.inkMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            val all = apps
            if (all == null) {
                Text("Loading apps…", color = p.inkMuted, modifier = Modifier.padding(vertical = 24.dp))
                return@Column
            }
            val filtered = all.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            val (messaging, others) = filtered.partition { it.pkg in ProtectedApps.SUGGESTED }

            LazyColumn(
                Modifier.weight(1f).padding(top = 8.dp),
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            ) {
                if (messaging.isNotEmpty()) {
                    item { PickerHeader("Messaging apps") }
                    items(messaging, key = { "m-" + it.pkg }) { PickerRow(it, it.pkg in selected) }
                }
                if (others.isNotEmpty()) {
                    item { PickerHeader("All apps") }
                    items(others, key = { it.pkg }) { PickerRow(it, it.pkg in selected) }
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "No installed app matches \"$query\".",
                            color = p.inkMuted,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = LocalPalette.current.inkMuted,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun PickerRow(app: TargetApp, checked: Boolean) {
    val context = LocalContext.current
    val toggle = {
        if (checked) ProtectedApps.remove(context, app.pkg) else ProtectedApps.add(context, app.pkg)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClick = toggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.pkg, 36.dp)
        Spacer(Modifier.width(14.dp))
        Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = null)
    }
}
