package com.devjugal.airscan.logging

import com.devjugal.airscan.history.RingSeries
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SessionState(
    val recording: Boolean = false,
    /** The newest samples, oldest first. The log files hold everything. */
    val samples: List<SignalSample> = emptyList(),
    val total: Long = 0,
    val csvName: String? = null,
)

/** The current recording, shared between the service that writes it and the screen that draws it. */
object SessionStore {
    private const val KEEP = 3_600
    private val ring = RingSeries<SignalSample>(KEEP)
    private val mutable = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = mutable

    fun start(csvName: String) {
        ring.clear()
        mutable.value = SessionState(recording = true, csvName = csvName)
    }

    fun add(sample: SignalSample) {
        ring.add(sample)
        mutable.value = mutable.value.copy(samples = ring.snapshot().filterNotNull(), total = mutable.value.total + 1)
    }

    fun stop() {
        mutable.value = mutable.value.copy(recording = false)
    }
}
