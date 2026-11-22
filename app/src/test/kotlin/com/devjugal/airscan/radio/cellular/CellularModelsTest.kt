package com.devjugal.airscan.radio.cellular

import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CellularModelsTest {
    // Values seen on the test phone: Airtel on LTE band 3, Jio on 5G SA band n78.
    private val airtelId =
        CellId(Rat.Lte, "404", "97", pci = 445, tac = 2802, cellId = 229917953, channel = 1526, bands = listOf(3), bandwidthKhz = 15000)
    private val jioId = CellId(Rat.Nr, "405", "870", pci = 358, tac = 68, cellId = 3374481457, channel = 632064, bands = listOf(78))
    private val neighbourId = CellId(Rat.Lte, pci = 446, channel = 1526, bands = listOf(3))

    private val airtelSim = SimInfo(6, 0, "airtel", "40497", isDefaultData = false)
    private val jioSim = SimInfo(7, 1, "Jio", "405870", isDefaultData = true)

    private val cells =
        listOf(
            CellReading(airtelId, SignalReading(Rat.Lte, rsrp = -91, rsrq = -12, sinr = -3), registered = true),
            CellReading(jioId, SignalReading(Rat.Nr, rsrp = -114, rsrq = -13, sinr = 1), registered = true),
            CellReading(neighbourId, SignalReading(Rat.Lte, rsrp = -96), registered = false),
        )

    @Test
    fun unavailableNumbersBecomeNull() {
        assertNull(Int.MAX_VALUE.orNull())
        assertNull(Long.MAX_VALUE.orNull())
        assertEquals(-91, (-91).orNull())
        assertEquals(0, 0.orNull())
    }

    @Test
    fun networkTypeNrMeansStandalone() {
        assertEquals(NrMode.Sa, nrModeOf(TelephonyManager.NETWORK_TYPE_NR, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE))
    }

    @Test
    fun lteWithNrOverrideMeansNonStandalone() {
        val lte = TelephonyManager.NETWORK_TYPE_LTE
        assertEquals(NrMode.Nsa, nrModeOf(lte, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA))
        assertEquals(NrMode.Nsa, nrModeOf(lte, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED))
        assertEquals(NrMode.None, nrModeOf(lte, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA))
        assertEquals(NrMode.None, nrModeOf(null, null))
    }

    @Test
    fun techLabelsAreShort() {
        assertEquals("5G SA", techLabel(Rat.Nr, NrMode.Sa))
        assertEquals("LTE + 5G NSA", techLabel(Rat.Lte, NrMode.Nsa))
        assertEquals("LTE", techLabel(Rat.Lte, NrMode.None))
        assertEquals("3G", techLabel(Rat.Wcdma, NrMode.None))
        assertEquals("2G", techLabel(Rat.Gsm, NrMode.None))
    }

    @Test
    fun bandLabelsUseNrAndLtePrefixes() {
        assertEquals("n78", bandLabel(jioId))
        assertEquals("B3", bandLabel(airtelId))
        assertEquals("B3, B40", bandLabel(airtelId.copy(bands = listOf(3, 40))))
        assertEquals("", bandLabel(airtelId.copy(bands = emptyList())))
    }

    @Test
    fun lteCellIdSplitsIntoEnbAndSector() {
        assertEquals(898117L, airtelId.enbId)
        assertEquals(1L, airtelId.sectorId)
        assertNull(jioId.enbId)
    }

    @Test
    fun sameCellNeedsTechnologyPciAndChannel() {
        assertTrue(sameCell(airtelId, airtelId.copy(cellId = null)))
        assertFalse(sameCell(airtelId, neighbourId))
        assertFalse(sameCell(airtelId, airtelId.copy(cellId = 1L)))
        assertFalse(sameCell(airtelId, airtelId.copy(rat = Rat.Nr)))
    }

    @Test
    fun eachSimGetsItsOwnServingCellFromTheSharedList() {
        val state =
            assembleCellularState(
                CellAccess.Full,
                listOf(
                    SimRaw(
                        airtelSim,
                        displayNetworkType = TelephonyManager.NETWORK_TYPE_LTE,
                        displayOverride = 0,
                        registrations = listOf(airtelId),
                    ),
                    SimRaw(
                        jioSim,
                        displayNetworkType = TelephonyManager.NETWORK_TYPE_NR,
                        displayOverride = 0,
                        registrations = listOf(jioId),
                    ),
                ),
                cells,
            )
        val (airtel, jio) = state.sims
        assertEquals(Rat.Lte, airtel.rat)
        assertEquals(NrMode.None, airtel.nrMode)
        assertEquals(
            -91,
            airtel.serving
                .single()
                .signal.rsrp,
        )
        assertEquals(Rat.Nr, jio.rat)
        assertEquals(NrMode.Sa, jio.nrMode)
        assertEquals(
            -114,
            jio.serving
                .single()
                .signal.rsrp,
        )
    }

    @Test
    fun neighboursExcludeEveryServingCellAndSortStrongestFirst() {
        val extra = CellReading(neighbourId.copy(pci = 9), SignalReading(Rat.Lte, rsrp = -80), registered = false)
        val state =
            assembleCellularState(
                CellAccess.Full,
                listOf(SimRaw(airtelSim, registrations = listOf(airtelId)), SimRaw(jioSim, registrations = listOf(jioId))),
                cells + extra,
            )
        assertEquals(listOf(9, 446), state.neighbours.map { it.id.pci })
    }

    @Test
    fun servingCellFallsBackToTheSimSignalWhenTheListHasNoMatch() {
        val own = SignalReading(Rat.Nr, rsrp = -119, rsrq = -16, sinr = -3)
        val state =
            assembleCellularState(
                CellAccess.Full,
                listOf(
                    SimRaw(
                        jioSim,
                        displayNetworkType = TelephonyManager.NETWORK_TYPE_NR,
                        registrations = listOf(jioId),
                        signals = listOf(own),
                    ),
                ),
                emptyList(),
            )
        assertEquals(
            -119,
            state.sims
                .single()
                .serving
                .single()
                .signal.rsrp,
        )
    }

    @Test
    fun withoutRegistrationsTheTechnologyComesFromTheSignal() {
        val state =
            assembleCellularState(
                CellAccess.SignalOnly,
                listOf(SimRaw(airtelSim, signals = listOf(SignalReading(Rat.Lte, rsrp = -100)))),
                emptyList(),
            )
        assertEquals(Rat.Lte, state.sims.single().rat)
        assertTrue(
            state.sims
                .single()
                .serving
                .isEmpty(),
        )
        assertEquals(CellAccess.SignalOnly, state.access)
    }

    @Test
    fun nsaShowsLteAnchorWithNrMode() {
        val state =
            assembleCellularState(
                CellAccess.Full,
                listOf(
                    SimRaw(
                        airtelSim,
                        displayNetworkType = TelephonyManager.NETWORK_TYPE_LTE,
                        displayOverride = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                        registrations = listOf(airtelId),
                    ),
                ),
                cells,
            )
        assertEquals("LTE + 5G NSA", state.sims.single().let { techLabel(it.rat, it.nrMode) })
    }

    @Test
    fun neighbourLabelsUseTheNumbersEachTechnologyHas() {
        assertEquals("LTE PCI 446 · B3", neighbourLabel(neighbourId))
        assertEquals("5G PCI 358 · n78", neighbourLabel(jioId))
        assertEquals("2G ARFCN 711", neighbourLabel(CellId(Rat.Gsm, channel = 711)))
        assertEquals("3G PSC 77 · UARFCN 10700", neighbourLabel(CellId(Rat.Wcdma, pci = 77, channel = 10700)))
        assertEquals("2G ARFCN n/a", neighbourLabel(CellId(Rat.Gsm)))
    }
}
