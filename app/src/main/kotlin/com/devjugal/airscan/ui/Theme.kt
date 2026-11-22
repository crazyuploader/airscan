package com.devjugal.airscan.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.devjugal.airscan.settings.ThemeMode

// minSdk is 31, so dynamic colour is always available.
@Composable
fun AirscanTheme(
    mode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = mode.isDark(isSystemInDarkTheme())
    val colors =
        when {
            !dark -> dynamicLightColorScheme(context)
            mode == ThemeMode.Black -> dynamicDarkColorScheme(context).toBlack()
            else -> dynamicDarkColorScheme(context)
        }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Pure black background, with cards only a little lighter so they stay visible. */
private fun ColorScheme.toBlack(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainer = Color(0xFF101010),
        surfaceContainerHigh = Color(0xFF181818),
        surfaceContainerHighest = Color(0xFF1E1E1E),
    )
