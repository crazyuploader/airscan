package com.devjugal.airscan.permissions

import android.Manifest

/** What the user has allowed right now. Read it again whenever the app resumes. Never keep it. */
data class Grants(
    val fineLocation: Boolean,
    val coarseLocation: Boolean,
    val phoneState: Boolean,
    val locationEnabled: Boolean,
) {
    /** The user chose "approximate" in the location dialog. Android hides cell and Wi-Fi details from the app then. */
    val onlyApproximate: Boolean get() = coarseLocation && !fineLocation

    /** Cell identity and cell lists need precise location. Service state and its listeners need phone state too. */
    val canReadCells: Boolean get() = fineLocation && phoneState

    /** SSID, BSSID and scan results need precise location with the Location switch on. */
    val canReadWifiDetails: Boolean get() = fineLocation && locationEnabled

    /** Signal strength and the 5G hint need no permission, so the app can always show them. */
    val missing: List<Missing>
        get() =
            buildList {
                if (!fineLocation) add(Missing.PreciseLocation)
                if (!phoneState) add(Missing.PhoneState)
                if (fineLocation && !locationEnabled) add(Missing.LocationSwitch)
            }

    val complete: Boolean get() = missing.isEmpty()

    /** The runtime permissions to ask for. Coarse goes with fine only on the first ask, so the dialog offers the precise choice. */
    fun permissionsToRequest(): List<String> =
        buildList {
            if (!fineLocation) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                if (!coarseLocation) add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (!phoneState) add(Manifest.permission.READ_PHONE_STATE)
        }
}

enum class Missing {
    PreciseLocation,
    PhoneState,
    LocationSwitch,
}
