package com.devjugal.airscan.logging

import com.devjugal.airscan.radio.cellular.CellularState
import com.devjugal.airscan.radio.cellular.SimState
import com.devjugal.airscan.radio.cellular.bandLabel
import com.devjugal.airscan.radio.cellular.techLabel
import com.devjugal.airscan.radio.wifi.WifiConnection

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val accuracyM: Float?,
)

data class WifiSample(
    val ssid: String?,
    val bssid: String?,
    val rssi: Int,
    val channel: Int?,
    val band: String?,
    val linkMbps: Int?,
)

data class SimSample(
    val subscriptionId: Int,
    val slot: Int,
    val carrier: String,
    val tech: String,
    val rsrp: Int?,
    val rsrq: Int?,
    val sinr: Int?,
    val rssi: Int?,
    val dbm: Int?,
    val band: String,
    val pci: Int?,
    val tac: Int?,
    val cellId: Long?,
    val channel: Int?,
)

/** One moment of signal readings from every source, with the position if there was a fix. */
data class SignalSample(
    val timeMs: Long,
    val wifi: WifiSample?,
    val sims: List<SimSample>,
    val fix: LocationFix?,
) {
    /** One number per source for the history graph. The source is -1 for Wi-Fi, or a SIM subscription ID. */
    fun valueFor(source: Int): Int? =
        if (source == WIFI_SOURCE) {
            wifi?.rssi
        } else {
            sims.firstOrNull { it.subscriptionId == source }?.let { it.rsrp ?: it.dbm }
        }

    companion object {
        const val WIFI_SOURCE = -1
    }
}

fun WifiConnection.toSample() = WifiSample(ssid, bssid, rssi, channel, band?.label, linkSpeedMbps)

fun SimState.toSample(): SimSample {
    val cell = serving.firstOrNull()
    val signal = cell?.signal ?: signals.firstOrNull()
    return SimSample(
        subscriptionId = sim.subscriptionId,
        slot = sim.slotIndex,
        carrier = sim.carrierName,
        tech = techLabel(rat, nrMode),
        rsrp = signal?.rsrp,
        rsrq = signal?.rsrq,
        sinr = signal?.sinr,
        rssi = signal?.rssi,
        dbm = signal?.dbm,
        band = cell?.id?.let(::bandLabel).orEmpty(),
        pci = cell?.id?.pci,
        tac = cell?.id?.tac,
        cellId = cell?.id?.cellId,
        channel = cell?.id?.channel,
    )
}

fun sampleOf(
    timeMs: Long,
    wifi: WifiConnection?,
    cellular: CellularState?,
    fix: LocationFix?,
) = SignalSample(timeMs, wifi?.toSample(), cellular?.sims.orEmpty().map { it.toSample() }, fix)

data class Stats(
    val min: Int,
    val avg: Int,
    val max: Int,
    val count: Int,
)

fun statsOf(values: List<Int?>): Stats? {
    val present = values.filterNotNull()
    if (present.isEmpty()) return null
    return Stats(present.min(), Math.round(present.average()).toInt(), present.max(), present.size)
}
