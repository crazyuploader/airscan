package com.devjugal.airscan

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.devjugal.airscan.settings.AppSettings
import com.devjugal.airscan.settings.ThemeMode
import com.devjugal.airscan.ui.AirscanApp
import com.devjugal.airscan.ui.AirscanTheme
import com.devjugal.airscan.ui.LocalHapticsEnabled

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The bottom bar draws its own colour, so Android must not add a grey scrim behind it.
        window.isNavigationBarContrastEnforced = false
        val settings = AppSettings(this)
        setContent {
            var mode by remember { mutableStateOf(settings.themeMode) }
            var haptics by remember { mutableStateOf(settings.haptics) }
            val dark = mode.isDark(isSystemInDarkTheme())
            // The bar icons follow the chosen theme, not only the system theme.
            DisposableEffect(dark, mode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle =
                        SystemBarStyle.auto(
                            LIGHT_SCRIM,
                            if (mode ==
                                ThemeMode.Black
                            ) {
                                Color.BLACK
                            } else {
                                DARK_SCRIM
                            },
                        ) { dark },
                )
                onDispose {}
            }
            AirscanTheme(mode) {
                CompositionLocalProvider(LocalHapticsEnabled provides haptics) {
                    AirscanApp(
                        themeMode = mode,
                        onThemeMode = {
                            mode = it
                            settings.themeMode = it
                        },
                        hapticsEnabled = haptics,
                        onHaptics = {
                            haptics = it
                            settings.haptics = it
                        },
                    )
                }
            }
        }
    }

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
