package com.devjugal.airscan.radio.wifi

import com.devjugal.airscan.radio.RadioMonitor

enum class ScanRequest { Started, Throttled, Refused, NotAllowed }

interface WifiMonitor : RadioMonitor<WifiState> {
    /** Asks Android for a new scan, if our own budget and the permissions allow one. */
    fun requestScan(): ScanRequest
}
