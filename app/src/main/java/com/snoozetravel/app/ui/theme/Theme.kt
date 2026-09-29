package com.snoozetravel.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snoozetravel.app.data.ThemeMode

// Paleta sobria: pizarra azulada + salvia, sin tonos fosforescentes.
private val LightColors = lightColorScheme(
    primary = Color(0xFF45626E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E3E8),
    onPrimaryContainer = Color(0xFF1B2F37),
    secondary = Color(0xFF66776C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE7DF),
    onSecondaryContainer = Color(0xFF223027),
    tertiary = Color(0xFF8A6F5A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF0E1D5),
    onTertiaryContainer = Color(0xFF33241A),
    background = Color(0xFFF6F7F6),
    onBackground = Color(0xFF1A1C1D),
    surface = Color(0xFFF6F7F6),
    onSurface = Color(0xFF1A1C1D),
    surfaceVariant = Color(0xFFE3E7E7),
    onSurfaceVariant = Color(0xFF5B6366),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F2F2),
    surfaceContainer = Color(0xFFEBEEEE),
    surfaceContainerHigh = Color(0xFFE5E9E9),
    surfaceContainerHighest = Color(0xFFDFE4E4),
    outline = Color(0xFFB9C1C3),
    outlineVariant = Color(0xFFD8DDDE),
    error = Color(0xFFA5524A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C4CF),
    onPrimary = Color(0xFF15282F),
    primaryContainer = Color(0xFF2F4751),
    onPrimaryContainer = Color(0xFFD5E3E8),
    secondary = Color(0xFFB4C4B9),
    onSecondary = Color(0xFF1F2B24),
    secondaryContainer = Color(0xFF34423A),
    onSecondaryContainer = Color(0xFFDDE7DF),
    tertiary = Color(0xFFD9C0AC),
    onTertiary = Color(0xFF3A2A1E),
    tertiaryContainer = Color(0xFF4A3A2E),
    onTertiaryContainer = Color(0xFFF0E1D5),
    background = Color(0xFF111416),
    onBackground = Color(0xFFE1E3E3),
    surface = Color(0xFF111416),
    onSurface = Color(0xFFE1E3E3),
    surfaceVariant = Color(0xFF2A3033),
    onSurfaceVariant = Color(0xFFB3BBBE),
    surfaceContainerLowest = Color(0xFF0C0F10),
    surfaceContainerLow = Color(0xFF171B1D),
    surfaceContainer = Color(0xFF1B2022),
    surfaceContainerHigh = Color(0xFF22282A),
    surfaceContainerHighest = Color(0xFF2A3033),
    outline = Color(0xFF4A5357),
    outlineVariant = Color(0xFF30373A),
    error = Color(0xFFE0A39C),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val base = Typography()
private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Normal),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
    labelLarge = base.labelLarge.copy(letterSpacing = 0.2.sp),
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Tema con transición suave de colores al cambiar entre claro y oscuro. */
@Composable
fun SnoozeTheme(dark: Boolean, content: @Composable () -> Unit) {
    val target = if (dark) DarkColors else LightColors
    MaterialTheme(
        colorScheme = animate(target),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

@Composable
private fun animate(t: ColorScheme): ColorScheme {
    @Composable
    fun a(c: Color): Color = animateColorAsState(c, tween(450), label = "theme").value
    return t.copy(
        primary = a(t.primary),
        onPrimary = a(t.onPrimary),
        primaryContainer = a(t.primaryContainer),
        onPrimaryContainer = a(t.onPrimaryContainer),
        secondaryContainer = a(t.secondaryContainer),
        onSecondaryContainer = a(t.onSecondaryContainer),
        tertiaryContainer = a(t.tertiaryContainer),
        onTertiaryContainer = a(t.onTertiaryContainer),
        background = a(t.background),
        onBackground = a(t.onBackground),
        surface = a(t.surface),
        onSurface = a(t.onSurface),
        onSurfaceVariant = a(t.onSurfaceVariant),
        surfaceContainerLow = a(t.surfaceContainerLow),
        surfaceContainer = a(t.surfaceContainer),
        surfaceContainerHigh = a(t.surfaceContainerHigh),
        outlineVariant = a(t.outlineVariant),
    )
}
