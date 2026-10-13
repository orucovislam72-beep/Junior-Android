package com.riglineaimstriker.junior.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.riglineaimstriker.junior.R

/** Manrope (OFL 1.1), статические начертания 400/500/600 из вариативного оригинала. */
val JuniorUiFont: FontFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
)

/** JetBrains Mono (OFL 1.1), Regular 400. */
val JuniorCodeFont: FontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
)

private fun ui(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = JuniorUiFont, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

/** Стиль кода: 13/20 sp. */
val JuniorCodeStyle: TextStyle =
    TextStyle(fontFamily = JuniorCodeFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp)

// headline 28/34, title 22/28, section 18/24, body 16/24, secondary 14/20, caption 12/18.
internal val JuniorTypography = Typography(
    displayLarge = ui(28, 34, FontWeight.SemiBold),
    displayMedium = ui(28, 34, FontWeight.SemiBold),
    displaySmall = ui(28, 34, FontWeight.Medium),
    headlineLarge = ui(28, 34, FontWeight.SemiBold),
    headlineMedium = ui(28, 34, FontWeight.Medium),
    headlineSmall = ui(22, 28, FontWeight.Medium),
    titleLarge = ui(22, 28, FontWeight.Medium),
    titleMedium = ui(18, 24, FontWeight.Medium),
    titleSmall = ui(16, 24, FontWeight.Medium),
    bodyLarge = ui(16, 24, FontWeight.Normal),
    bodyMedium = ui(14, 20, FontWeight.Normal),
    bodySmall = ui(12, 18, FontWeight.Normal),
    labelLarge = ui(14, 20, FontWeight.Medium),
    labelMedium = ui(12, 18, FontWeight.Medium),
    labelSmall = ui(12, 18, FontWeight.Normal),
)
