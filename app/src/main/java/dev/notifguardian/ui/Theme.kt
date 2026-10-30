package dev.notifguardian.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * "Signal" palette: deep navy ink, cool paper, and three states for a delivery path:
 * clear (teal), unknown (amber), broken (red).
 */
@Immutable
data class Palette(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val inkMuted: Color,
    val line: Color,
    val clear: Color,
    val unknown: Color,
    val broken: Color,
    val heroBackground: Color,
    val heroText: Color,
    val heroMuted: Color,
)

private val Light = Palette(
    background = Color(0xFFEEF2F6),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF14213D),
    inkMuted = Color(0xFF5B6B82),
    line = Color(0xFFD5DEE8),
    clear = Color(0xFF1F8A70),
    unknown = Color(0xFFB7791F),
    broken = Color(0xFFC0392B),
    heroBackground = Color(0xFF14213D),
    heroText = Color(0xFFF3F6FA),
    heroMuted = Color(0xFFA9B6C8),
)

private val Dark = Palette(
    background = Color(0xFF0D1522),
    surface = Color(0xFF152033),
    ink = Color(0xFFE6EDF5),
    inkMuted = Color(0xFF93A3B8),
    line = Color(0xFF26354D),
    clear = Color(0xFF3FBF9A),
    unknown = Color(0xFFE0A84A),
    broken = Color(0xFFEF6B5B),
    heroBackground = Color(0xFF1B2A44),
    heroText = Color(0xFFF3F6FA),
    heroMuted = Color(0xFFA9B6C8),
)

val LocalPalette = staticCompositionLocalOf { Light }

private val GuardianType = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun GuardianTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.clear, onPrimary = Color.White, background = p.background, onBackground = p.ink,
            surface = p.surface, onSurface = p.ink, onSurfaceVariant = p.inkMuted, outline = p.line,
            surfaceContainerLow = p.surface, surfaceContainerHigh = p.surface, error = p.broken,
        )
    } else {
        lightColorScheme(
            primary = p.ink, onPrimary = Color.White, background = p.background, onBackground = p.ink,
            surface = p.surface, onSurface = p.ink, onSurfaceVariant = p.inkMuted, outline = p.line,
            surfaceContainerLow = p.surface, surfaceContainerHigh = p.surface, error = p.broken,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = GuardianType) {
            // Sets the default text color; without it Text falls back to black in dark mode
            Surface(color = p.background, contentColor = p.ink, content = content)
        }
    }
}
