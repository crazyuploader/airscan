package com.devjugal.airscan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devjugal.airscan.permissions.Grants
import com.devjugal.airscan.radio.RadioMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** Listens to one radio only while the screen is visible, and restarts when the grants change. */
open class RadioViewModel<S>(
    monitor: RadioMonitor<S>,
) : ViewModel() {
    private val grants = MutableStateFlow<Grants?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<S?> =
        grants
            .filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest { monitor.states(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_DELAY_MS), null)

    fun onGrants(value: Grants) {
        grants.value = value
    }

    private companion object {
        const val STOP_DELAY_MS = 5_000L
    }
}
