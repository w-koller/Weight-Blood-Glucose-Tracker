package io.github.wkoller.glucosetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import io.github.wkoller.glucosetracker.data.prefs.Settings
import io.github.wkoller.glucosetracker.data.prefs.ThemeChoice

/** Colours for glucose status. Each status is always shown with an icon and a word as well. */
@Immutable
data class StatusColors(
    val low: Color,
    val onLow: Color,
    val inTarget: Color,
    val onInTarget: Color,
    val above: Color,
    val onAbove: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val highlight: Color,
    val onHighlight: Color,
)

/** Display choices from Settings › Display and accessibility, read by the shared components. */
@Immutable
data class UiPrefs(
    val showIcons: Boolean = true,
    val showHelperText: Boolean = true,
    val simpleMode: Boolean = false,
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    val readAloud: Boolean = false,
    val speechRate: Float = 0.9f,
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }
val LocalUiPrefs = staticCompositionLocalOf { UiPrefs() }

// Text on these backgrounds meets a contrast ratio of at least 7:1.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B4F8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6F7),
    onPrimaryContainer = Color(0xFF062B4D),
    secondary = Color(0xFF005048),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3EDE9),
    onSecondaryContainer = Color(0xFF00201C),
    background = Color(0xFFFFFCF5),
    onBackground = Color(0xFF15181C),
    surface = Color(0xFFFFFCF5),
    onSurface = Color(0xFF15181C),
    surfaceVariant = Color(0xFFEDEBE4),
    onSurfaceVariant = Color(0xFF33363A),
    surfaceContainer = Color(0xFFF4F1EA),
    surfaceContainerHigh = Color(0xFFEDEBE4),
    outline = Color(0xFF55595E),
    error = Color(0xFFA4161A),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8CFFF),
    onPrimary = Color(0xFF00213F),
    primaryContainer = Color(0xFF0B3A66),
    onPrimaryContainer = Color(0xFFE2EEFF),
    secondary = Color(0xFF8BD8CC),
    onSecondary = Color(0xFF00201C),
    secondaryContainer = Color(0xFF0F3D38),
    onSecondaryContainer = Color(0xFFD3EDE9),
    background = Color(0xFF111316),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF111316),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF26292D),
    onSurfaceVariant = Color(0xFFDADCE0),
    surfaceContainer = Color(0xFF1B1E22),
    surfaceContainerHigh = Color(0xFF26292D),
    outline = Color(0xFFB8BCC2),
    error = Color(0xFFFFB3AE),
    onError = Color(0xFF410002),
)

private val MaxContrastColors = darkColorScheme(
    primary = Color(0xFFFFE600),
    onPrimary = Color.Black,
    // Containers stay dark so yellow buttons inside them remain visible; borders carry the colour.
    primaryContainer = Color(0xFF1A1A1A),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF7DF9FF),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1A1A1A),
    onSecondaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color.White,
    surfaceContainer = Color(0xFF0D0D0D),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    outline = Color.White,
    error = Color(0xFFFF8A80),
    onError = Color.Black,
)

private val LightStatus = StatusColors(
    low = Color(0xFFA4161A), onLow = Color.White,
    inTarget = Color(0xFF0B57D0), onInTarget = Color.White,
    above = Color(0xFF8A4300), onAbove = Color.White,
    warningContainer = Color(0xFFFFE8C7), onWarningContainer = Color(0xFF3D1E00),
    dangerContainer = Color(0xFFFFDAD6), onDangerContainer = Color(0xFF410002),
    infoContainer = Color(0xFFD6E6F7), onInfoContainer = Color(0xFF062B4D),
    highlight = Color(0xFFFFE08A), onHighlight = Color(0xFF15181C),
)

private val DarkStatus = StatusColors(
    low = Color(0xFFFFB3AE), onLow = Color(0xFF410002),
    inTarget = Color(0xFFA8CFFF), onInTarget = Color(0xFF00213F),
    above = Color(0xFFFFB870), onAbove = Color(0xFF2E1500),
    warningContainer = Color(0xFF4A2A00), onWarningContainer = Color(0xFFFFE8C7),
    dangerContainer = Color(0xFF5C1210), onDangerContainer = Color(0xFFFFDAD6),
    infoContainer = Color(0xFF0B3A66), onInfoContainer = Color(0xFFE2EEFF),
    highlight = Color(0xFF5A4A00), onHighlight = Color(0xFFFFF4C2),
)

private val MaxContrastStatus = StatusColors(
    low = Color(0xFFFF8A80), onLow = Color.Black,
    inTarget = Color(0xFF7DF9FF), onInTarget = Color.Black,
    above = Color(0xFFFFB870), onAbove = Color.Black,
    warningContainer = Color(0xFFFFB870), onWarningContainer = Color.Black,
    dangerContainer = Color(0xFFFF8A80), onDangerContainer = Color.Black,
    infoContainer = Color(0xFF7DF9FF), onInfoContainer = Color.Black,
    highlight = Color(0xFFFFE600), onHighlight = Color.Black,
)

// Sizes are larger than Material defaults: body text starts at 20sp before the text-size setting is applied.
private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 72.sp, lineHeight = 80.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 52.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 25.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 21.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontSize = 19.sp, lineHeight = 27.sp),
    bodySmall = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
)

/** The combined text scale never goes beyond this, so layouts stay usable at the phone's largest font. */
private const val MAX_FONT_SCALE = 2.0f

@Composable
fun TrackerTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    val colors: ColorScheme
    val status: StatusColors
    when {
        settings.maxContrast -> { colors = MaxContrastColors; status = MaxContrastStatus }
        dark -> { colors = DarkColors; status = DarkStatus }
        else -> { colors = LightColors; status = LightStatus }
    }
    val density = LocalDensity.current
    val scaled = Density(density.density, (density.fontScale * settings.textSize.scale).coerceAtMost(MAX_FONT_SCALE))
    val prefs = UiPrefs(
        showIcons = settings.showIcons,
        showHelperText = settings.showHelperText,
        simpleMode = settings.simpleMode,
        reduceMotion = settings.reduceMotion,
        haptics = settings.haptics,
        readAloud = settings.readAloud,
        speechRate = settings.speechRate,
    )
    MaterialTheme(colorScheme = colors, typography = AppTypography) {
        CompositionLocalProvider(
            LocalDensity provides scaled,
            LocalStatusColors provides status,
            LocalUiPrefs provides prefs,
            content = content,
        )
    }
}
