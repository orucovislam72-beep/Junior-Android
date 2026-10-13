package com.riglineaimstriker.junior.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

val Accent = Color(0xFF7C6CFF)

private val Dark = darkColorScheme(
    primary = Accent, onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2750), onPrimaryContainer = Color(0xFFE8EAED),
    background = Color(0xFF0F1115), onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF0F1115), onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF171A21), onSurfaceVariant = Color(0xFF9AA0AB),
    outlineVariant = Color(0xFF262A33), error = Color(0xFFFF6B6B),
)

private val Light = lightColorScheme(
    primary = Accent, onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E1FF), onPrimaryContainer = Color(0xFF1B1D21),
    background = Color(0xFFFAFAF8), onBackground = Color(0xFF1B1D21),
    surface = Color(0xFFFAFAF8), onSurface = Color(0xFF1B1D21),
    surfaceVariant = Color(0xFFF1F0EC), onSurfaceVariant = Color(0xFF6B7078),
    outlineVariant = Color(0xFFE2E0DA), error = Color(0xFFD93025),
)

private val JuniorTypography = Typography(
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
)

@Composable
fun JuniorTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = JuniorTypography,
        content = content,
    )
}
