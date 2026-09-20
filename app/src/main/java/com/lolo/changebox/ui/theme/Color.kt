package com.lolo.changebox.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Paleta de Changebox, portada 1:1 de los tokens @theme de la web
// (fantastic-eureka/src/app/globals.css): petróleo/esmeralda + acento dorado.

val Brand = Color(0xFF0C6B70) // --color-brand
val BrandMid = Color(0xFF128F89) // --color-brand-mid
val BrandLight = Color(0xFF27B3A4) // --color-brand-light
val BrandSoft = Color(0xFF82D7C9) // --color-brand-soft
val Navy = Color(0xFF07272E) // --color-navy
val Chip = Color(0xFFDCF0EC) // --color-chip
val AppBg = Color(0xFFEEF4F2) // --color-app
val PageBg = Color(0xFFC3D6D3) // --color-page
val Ok = Color(0xFF1D8A4E) // --color-ok
val Warn = Color(0xFFB07B2E) // --color-warn
val Danger = Color(0xFFC04B5D) // --color-danger
val Gold = Color(0xFFD9A13B) // --color-gold

// Variantes solo-Android para el tema oscuro (la web no tiene modo oscuro;
// se derivan de la misma paleta manteniendo el contraste AA).
val NavySurface = Color(0xFF0C333B)
val NavySurfaceHigh = Color(0xFF11404A)
val DangerSoft = Color(0xFFE08794)
val WarnSoft = Color(0xFFD9A45E)
val OkSoft = Color(0xFF5CBF8B)

/**
 * Colores semánticos que no caben en los slots de Material 3 (ok/warn/gold y
 * tintes de ingreso/gasto). Se consumen vía [LocalExtendedColors].
 */
@Immutable
data class ExtendedColors(
    val ok: Color,
    val warn: Color,
    val gold: Color,
    val income: Color,
    val expense: Color,
    val chip: Color,
)

val LightExtendedColors = ExtendedColors(
    ok = Ok,
    warn = Warn,
    gold = Gold,
    income = Ok,
    expense = Danger,
    chip = Chip,
)

val DarkExtendedColors = ExtendedColors(
    ok = OkSoft,
    warn = WarnSoft,
    gold = Gold,
    income = OkSoft,
    expense = DangerSoft,
    chip = NavySurfaceHigh,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

