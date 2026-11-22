package com.devjugal.airscan.radio

import android.content.Context
import com.devjugal.airscan.radio.cellular.AndroidCellularMonitor
import com.devjugal.airscan.radio.wifi.AndroidWifiMonitor

/** One monitor per radio for the whole app, so the screens and the recorder share the Wi-Fi scan budget. */
object Radios {
    @Volatile private var wifi: AndroidWifiMonitor? = null

    @Volatile private var cellular: AndroidCellularMonitor? = null

    fun wifi(context: Context): AndroidWifiMonitor = wifi ?: synchronized(this) { wifi ?: AndroidWifiMonitor(context).also { wifi = it } }

    fun cellular(context: Context): AndroidCellularMonitor =
        cellular ?: synchronized(this) { cellular ?: AndroidCellularMonitor(context).also { cellular = it } }
}
