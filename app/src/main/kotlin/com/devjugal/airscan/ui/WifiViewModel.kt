package com.devjugal.airscan.ui

import com.devjugal.airscan.radio.wifi.ScanRequest
import com.devjugal.airscan.radio.wifi.WifiMonitor
import com.devjugal.airscan.radio.wifi.WifiState

class WifiViewModel(
    private val wifi: WifiMonitor,
) : RadioViewModel<WifiState>(wifi) {
    fun scanNow(): ScanRequest = wifi.requestScan()
}
