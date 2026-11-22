package com.devjugal.airscan.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {
    @Test
    fun portraitPhoneIsNotWide() {
        assertFalse(isWide(widthDp = 411f, heightDp = 890f))
    }

    @Test
    fun landscapePhoneIsWide() {
        assertTrue(isWide(widthDp = 890f, heightDp = 411f))
    }

    @Test
    fun portraitTabletIsWide() {
        assertTrue(isWide(widthDp = 800f, heightDp = 1280f))
    }

    @Test
    fun squareWindowBelowTheLimitIsNotWide() {
        assertFalse(isWide(widthDp = 500f, heightDp = 500f))
    }
}
