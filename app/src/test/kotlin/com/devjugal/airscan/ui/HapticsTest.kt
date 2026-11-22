package com.devjugal.airscan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HapticsTest {
    @Test
    fun noTapWithoutAnEarlierLevelOrChange() {
        assertNull(hapticFor(null, Quality.Good))
        assertNull(hapticFor(Quality.Good, Quality.Good))
    }

    @Test
    fun betterAndWorseHaveDifferentTaps() {
        assertEquals(HapticKind.Better, hapticFor(Quality.Fair, Quality.Good))
        assertEquals(HapticKind.Worse, hapticFor(Quality.Good, Quality.Poor))
    }

    @Test
    fun aValueNearAnEdgeKeepsItsLevel() {
        // 0.41 is Fair by the plain scale, but a value that was Poor stays Poor until it is clearly past 0.4.
        assertEquals(Quality.Fair, qualityOf(0.41f))
        assertEquals(Quality.Poor, stableQuality(Quality.Poor, 0.41f))
        assertEquals(Quality.Fair, stableQuality(Quality.Poor, 0.45f))
    }

    @Test
    fun aValueNearAnEdgeFromAboveAlsoKeepsItsLevel() {
        assertEquals(Quality.Poor, qualityOf(0.39f))
        assertEquals(Quality.Fair, stableQuality(Quality.Fair, 0.39f))
        assertEquals(Quality.Poor, stableQuality(Quality.Fair, 0.30f))
    }

    @Test
    fun firstValueUsesThePlainScale() {
        assertEquals(Quality.Excellent, stableQuality(null, 0.95f))
        assertEquals(Quality.VeryPoor, stableQuality(null, 0f))
    }

    @Test
    fun theBestLevelHoldsAtTheTopOfTheRange() {
        assertEquals(Quality.Excellent, stableQuality(Quality.Excellent, 1f))
    }
}
