package com.devjugal.airscan.radio.wifi

import android.net.wifi.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiModelsTest {
    private fun ap(
        channel: Int,
        band: WifiBand = WifiBand.Ghz24,
        width: Int = 20,
        center: Int = frequencyOf(band, channel),
        rssi: Int = -60,
    ) = AccessPoint(
        ssid = "n$channel",
        bssid = "00:00:00:00:00:%02x".format(channel),
        rssi = rssi,
        frequencyMhz = frequencyOf(band, channel),
        centerMhz = center,
        widthMhz = width,
        standard = WifiStandard.Wifi5,
        security = Security.Wpa2,
        ageMs = 0,
    )

    @Test
    fun channelsFromFrequencies() {
        assertEquals(1, channelOf(2412))
        assertEquals(13, channelOf(2472))
        assertEquals(14, channelOf(2484))
        assertEquals(36, channelOf(5180))
        assertEquals(149, channelOf(5745))
        assertEquals(1, channelOf(5955))
        assertEquals(93, channelOf(6415))
        assertNull(channelOf(900))
    }

    @Test
    fun frequenciesFromChannelsRoundTrip() {
        for (band in WifiBand.entries) {
            for (channel in channelsOf(band)) {
                assertEquals("$band $channel", channel, channelOf(frequencyOf(band, channel)))
            }
        }
    }

    @Test
    fun bandsHaveGapsBetweenThem() {
        assertEquals(WifiBand.Ghz24, bandOf(2437))
        assertEquals(WifiBand.Ghz5, bandOf(5500))
        assertEquals(WifiBand.Ghz6, bandOf(5955))
        assertNull(bandOf(5900))
        assertNull(bandOf(7200))
    }

    @Test
    fun widthCodesMapToMegahertz() {
        assertEquals(20, widthMhzOf(ScanResult.CHANNEL_WIDTH_20MHZ))
        assertEquals(40, widthMhzOf(ScanResult.CHANNEL_WIDTH_40MHZ))
        assertEquals(80, widthMhzOf(ScanResult.CHANNEL_WIDTH_80MHZ))
        assertEquals(160, widthMhzOf(ScanResult.CHANNEL_WIDTH_160MHZ))
        assertEquals(160, widthMhzOf(ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ))
        assertEquals(320, widthMhzOf(5))
    }

    @Test
    fun wifiSixOnTheSixGigahertzBandIsSixE() {
        assertEquals(WifiStandard.Wifi6, standardOf(ScanResult.WIFI_STANDARD_11AX, WifiBand.Ghz5))
        assertEquals(WifiStandard.Wifi6E, standardOf(ScanResult.WIFI_STANDARD_11AX, WifiBand.Ghz6))
        assertEquals(WifiStandard.Wifi4, standardOf(ScanResult.WIFI_STANDARD_11N, WifiBand.Ghz24))
        assertEquals(WifiStandard.Wifi5, standardOf(ScanResult.WIFI_STANDARD_11AC, WifiBand.Ghz5))
        assertEquals(WifiStandard.Wifi7, standardOf(8, WifiBand.Ghz6))
        assertEquals(WifiStandard.Unknown, standardOf(ScanResult.WIFI_STANDARD_UNKNOWN, null))
    }

    @Test
    fun securityFromCapabilityText() {
        assertEquals(Security.Wpa3, securityOf("[RSN-SAE-CCMP][ESS]"))
        assertEquals(Security.Wpa2, securityOf("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"))
        assertEquals(Security.Wpa, securityOf("[WPA-PSK-TKIP][ESS]"))
        assertEquals(Security.Owe, securityOf("[OWE][ESS]"))
        assertEquals(Security.Wep, securityOf("[WEP][ESS]"))
        assertEquals(Security.Open, securityOf("[ESS]"))
    }

    @Test
    fun neighbouringChannelsOverlapInTwoPointFourGigahertz() {
        val load = channelOccupancy(WifiBand.Ghz24, listOf(ap(1), ap(6))).associateBy { it.channel }
        assertEquals(1, load.getValue(1).count)
        assertEquals(2, load.getValue(3).count)
        assertEquals(1, load.getValue(6).count)
        assertEquals(0, load.getValue(11).count)
    }

    @Test
    fun wideChannelsCoverSeveralTwentyMegahertzChannels() {
        val wide = ap(channel = 36, band = WifiBand.Ghz5, width = 80, center = 5210, rssi = -50)
        val load = channelOccupancy(WifiBand.Ghz5, listOf(wide)).associateBy { it.channel }
        assertTrue(listOf(36, 40, 44, 48).all { load.getValue(it).count == 1 })
        assertEquals(0, load.getValue(52).count)
        assertEquals(-50, load.getValue(40).strongestRssi)
        assertNull(load.getValue(52).strongestRssi)
    }

    @Test
    fun otherBandsDoNotCount() {
        val load = channelOccupancy(WifiBand.Ghz24, listOf(ap(channel = 36, band = WifiBand.Ghz5)))
        assertTrue(load.all { it.count == 0 })
    }

    @Test
    fun throttleAllowsFourScansThenWaitsForTheFirstToExpire() {
        val throttle = ScanThrottle()
        repeat(4) { i ->
            assertTrue(throttle.canScan(i * 1_000L))
            throttle.record(i * 1_000L)
        }
        assertFalse(throttle.canScan(4_000))
        assertEquals(0, throttle.scansLeft(4_000))
        assertEquals(116_000, throttle.waitMs(4_000))
        assertTrue(throttle.canScan(120_000))
        assertEquals(1, throttle.scansLeft(120_000))
    }

    @Test
    fun throttleAllowsScansAgainAfterTheWindow() {
        val throttle = ScanThrottle()
        repeat(4) { throttle.record(0) }
        assertEquals(4, throttle.scansLeft(120_000))
        assertEquals(0, throttle.waitMs(120_000))
    }
}
