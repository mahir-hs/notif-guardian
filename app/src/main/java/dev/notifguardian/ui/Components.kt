package dev.notifguardian.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

@Composable
fun AppIcon(pkg: String, size: Dp = 40.dp) {
    val context = LocalContext.current
    // Keyed on pkg: rows get reused for other apps when the list changes, so the state must reset
    val icon by key(pkg) {
        produceState(iconCache[pkg]) {
            if (value == null) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        context.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap()
                    }.getOrNull()?.also { iconCache[pkg] = it }
                }
            }
        }
    }
    val current = icon
    if (current != null) {
        Image(current, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(LocalPalette.current.line))
    }
}

/** An app's delivery path at a glance: one segment per check. */
@Composable
fun SignalStrip(checks: List<Check>, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val broken = checks.count { it.state == CheckState.BROKEN }
    Row(
        modifier.semantics {
            contentDescription = if (broken == 0) "All checks clear" else "$broken checks failing"
        },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        checks.forEach {
            Box(
                Modifier
                    .size(width = 12.dp, height = 18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(stateColor(it.state, p)),
            )
        }
    }
}

fun stateColor(state: CheckState, p: Palette): Color = when (state) {
    CheckState.CLEAR -> p.clear
    CheckState.BROKEN -> p.broken
    CheckState.UNKNOWN -> p.unknown
}

fun plural(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

fun formatDelay(ms: Long): String = when {
    ms < 60_000 -> "${ms / 1000} s"
    ms < 3_600_000 -> "${ms / 60_000} min"
    else -> "${ms / 3_600_000} h ${(ms % 3_600_000) / 60_000} min"
}
