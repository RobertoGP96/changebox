package com.lolo.changebox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Identidad de Changebox: SIN dynamic color a propósito — la marca (petróleo/
// esmeralda + dorado) debe verse igual en todos los dispositivos, como en
// la web. Los colores semánticos extra van en LocalExtendedColors.

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Chip,
    onPrimaryContainer = Navy,
    secondary = BrandMid,
    onSecondary = Color.White,
    secondaryContainer = Chip,
    onSecondaryContainer = Navy,
    tertiary = Gold,
    onTertiary = Navy,
    tertiaryContainer = Color(0xFFF4E3C4),
    onTertiaryContainer = Color(0xFF4A3812),
    background = AppBg,
    onBackground = Navy,
    surface = Color.White,
    onSurface = Navy,
    surfaceVariant = Chip,
    onSurfaceVariant = Color(0xFF3F5B57),
    outline = Color(0xFF6F8A86),
    outlineVariant = PageBg,
    error = Danger,
    onError = Color.White,
    errorContainer = Color(0xFFF7DCE0),
    onErrorContainer = Color(0xFF5C1220),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6FAF8),
    surfaceContainer = AppBg,
    surfaceContainerHigh = Color(0xFFE4EEEB),
    surfaceContainerHighest = Chip,
)

private val DarkColors = darkColorScheme(
    primary = BrandLight,
    onPrimary = Navy,
    primaryContainer = Brand,
    onPrimaryContainer = Chip,
    secondary = BrandSoft,
    onSecondary = Navy,
    secondaryContainer = NavySurfaceHigh,
    onSecondaryContainer = BrandSoft,
    tertiary = Gold,
    onTertiary = Navy,
    tertiaryContainer = Color(0xFF5C4718),
    onTertiaryContainer = Color(0xFFF4E3C4),
    background = Navy,
    onBackground = Color(0xFFE2EFEC),
    surface = NavySurface,
    onSurface = Color(0xFFE2EFEC),
    surfaceVariant = NavySurfaceHigh,
    onSurfaceVariant = Color(0xFFA9C5C0),
    outline = Color(0xFF6F8A86),
    outlineVariant = Color(0xFF2A4A50),
    error = DangerSoft,
    onError = Color(0xFF43111C),
    errorContainer = Color(0xFF6E2231),
    onErrorContainer = Color(0xFFF7DCE0),
    surfaceContainerLowest = Navy,
    surfaceContainerLow = Color(0xFF0A2E36),
    surfaceContainer = NavySurface,
    surfaceContainerHigh = NavySurfaceHigh,
    surfaceContainerHighest = Color(0xFF16505C),
)

@Composable
fun ChangeboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extended = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ChangeboxTypography,
            content = content,
        )
    }
}

/** Acceso corto a los colores semánticos extra dentro de un composable. */
object ChangeboxColors {
    val extended: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}

