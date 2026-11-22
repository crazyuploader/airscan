package com.devjugal.airscan.ui

import com.devjugal.airscan.permissions.Grants
import com.devjugal.airscan.radio.cellular.CellAccess
import com.devjugal.airscan.radio.cellular.CellularMonitor
import com.devjugal.airscan.radio.cellular.CellularState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadioViewModelTest {
    private class FakeMonitor : CellularMonitor {
        val started = mutableListOf<Grants>()

        override fun states(grants: Grants): Flow<CellularState> {
            started += grants
            val access = if (grants.canReadCells) CellAccess.Full else CellAccess.SignalOnly
            return flowOf(CellularState(access, emptyList(), emptyList()))
        }
    }

    private val none = Grants(fineLocation = false, coarseLocation = false, phoneState = false, locationEnabled = true)
    private val full = Grants(fineLocation = true, coarseLocation = true, phoneState = true, locationEnabled = true)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun nothingStartsBeforeTheGrantsAreKnown() =
        runTest {
            val monitor = FakeMonitor()
            val vm = RadioViewModel(monitor)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
            assertNull(vm.state.value)
            assertEquals(0, monitor.started.size)
        }

    @Test
    fun theMonitorRestartsWhenTheGrantsChange() =
        runTest {
            val monitor = FakeMonitor()
            val vm = RadioViewModel(monitor)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
            vm.onGrants(none)
            assertEquals(CellAccess.SignalOnly, vm.state.value?.access)
            vm.onGrants(full)
            assertEquals(CellAccess.Full, vm.state.value?.access)
            assertEquals(listOf(none, full), monitor.started)
        }

    @Test
    fun sameGrantsDoNotRestartTheMonitor() =
        runTest {
            val monitor = FakeMonitor()
            val vm = RadioViewModel(monitor)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
            vm.onGrants(full)
            vm.onGrants(full.copy())
            assertEquals(1, monitor.started.size)
        }
}
