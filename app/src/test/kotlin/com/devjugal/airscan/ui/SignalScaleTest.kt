package com.devjugal.airscan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SignalScaleTest {
    @Test
    fun fractionIsClampedToZeroAndOne() {
        assertEquals(0f, fractionOf(-120, SignalKind.WifiRssi), 0.001f)
        assertEquals(1f, fractionOf(-10, SignalKind.WifiRssi), 0.001f)
        assertEquals(0.5f, fractionOf(-60, SignalKind.WifiRssi), 0.001f)
    }

    @Test
    fun rsrpUsesItsOwnRange() {
        assertEquals(0f, fractionOf(-140, SignalKind.Rsrp), 0.001f)
        assertEquals(1f, fractionOf(-70, SignalKind.Rsrp), 0.001f)
        assertEquals(0.5f, fractionOf(-105, SignalKind.Rsrp), 0.001f)
    }

    @Test
    fun qualityWordsFollowTheFraction() {
        assertEquals(Quality.VeryPoor, qualityOf(0.1f))
        assertEquals(Quality.Poor, qualityOf(0.3f))
        assertEquals(Quality.Fair, qualityOf(0.5f))
        assertEquals(Quality.Good, qualityOf(0.7f))
        assertEquals(Quality.Excellent, qualityOf(0.95f))
    }

    @Test
    fun barsRunFromZeroToFour() {
        assertEquals(0, barsOf(0f))
        assertEquals(2, barsOf(0.5f))
        assertEquals(4, barsOf(1f))
    }

    @Test
    fun colourDiffersAtTheTwoEndsOfTheScale() {
        assertNotEquals(signalColor(0f), signalColor(1f))
    }
}
