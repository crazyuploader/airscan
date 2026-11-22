package com.devjugal.airscan.ui

import com.devjugal.airscan.radio.cellular.SignalReading
import com.devjugal.airscan.radio.cellular.SimState

/** The one number the walking meter shows. */
data class MeterReading(
    val value: Int,
    val unit: String,
    val kind: SignalKind,
)

/** The Wi-Fi meter shows the signal of the connected network. */
fun wifiMeterReading(rssi: Int?): MeterReading? = rssi?.let { MeterReading(it, "dBm", SignalKind.WifiRssi) }

/**
 * The mobile meter shows RSRP of the first serving cell that reports it. On 5G standalone that is the NR cell.
 * With non-standalone 5G it is the LTE anchor. Without cell details it uses the SIM's own signal strength, then plain dBm.
 */
fun simMeterReading(sim: SimState): MeterReading? {
    val candidates: List<SignalReading> = sim.serving.map { it.signal } + sim.signals
    candidates.firstNotNullOfOrNull { it.rsrp }?.let { return MeterReading(it, "dBm", SignalKind.Rsrp) }
    return candidates.firstNotNullOfOrNull { it.dbm }?.let { MeterReading(it, "dBm", SignalKind.Dbm) }
}
