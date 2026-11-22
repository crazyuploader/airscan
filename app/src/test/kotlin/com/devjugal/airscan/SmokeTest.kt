package com.devjugal.airscan

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun applicationIdMatchesNamespace() {
        assertEquals("com.devjugal.airscan", MainActivity::class.java.packageName)
    }
}
