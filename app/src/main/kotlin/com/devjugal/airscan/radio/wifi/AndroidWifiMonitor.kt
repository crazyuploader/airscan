package com.devjugal.airscan.radio.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.SystemClock
import com.devjugal.airscan.permissions.Grants
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Polls the Wi-Fi state once a second while someone collects it. Android hands out cached scan results,
 * so reading them is cheap. A new scan starts about every 35 seconds, which stays inside the 4 per 2 minutes limit.
 *
 * Lint cannot see the permission guards: results are read only when [Grants.canReadWifiDetails] is true, and a SecurityException is caught.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class AndroidWifiMonitor(
    context: Context,
) : WifiMonitor {
    private val wifi: WifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private val throttle = ScanThrottle()
    private var lastAutoScanAt = -AUTO_SCAN_MS
    private var allowed = false

    override fun states(grants: Grants): Flow<WifiState> =
        flow {
            allowed = grants.canReadWifiDetails
            while (true) {
                val now = SystemClock.elapsedRealtime()
                if (allowed && now - lastAutoScanAt >= AUTO_SCAN_MS && throttle.canScan(now)) {
                    lastAutoScanAt = now
                    scan(now)
                }
                emit(snapshot(grants, now))
                delay(POLL_MS)
            }
        }

    override fun requestScan(): ScanRequest {
        val now = SystemClock.elapsedRealtime()
        return when {
            !allowed -> ScanRequest.NotAllowed
            !throttle.canScan(now) -> ScanRequest.Throttled
            scan(now) -> ScanRequest.Started
            else -> ScanRequest.Refused
        }
    }

    private fun scan(now: Long): Boolean {
        val started = runCatching { wifi.startScan() }.getOrDefault(false)
        if (started) throttle.record(now)
        return started
    }

    private fun snapshot(
        grants: Grants,
        now: Long,
    ): WifiState {
        val points =
            if (grants.canReadWifiDetails) {
                runCatching { wifi.scanResults }.getOrNull().orEmpty().map { it.toAccessPoint(now) }
            } else {
                emptyList()
            }
        val info = runCatching { wifi.connectionInfo }.getOrNull()
        val connection = info?.takeIf { it.frequency > 0 }?.toConnection(points)
        return WifiState(
            access = if (grants.canReadWifiDetails) WifiAccess.Full else WifiAccess.Limited,
            connection = connection,
            accessPoints = points.sortedByDescending { it.rssi },
            scanAgeMs = points.minOfOrNull { it.ageMs },
            scansLeft = throttle.scansLeft(now),
            nextScanInMs = throttle.waitMs(now),
        )
    }

    private fun ScanResult.toAccessPoint(now: Long): AccessPoint {
        val band = bandOf(frequency)
        return AccessPoint(
            ssid = SSID.orEmpty(),
            bssid = BSSID.orEmpty(),
            rssi = level,
            frequencyMhz = frequency,
            centerMhz = if (centerFreq0 > 0) centerFreq0 else frequency,
            widthMhz = widthMhzOf(channelWidth),
            standard = standardOf(wifiStandard, band),
            security = securityOf(capabilities.orEmpty()),
            ageMs = (now - timestamp / 1000).coerceAtLeast(0),
        )
    }

    private fun WifiInfo.toConnection(points: List<AccessPoint>): WifiConnection {
        val own = points.firstOrNull { it.bssid.equals(bssid, ignoreCase = true) }
        return WifiConnection(
            ssid = ssid?.trim('"')?.takeUnless { it == UNKNOWN_SSID || it.isBlank() },
            bssid = bssid?.takeUnless { it == REDACTED_BSSID },
            rssi = rssi,
            linkSpeedMbps = linkSpeed.takeIf { it > 0 },
            rxMbps = rxLinkSpeedMbps.takeIf { it > 0 },
            txMbps = txLinkSpeedMbps.takeIf { it > 0 },
            frequencyMhz = frequency,
            widthMhz = own?.widthMhz,
            standard = standardOf(wifiStandard, bandOf(frequency)),
        )
    }

    private companion object {
        const val POLL_MS = 1_000L
        const val AUTO_SCAN_MS = 35_000L
        const val UNKNOWN_SSID = "<unknown ssid>"
        const val REDACTED_BSSID = "02:00:00:00:00:00"
    }
}
