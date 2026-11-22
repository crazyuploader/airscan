package com.devjugal.airscan.radio.wifi

import android.net.wifi.ScanResult
import kotlin.math.abs

enum class WifiBand(
    val label: String,
    val startMhz: Int,
    val endMhz: Int,
) {
    Ghz24("2.4 GHz", 2400, 2495),
    Ghz5("5 GHz", 5150, 5895),
    Ghz6("6 GHz", 5925, 7125),
}

enum class WifiStandard(
    val label: String,
) {
    Unknown("?"),
    Legacy("Wi-Fi 1-3"),
    Wifi4("Wi-Fi 4"),
    Wifi5("Wi-Fi 5"),
    Wifi6("Wi-Fi 6"),
    Wifi6E("Wi-Fi 6E"),
    Wifi7("Wi-Fi 7"),
}

enum class Security(
    val label: String,
) {
    Open("Open"),
    Owe("OWE"),
    Wep("WEP"),
    Wpa("WPA"),
    Wpa2("WPA2"),
    Wpa3("WPA3"),
}

/** Whether the app may read names and scan results, or only the numbers that need no permission. */
enum class WifiAccess { Full, Limited }

data class AccessPoint(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val centerMhz: Int,
    val widthMhz: Int,
    val standard: WifiStandard,
    val security: Security,
    val ageMs: Long,
) {
    val band: WifiBand? get() = bandOf(frequencyMhz)
    val channel: Int? get() = channelOf(frequencyMhz)
}

data class WifiConnection(
    val ssid: String?,
    val bssid: String?,
    val rssi: Int,
    val linkSpeedMbps: Int?,
    val rxMbps: Int?,
    val txMbps: Int?,
    val frequencyMhz: Int,
    val widthMhz: Int?,
    val standard: WifiStandard,
) {
    val band: WifiBand? get() = bandOf(frequencyMhz)
    val channel: Int? get() = channelOf(frequencyMhz)
}

data class WifiState(
    val access: WifiAccess,
    val connection: WifiConnection?,
    val accessPoints: List<AccessPoint>,
    /** Age of the newest scan result, or null when there are none. */
    val scanAgeMs: Long?,
    val scansLeft: Int,
    /** Milliseconds until a new scan is allowed. Zero when one is allowed now. */
    val nextScanInMs: Long,
)

fun bandOf(frequencyMhz: Int): WifiBand? =
    when (frequencyMhz) {
        in 2400..2495 -> WifiBand.Ghz24
        in 5150..5895 -> WifiBand.Ghz5
        in 5925..7125 -> WifiBand.Ghz6
        else -> null
    }

fun channelOf(frequencyMhz: Int): Int? =
    when (bandOf(frequencyMhz)) {
        WifiBand.Ghz24 -> if (frequencyMhz == 2484) 14 else (frequencyMhz - 2407) / 5
        WifiBand.Ghz5 -> (frequencyMhz - 5000) / 5
        WifiBand.Ghz6 -> (frequencyMhz - 5950) / 5
        null -> null
    }

fun frequencyOf(
    band: WifiBand,
    channel: Int,
): Int =
    when (band) {
        WifiBand.Ghz24 -> if (channel == 14) 2484 else 2407 + channel * 5
        WifiBand.Ghz5 -> 5000 + channel * 5
        WifiBand.Ghz6 -> 5950 + channel * 5
    }

fun widthMhzOf(channelWidth: Int): Int =
    when (channelWidth) {
        ScanResult.CHANNEL_WIDTH_20MHZ -> 20
        ScanResult.CHANNEL_WIDTH_40MHZ -> 40
        ScanResult.CHANNEL_WIDTH_80MHZ -> 80
        ScanResult.CHANNEL_WIDTH_160MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
        CHANNEL_WIDTH_320MHZ -> 320
        else -> 20
    }

// ScanResult.CHANNEL_WIDTH_320MHZ is API 33. The value is fixed, so the constant avoids a version check.
private const val CHANNEL_WIDTH_320MHZ = 5

/** Standard code from `ScanResult.getWifiStandard` or `WifiInfo.getWifiStandard`. 6 GHz with Wi-Fi 6 is Wi-Fi 6E. */
fun standardOf(
    code: Int,
    band: WifiBand?,
): WifiStandard =
    when (code) {
        ScanResult.WIFI_STANDARD_LEGACY -> WifiStandard.Legacy
        ScanResult.WIFI_STANDARD_11N -> WifiStandard.Wifi4
        ScanResult.WIFI_STANDARD_11AC -> WifiStandard.Wifi5
        ScanResult.WIFI_STANDARD_11AX -> if (band == WifiBand.Ghz6) WifiStandard.Wifi6E else WifiStandard.Wifi6
        WIFI_STANDARD_11BE -> WifiStandard.Wifi7
        else -> WifiStandard.Unknown
    }

// ScanResult.WIFI_STANDARD_11BE is API 33.
private const val WIFI_STANDARD_11BE = 8

fun securityOf(capabilities: String): Security =
    when {
        "SAE" in capabilities -> Security.Wpa3
        "RSN" in capabilities || "WPA2" in capabilities -> Security.Wpa2
        "WPA" in capabilities -> Security.Wpa
        "OWE" in capabilities -> Security.Owe
        "WEP" in capabilities -> Security.Wep
        else -> Security.Open
    }

/** The 20 MHz channels to show for a band. */
fun channelsOf(band: WifiBand): List<Int> =
    when (band) {
        WifiBand.Ghz24 -> (1..13).toList()
        WifiBand.Ghz5 -> (36..64 step 4) + (100..144 step 4) + (149..165 step 4)
        WifiBand.Ghz6 -> (1..233 step 4).toList()
    }

data class ChannelLoad(
    val channel: Int,
    val count: Int,
    val strongestRssi: Int?,
)

/**
 * How many access points overlap each channel. An access point covers its centre frequency plus or minus half its width.
 * A 20 MHz channel in 2.4 GHz is 22 MHz wide, so neighbouring channels overlap there.
 */
fun channelOccupancy(
    band: WifiBand,
    accessPoints: List<AccessPoint>,
): List<ChannelLoad> {
    val inBand = accessPoints.filter { it.band == band }
    val halfChannel = if (band == WifiBand.Ghz24) 11 else 10
    return channelsOf(band).map { channel ->
        val centre = frequencyOf(band, channel)
        val overlapping =
            inBand.filter { ap ->
                val half = if (band == WifiBand.Ghz24 && ap.widthMhz == 20) 11 else ap.widthMhz / 2
                abs(centre - ap.centerMhz) < half + halfChannel
            }
        ChannelLoad(channel, overlapping.size, overlapping.maxOfOrNull { it.rssi })
    }
}

/**
 * Android allows 4 Wi-Fi scans per 2 minutes to a foreground app. This tracks our own scans so the app
 * never asks for one that Android would refuse.
 */
class ScanThrottle(
    private val limit: Int = 4,
    private val windowMs: Long = 120_000,
) {
    private val times = ArrayDeque<Long>()

    private fun prune(now: Long) {
        while (times.isNotEmpty() && now - times.first() >= windowMs) times.removeFirst()
    }

    fun scansLeft(now: Long): Int {
        prune(now)
        return limit - times.size
    }

    fun canScan(now: Long): Boolean = scansLeft(now) > 0

    fun record(now: Long) {
        prune(now)
        times.addLast(now)
    }

    fun waitMs(now: Long): Long {
        prune(now)
        return if (times.size < limit) 0 else times.first() + windowMs - now
    }
}
