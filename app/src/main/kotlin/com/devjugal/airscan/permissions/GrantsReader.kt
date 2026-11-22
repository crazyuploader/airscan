package com.devjugal.airscan.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

fun readGrants(context: Context): Grants {
    fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val location = context.getSystemService(LocationManager::class.java)
    return Grants(
        fineLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION),
        coarseLocation = granted(Manifest.permission.ACCESS_COARSE_LOCATION),
        phoneState = granted(Manifest.permission.READ_PHONE_STATE),
        locationEnabled = location.isLocationEnabled,
    )
}
