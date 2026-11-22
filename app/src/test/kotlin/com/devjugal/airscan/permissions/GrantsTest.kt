package com.devjugal.airscan.permissions

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrantsTest {
    private val none = Grants(fineLocation = false, coarseLocation = false, phoneState = false, locationEnabled = true)

    @Test
    fun nothingGrantedMissesLocationAndPhoneState() {
        assertEquals(listOf(Missing.PreciseLocation, Missing.PhoneState), none.missing)
        assertFalse(none.canReadCells)
        assertFalse(none.canReadWifiDetails)
        assertFalse(none.complete)
    }

    @Test
    fun approximateOnlyIsDetectedAndStillNeedsPreciseLocation() {
        val g = none.copy(coarseLocation = true, phoneState = true)
        assertTrue(g.onlyApproximate)
        assertEquals(listOf(Missing.PreciseLocation), g.missing)
        assertFalse(g.canReadCells)
    }

    @Test
    fun fineWithoutPhoneStateCannotReadCells() {
        val g = none.copy(fineLocation = true, coarseLocation = true)
        assertFalse(g.onlyApproximate)
        assertEquals(listOf(Missing.PhoneState), g.missing)
        assertFalse(g.canReadCells)
        assertTrue(g.canReadWifiDetails)
    }

    @Test
    fun locationSwitchOffIsReportedOnlyWhenPreciseLocationIsGranted() {
        assertEquals(
            listOf(Missing.PreciseLocation, Missing.PhoneState),
            none.copy(locationEnabled = false).missing,
        )
        val g = Grants(fineLocation = true, coarseLocation = true, phoneState = true, locationEnabled = false)
        assertEquals(listOf(Missing.LocationSwitch), g.missing)
        assertTrue(g.canReadCells)
        assertFalse(g.canReadWifiDetails)
    }

    @Test
    fun everythingGrantedIsComplete() {
        val g = Grants(fineLocation = true, coarseLocation = true, phoneState = true, locationEnabled = true)
        assertTrue(g.complete)
        assertTrue(g.permissionsToRequest().isEmpty())
    }

    @Test
    fun requestAsksForFineWithCoarseAndPhoneState() {
        assertEquals(
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE,
            ),
            none.permissionsToRequest(),
        )
    }

    @Test
    fun requestSkipsWhatIsAlreadyGranted() {
        val g = none.copy(fineLocation = true, coarseLocation = true)
        assertEquals(listOf(Manifest.permission.READ_PHONE_STATE), g.permissionsToRequest())
    }

    @Test
    fun approximateOnlyAsksForFineAlone() {
        val g = none.copy(coarseLocation = true, phoneState = true)
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), g.permissionsToRequest())
    }
}
