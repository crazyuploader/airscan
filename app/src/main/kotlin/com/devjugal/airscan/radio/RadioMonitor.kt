package com.devjugal.airscan.radio

import com.devjugal.airscan.permissions.Grants
import kotlinx.coroutines.flow.Flow

/**
 * One radio (cellular, Wi-Fi, later Bluetooth). Collect [states] to start listening, cancel to stop.
 * The flow reads nothing the grants do not allow, and restarts when the grants change.
 */
interface RadioMonitor<S> {
    fun states(grants: Grants): Flow<S>
}
