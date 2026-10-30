package dev.notifguardian.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.notifguardian.log.DiagLog
import dev.notifguardian.support.Support
import dev.notifguardian.support.SupportInfo
import dev.notifguardian.support.SupportMethod
import kotlinx.coroutines.launch

/** Donation options, loaded from the website only when the user asks for them. */
@Composable
fun SupportSection() {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<SupportInfo?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    if (Support.url == null) return

    Column {
        Text("Support development", style = MaterialTheme.typography.titleLarge)
        Text(
            info?.message?.takeIf { it.isNotBlank() }
                ?: "Notif Guardian is free and open source. Donations help keep it going.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.inkMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        val loaded = info
        when {
            loaded == null -> {
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = p.broken, modifier = Modifier.padding(bottom = 8.dp))
                }
                OutlinedButton(
                    enabled = !loading,
                    onClick = {
                        loading = true
                        error = null
                        scope.launch {
                            try {
                                info = Support.fetch()
                            } catch (e: Exception) {
                                DiagLog.w("Support", "couldn't load support info", e)
                                error = "Couldn't load donation options. Check your connection and try again."
                            } finally {
                                loading = false
                            }
                        }
                    },
                ) { Text(if (loading) "Loading…" else "Show ways to donate") }
            }
            loaded.methods.isEmpty() -> Text(
                "Donation options are coming soon. Starring the project on GitHub helps too.",
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> {
                // One group per country, in the order the methods are listed
                loaded.methods.groupBy { it.region }.forEach { (code, methods) ->
                    val region = code?.let { loaded.regions[it] }
                    if (region != null) {
                        Text(
                            "${region.flag}  In ${region.name}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        region.about?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = p.inkMuted)
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)
                            .clip(RoundedCornerShape(20.dp)).background(p.surface),
                    ) {
                        methods.forEachIndexed { i, m ->
                            if (i > 0) HorizontalDivider(color = p.line, modifier = Modifier.padding(start = 16.dp))
                            MethodRow(m)
                        }
                    }
                }
                loaded.elsewhere?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = p.inkMuted)
                }
            }
        }
    }
}

@Composable
private fun MethodRow(m: SupportMethod) {
    val context = LocalContext.current
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(m.name, style = MaterialTheme.typography.titleMedium)
            m.value?.let { Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) }
            m.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = p.inkMuted) }
        }
        Spacer(Modifier.width(8.dp))
        val value = m.value
        val url = m.url
        when {
            url != null -> OutlinedButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }) { Text("Open") }
            value != null -> OutlinedButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText(m.name, value))
                Toast.makeText(context, "${m.name} number copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy") }
        }
    }
}
