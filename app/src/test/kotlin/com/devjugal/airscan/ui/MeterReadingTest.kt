package com.devjugal.airscan.ui

import com.devjugal.airscan.radio.cellular.CellId
import com.devjugal.airscan.radio.cellular.CellReading
import com.devjugal.airscan.radio.cellular.NrMode
import com.devjugal.airscan.radio.cellular.Rat
import com.devjugal.airscan.radio.cellular.SignalReading
import com.devjugal.airscan.radio.cellular.SimInfo
import com.devjugal.airscan.radio.cellular.SimState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeterReadingTest {
    private val sim = SimInfo(7, 1, "Jio", "405870", isDefaultData = true)

    private fun state(
        serving: List<SignalReading> = emptyList(),
        signals: List<SignalReading> = emptyList(),
    ) = SimState(
        sim,
        Rat.Nr,
        NrMode.Sa,
        signals,
        serving.map { CellReading(CellId(it.rat), it, registered = true) },
    )

    @Test
    fun wifiMeterUsesRssi() {
        assertEquals(MeterReading(-55, "dBm", SignalKind.WifiRssi), wifiMeterReading(-55))
        assertNull(wifiMeterReading(null))
    }

    @Test
    fun simMeterPrefersTheServingCellRsrp() {
        val reading =
            simMeterReading(
                state(serving = listOf(SignalReading(Rat.Nr, rsrp = -114)), signals = listOf(SignalReading(Rat.Nr, rsrp = -119))),
            )
        assertEquals(MeterReading(-114, "dBm", SignalKind.Rsrp), reading)
    }

    @Test
    fun simMeterFallsBackToTheSimSignalThenToDbm() {
        assertEquals(-119, simMeterReading(state(signals = listOf(SignalReading(Rat.Nr, rsrp = -119))))?.value)
        val old = simMeterReading(state(signals = listOf(SignalReading(Rat.Gsm, dbm = -95))))
        assertEquals(MeterReading(-95, "dBm", SignalKind.Dbm), old)
    }

    @Test
    fun simMeterHasNothingWhenNoNumbersExist() {
        assertNull(simMeterReading(state(signals = listOf(SignalReading(Rat.Lte)))))
    }
}
