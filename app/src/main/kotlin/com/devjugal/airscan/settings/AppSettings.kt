package com.devjugal.airscan.settings

import android.content.Context
import androidx.core.content.edit

class AppSettings(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = ThemeMode.parse(prefs.getString(KEY_THEME, null))
        set(value) = prefs.edit { putString(KEY_THEME, value.name) }

    var haptics: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit { putBoolean(KEY_HAPTICS, value) }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_HAPTICS = "haptics"
    }
}
