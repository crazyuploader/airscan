package com.devjugal.airscan.settings

/** Black is dark with pure black surfaces, which saves power and looks right on an AMOLED screen. */
enum class ThemeMode {
    System,
    Light,
    Dark,
    Black,
    ;

    fun isDark(systemDark: Boolean): Boolean =
        when (this) {
            System -> systemDark
            Light -> false
            Dark, Black -> true
        }

    companion object {
        /** An unknown or missing name falls back to following the system. */
        fun parse(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: System
    }
}
