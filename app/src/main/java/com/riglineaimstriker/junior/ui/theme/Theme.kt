package com.riglineaimstriker.junior.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Светлый акцент. Сохранён для совместимости; в UI используйте MaterialTheme.colorScheme.primary. */
val Accent: Color = LightPrimary

@Immutable
data class JuniorExtraColors(
    val codeBackground: Color,
    val codeBorder: Color,
    val success: Color,
    val onSuccessContainer: Color,
    val successContainer: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
)

private val LightExtra = JuniorExtraColors(
    codeBackground = LightSoft, codeBorder = LightDivider,
    success = LightSuccess, onSuccessContainer = LightSuccess, successContainer = LightSuccessSoft,
    errorContainer = LightErrorSoft, onErrorContainer = LightError,
)

private val DarkExtra = JuniorExtraColors(
    codeBackground = DarkSoft, codeBorder = DarkDivider,
    success = DarkSuccess, onSuccessContainer = DarkSuccess, successContainer = DarkSuccessSoft,
    errorContainer = DarkErrorSoft, onErrorContainer = DarkError,
)

val LocalJuniorColors: ProvidableCompositionLocal<JuniorExtraColors> = staticCompositionLocalOf { LightExtra }

/** Отступы 4/8/12/16/24/32 dp. */
object JuniorSpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

private val LightScheme: ColorScheme = lightColorScheme(
    primary = LightPrimary, onPrimary = LightOnPrimary,
    primaryContainer = LightPrimarySoft, onPrimaryContainer = LightText,
    inversePrimary = DarkPrimary,
    secondary = LightPrimary, onSecondary = LightOnPrimary,
    secondaryContainer = LightSoft, onSecondaryContainer = LightText,
    tertiary = LightPrimary, onTertiary = LightOnPrimary,
    tertiaryContainer = LightSoft, onTertiaryContainer = LightText,
    background = LightBackground, onBackground = LightText,
    surface = LightBackground, onSurface = LightText,
    surfaceVariant = LightSoft, onSurfaceVariant = LightTextSecondary,
    surfaceTint = Color.Transparent,
    inverseSurface = DarkSurface, inverseOnSurface = DarkText,
    error = LightError, onError = LightOnPrimary,
    errorContainer = LightErrorSoft, onErrorContainer = LightError,
    outline = LightOutline, outlineVariant = LightDivider,
    scrim = Color.Black,
    surfaceBright = LightSurface, surfaceDim = LightSoft,
    surfaceContainerLowest = LightSurface, surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface, surfaceContainerHigh = LightSoft,
    surfaceContainerHighest = LightSoft,
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = DarkPrimary, onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimarySoft, onPrimaryContainer = DarkText,
    inversePrimary = LightPrimary,
    secondary = DarkPrimary, onSecondary = DarkOnPrimary,
    secondaryContainer = DarkSoft, onSecondaryContainer = DarkText,
    tertiary = DarkPrimary, onTertiary = DarkOnPrimary,
    tertiaryContainer = DarkSoft, onTertiaryContainer = DarkText,
    background = DarkBackground, onBackground = DarkText,
    surface = DarkBackground, onSurface = DarkText,
    surfaceVariant = DarkSoft, onSurfaceVariant = DarkTextSecondary,
    surfaceTint = Color.Transparent,
    inverseSurface = LightSurface, inverseOnSurface = LightText,
    error = DarkError, onError = DarkOnPrimary,
    errorContainer = DarkErrorSoft, onErrorContainer = DarkError,
    outline = DarkOutline, outlineVariant = DarkDivider,
    scrim = Color.Black,
    surfaceBright = DarkSoft, surfaceDim = DarkBackground,
    surfaceContainerLowest = DarkBackground, surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface, surfaceContainerHigh = DarkSoft,
    surfaceContainerHighest = DarkSoft,
)

private val JuniorShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun JuniorTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalJuniorColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = JuniorTypography,
            shapes = JuniorShapes,
            content = content,
        )
    }
}
