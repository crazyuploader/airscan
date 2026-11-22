package com.devjugal.airscan.history

import org.junit.Assert.assertEquals
import org.junit.Test

class RingSeriesTest {
    @Test
    fun keepsOnlyTheNewestSamples() {
        val series = RingSeries<Int>(3)
        (1..5).forEach { series.add(it) }
        assertEquals(listOf(3, 4, 5), series.snapshot())
        assertEquals(3, series.size)
    }

    @Test
    fun gapsStayInPlace() {
        val series = RingSeries<Int>(4)
        series.add(1)
        series.add(null)
        series.add(3)
        assertEquals(listOf(1, null, 3), series.snapshot())
        assertEquals(listOf(null, 3), series.last(2))
    }

    @Test
    fun clearEmptiesTheSeries() {
        val series = RingSeries<Int>(2)
        series.add(1)
        series.clear()
        assertEquals(emptyList<Int?>(), series.snapshot())
    }
}
