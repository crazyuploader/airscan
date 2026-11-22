package com.devjugal.airscan.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LoggingTest {
    private val fix = LocationFix(12.5, 77.25, altitudeM = 900.0, accuracyM = 4.5f)
    private val wifi = WifiSample("Home, 5G", "aa:bb:cc:dd:ee:ff", -55, 36, "5 GHz", 866)
    private val sim =
        SimSample(
            7,
            1,
            "Jio",
            "5G SA",
            rsrp = -114,
            rsrq = -13,
            sinr = 1,
            rssi = null,
            dbm = -114,
            band = "n78",
            pci = 358,
            tac = 68,
            cellId = 3374481457,
            channel = 632064,
        )
    private val sample = SignalSample(1_700_000_000_000, wifi, listOf(sim), fix)

    @Test
    fun csvFieldsWithSpecialCharactersAreQuoted() {
        assertEquals("plain", csvField("plain"))
        assertEquals("\"a,b\"", csvField("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", csvField("say \"hi\""))
        assertEquals("", csvField(null))
    }

    @Test
    fun everyCsvRowHasAsManyCellsAsTheHeader() {
        val columns = CSV_HEADER.split(",").size
        // Quoted commas would split wrongly, so count on a sample without them.
        val rows = csvRows(sample.copy(wifi = wifi.copy(ssid = "Home")))
        assertEquals(2, rows.size)
        rows.forEach { assertEquals(columns, it.split(",").size) }
    }

    @Test
    fun csvHasOneRowForWifiAndOnePerSim() {
        val rows = csvRows(sample)
        assertTrue(rows[0].contains(",wifi,\"Home, 5G\","))
        assertTrue(rows[1].contains(",sim2,Jio,5G SA,-114,-13,1,,-114,n78,358,68,3374481457,632064,"))
        assertTrue(rows.all { it.endsWith(",12.5,77.25,900.0,4.5") })
    }

    @Test
    fun csvLeavesPlaceCellsEmptyWithoutAFix() {
        val rows = csvRows(sample.copy(fix = null))
        assertTrue(rows.all { it.endsWith(",,,,") })
    }

    @Test
    fun gpxPointNeedsAFix() {
        assertNull(gpxPoint(sample.copy(fix = null)))
        val point = gpxPoint(sample)!!
        assertTrue(point.startsWith("<trkpt lat=\"12.5\" lon=\"77.25\">"))
        assertTrue(point.contains("<airscan:wifi_rssi>-55</airscan:wifi_rssi>"))
        assertTrue(point.contains("<airscan:sim2_rsrp>-114</airscan:sim2_rsrp>"))
        assertTrue(point.contains("<time>2023-11-14T22:13:20Z</time>"))
    }

    @Test
    fun gpxFileStaysCompleteAfterEveryAppend() {
        val file = File.createTempFile("airscan", ".gpx")
        try {
            file.delete()
            val gpx = GpxFile(file, "Walk <1> & more")
            assertTrue(file.readText().endsWith(GPX_FOOTER))
            assertTrue(file.readText().contains("<name>Walk &lt;1&gt; &amp; more</name>"))
            gpx.append(gpxPoint(sample)!!)
            gpx.append(gpxPoint(sample.copy(timeMs = sample.timeMs + 1000))!!)
            val text = file.readText()
            assertEquals(2, "<trkpt".toRegex().findAll(text).count())
            assertTrue(text.endsWith(GPX_FOOTER))
            assertEquals(1, "</gpx>".toRegex().findAll(text).count())
        } finally {
            file.delete()
        }
    }

    @Test
    fun gpxFileIsNotReplacedWhenItAlreadyHasContent() {
        val file = File.createTempFile("airscan", ".gpx")
        try {
            file.delete()
            GpxFile(file, "one").append(gpxPoint(sample)!!)
            GpxFile(file, "two").append(gpxPoint(sample)!!)
            assertEquals(2, "<trkpt".toRegex().findAll(file.readText()).count())
            assertTrue(file.readText().contains("<name>one</name>"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun valueForPicksTheRightSource() {
        assertEquals(-55, sample.valueFor(SignalSample.WIFI_SOURCE))
        assertEquals(-114, sample.valueFor(7))
        assertNull(sample.valueFor(99))
    }

    @Test
    fun statsIgnoreGaps() {
        assertNull(statsOf(listOf(null, null)))
        assertEquals(Stats(-90, -80, -70, 3), statsOf(listOf(-90, null, -80, -70)))
    }

    @Test
    fun logLabelsAreReadableAndOtherNamesStay() {
        assertEquals("2026-11-22 13:23:24 · GPX", logLabel("airscan-20261122-132324.gpx"))
        assertEquals("2026-11-22 13:23:24 · CSV", logLabel("airscan-20261122-132324.csv"))
        assertEquals("notes.txt", logLabel("notes.txt"))
    }
}
