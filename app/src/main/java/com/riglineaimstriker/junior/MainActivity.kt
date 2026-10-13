package com.riglineaimstriker.junior

import android.os.Bundle
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riglineaimstriker.junior.model.AppPrefs
import com.riglineaimstriker.junior.model.ThemeMode
import com.riglineaimstriker.junior.ui.AppNav
import com.riglineaimstriker.junior.ui.theme.JuniorTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var prefs: AppPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by prefs.theme.collectAsStateWithLifecycle()
            val dark = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Иконки системных панелей следуют теме, выбранной в Junior, а не системной:
            // иначе при светлой теме приложения на тёмном телефоне иконки станут белыми на светлом фоне.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(DARK_SCRIM)
                    else SystemBarStyle.light(LIGHT_SCRIM, DARK_SCRIM),
                )
                onDispose { }
            }
            JuniorTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNav()
                }
            }
        }
    }
}

/** Полупрозрачные подложки навигационной панели (как в androidx.activity по умолчанию). */
private const val LIGHT_SCRIM = 0xE6FFFFFF.toInt()
private const val DARK_SCRIM = 0x801B1B1B.toInt()
