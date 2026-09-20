package com.lolo.changebox.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.lolo.changebox.R

// Outfit (la fuente de la web), empaquetada como fuente VARIABLE — un solo
// TTF cubre todos los pesos vía el eje wght. Sin Downloadable Fonts: la app
// es offline-first y no puede depender de una descarga en el primer render.

@OptIn(ExperimentalTextApi::class)
private fun outfit(weight: FontWeight) = Font(
    resId = R.font.outfit_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Outfit = FontFamily(
    outfit(FontWeight.Normal),
    outfit(FontWeight.Medium),
    outfit(FontWeight.SemiBold),
    outfit(FontWeight.Bold),
)

// Tipografía M3 por defecto re-vestida con Outfit.
private val defaults = Typography()

val ChangeboxTypography = Typography(
    displayLarge = defaults.displayLarge.copy(fontFamily = Outfit),
    displayMedium = defaults.displayMedium.copy(fontFamily = Outfit),
    displaySmall = defaults.displaySmall.copy(fontFamily = Outfit),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = Outfit),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = Outfit),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = Outfit),
    titleLarge = defaults.titleLarge.copy(fontFamily = Outfit),
    titleMedium = defaults.titleMedium.copy(fontFamily = Outfit),
    titleSmall = defaults.titleSmall.copy(fontFamily = Outfit),
    bodyLarge = defaults.bodyLarge.copy(fontFamily = Outfit),
    bodyMedium = defaults.bodyMedium.copy(fontFamily = Outfit),
    bodySmall = defaults.bodySmall.copy(fontFamily = Outfit),
    labelLarge = defaults.labelLarge.copy(fontFamily = Outfit),
    labelMedium = defaults.labelMedium.copy(fontFamily = Outfit),
    labelSmall = defaults.labelSmall.copy(fontFamily = Outfit),
)

