package com.devjugal.airscan.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun systemFollowsTheSystem() {
        assertTrue(ThemeMode.System.isDark(systemDark = true))
        assertFalse(ThemeMode.System.isDark(systemDark = false))
    }

    @Test
    fun lightIsNeverDarkAndDarkAndBlackAlwaysAre() {
        assertFalse(ThemeMode.Light.isDark(systemDark = true))
        assertTrue(ThemeMode.Dark.isDark(systemDark = false))
        assertTrue(ThemeMode.Black.isDark(systemDark = false))
    }

    @Test
    fun parseReadsNamesAndFallsBackToSystem() {
        assertEquals(ThemeMode.Black, ThemeMode.parse("Black"))
        assertEquals(ThemeMode.Light, ThemeMode.parse("Light"))
        assertEquals(ThemeMode.System, ThemeMode.parse(null))
        assertEquals(ThemeMode.System, ThemeMode.parse("Purple"))
    }
}
