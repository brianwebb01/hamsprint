package org.brianwebb.hamsprint.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.brianwebb.hamsprint.data.ThemeMode

/** Every color in the app comes from here; screens never hard-code a color. */
@Immutable
data class HamColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfacePressed: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val separator: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val success: Color,
    val successSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val warning: Color,
    val brandStart: Color,
    val brandEnd: Color,
    val figureBackground: Color,
    /** Ink drawn on top of [figureBackground]; figures stay light in both themes. */
    val onFigure: Color,
    val onFigureScrim: Color,
    val shadow: Color,
) {
    val brand: Brush get() = Brush.linearGradient(listOf(brandStart, brandEnd))
}

private val Light = HamColors(
    isDark = false,
    background = Color(0xFFF3F3F7),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    surfacePressed = Color(0xFFEDEDF2),
    textPrimary = Color(0xFF0C0C14),
    textSecondary = Color(0xFF64646F),
    textTertiary = Color(0xFF9C9CA6),
    separator = Color(0xFFE5E5EC),
    accent = Color(0xFF5546F0),
    accentSoft = Color(0xFFEEEDFF),
    onAccent = Color(0xFFFFFFFF),
    success = Color(0xFF14A85A),
    successSoft = Color(0xFFE2F6EA),
    danger = Color(0xFFE5383F),
    dangerSoft = Color(0xFFFDE7E8),
    warning = Color(0xFFE59A0B),
    brandStart = Color(0xFF6A4DFF),
    brandEnd = Color(0xFF2E9BFF),
    figureBackground = Color(0xFFFFFFFF),
    onFigure = Color(0xFF0C0C14),
    onFigureScrim = Color(0x14000000),
    shadow = Color(0x1A1B1B3A),
)

private val Dark = HamColors(
    isDark = true,
    background = Color(0xFF000000),
    surface = Color(0xFF131317),
    surfaceRaised = Color(0xFF1C1C22),
    surfacePressed = Color(0xFF26262E),
    textPrimary = Color(0xFFF5F5F8),
    textSecondary = Color(0xFF9D9DA8),
    textTertiary = Color(0xFF6A6A75),
    separator = Color(0xFF26262D),
    accent = Color(0xFF8A7DFF),
    accentSoft = Color(0xFF221E46),
    onAccent = Color(0xFFFFFFFF),
    success = Color(0xFF35D07F),
    successSoft = Color(0xFF0F2D1E),
    danger = Color(0xFFFF5D63),
    dangerSoft = Color(0xFF341517),
    warning = Color(0xFFFFB534),
    brandStart = Color(0xFF7A5CFF),
    brandEnd = Color(0xFF38A8FF),
    figureBackground = Color(0xFFF6F6F8),
    onFigure = Color(0xFF0C0C14),
    onFigureScrim = Color(0x14000000),
    shadow = Color(0x00000000),
)

/** Spacing and shape scale. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val gutter = 20.dp
}

object Radius {
    val sm = 12.dp
    val md = 18.dp
    val lg = 24.dp
    val xl = 32.dp
}

val LocalHamColors = staticCompositionLocalOf { Light }

object HamTheme {
    val colors: HamColors @Composable get() = LocalHamColors.current
}

@Composable
fun HamSprintTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent, background = colors.background,
            surface = colors.surface, onSurface = colors.textPrimary, onBackground = colors.textPrimary,
            surfaceVariant = colors.surfaceRaised, onSurfaceVariant = colors.textSecondary,
            outline = colors.separator, error = colors.danger,
            surfaceContainerHigh = colors.surfaceRaised, surfaceContainer = colors.surface,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent, background = colors.background,
            surface = colors.surface, onSurface = colors.textPrimary, onBackground = colors.textPrimary,
            surfaceVariant = colors.surfaceRaised, onSurfaceVariant = colors.textSecondary,
            outline = colors.separator, error = colors.danger,
            surfaceContainerHigh = colors.surfaceRaised, surfaceContainer = colors.surface,
        )
    }
    CompositionLocalProvider(LocalHamColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = HamTypography, content = content)
    }
}
