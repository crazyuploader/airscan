package com.devjugal.airscan.radio.cellular

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.CellInfo
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import com.devjugal.airscan.permissions.Grants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reads every active SIM. Callbacks run on the main thread, so the state below needs no lock.
 * Each listener has its own callback object, because one refused listener must not stop the others.
 *
 * Lint cannot see the permission guards: every protected call is behind a Grants check or a SecurityException catch.
 */
@SuppressLint("MissingPermission")
class AndroidCellularMonitor(
    context: Context,
) : CellularMonitor {
    private val appContext = context.applicationContext
    private val telephony: TelephonyManager = appContext.getSystemService(TelephonyManager::class.java)
    private val subscriptions: SubscriptionManager = appContext.getSystemService(SubscriptionManager::class.java)

    override fun states(grants: Grants): Flow<CellularState> =
        callbackFlow {
            val executor = appContext.mainExecutor
            val access = if (grants.canReadCells) CellAccess.Full else CellAccess.SignalOnly
            val registered = mutableListOf<Pair<TelephonyManager, TelephonyCallback>>()
            val sims = loadSims(grants).toMutableList()
            var cells: List<CellReading> = emptyList()
            val raws = sims.associate { it.subscriptionId to SimRaw(it) }.toMutableMap()

            fun publish() {
                trySend(assembleCellularState(access, raws.values.toList(), cells))
            }

            fun update(
                id: Int,
                change: (SimRaw) -> SimRaw,
            ) {
                raws[id]?.let { raws[id] = change(it) }
                publish()
            }

            fun <T : TelephonyCallback> listen(
                tm: TelephonyManager,
                callback: T,
            ) {
                try {
                    tm.registerTelephonyCallback(executor, callback)
                    registered += tm to callback
                } catch (_: SecurityException) {
                    // The grant is missing. The screen explains this, so the data stays empty.
                }
            }

            for (sim in sims) {
                val tm = managerFor(sim.subscriptionId)
                val id = sim.subscriptionId
                runCatching { tm.signalStrength }.getOrNull()?.let { ss ->
                    raws[id] = raws.getValue(id).copy(signals = ss.toSignals())
                }
                if (grants.phoneState) {
                    runCatching { tm.serviceState }.getOrNull()?.let { raws[id] = raws.getValue(id).withService(it) }
                }
                listen(
                    tm,
                    object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                            update(id) { it.copy(signals = signalStrength.toSignals()) }
                        }
                    },
                )
                listen(
                    tm,
                    object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                        override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                            update(id) {
                                it.copy(
                                    displayNetworkType = telephonyDisplayInfo.networkType,
                                    displayOverride = telephonyDisplayInfo.overrideNetworkType,
                                )
                            }
                        }
                    },
                )
                if (grants.phoneState) {
                    listen(
                        tm,
                        object : TelephonyCallback(), TelephonyCallback.ServiceStateListener {
                            override fun onServiceStateChanged(serviceState: ServiceState) {
                                update(id) { it.withService(serviceState) }
                            }
                        },
                    )
                }
            }

            if (grants.canReadCells) {
                cells = readCells()
                listen(
                    telephony,
                    object : TelephonyCallback(), TelephonyCallback.CellInfoListener {
                        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
                            cells = cellInfo.mapNotNull { it.toReading() }
                            publish()
                        }
                    },
                )
                // The change listener is quiet while nothing moves, so ask for fresh values now and then.
                launch {
                    while (isActive) {
                        delay(CELL_REFRESH_MS)
                        requestCells(executor) {
                            cells = it
                            publish()
                        }
                    }
                }
            }
            publish()
            awaitClose { registered.forEach { (tm, cb) -> runCatching { tm.unregisterTelephonyCallback(cb) } } }
        }

    private fun managerFor(subscriptionId: Int): TelephonyManager =
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            telephony
        } else {
            telephony.createForSubscriptionId(subscriptionId)
        }

    /** Without phone state Android hides the SIM list, so show one entry for the default SIM. */
    private fun loadSims(grants: Grants): List<SimInfo> {
        val defaultData = SubscriptionManager.getDefaultDataSubscriptionId()
        val infos =
            if (grants.phoneState) {
                runCatching { subscriptions.activeSubscriptionInfoList }.getOrNull().orEmpty()
            } else {
                emptyList()
            }
        if (infos.isEmpty()) {
            return listOf(SimInfo(SubscriptionManager.INVALID_SUBSCRIPTION_ID, 0, "", "", true))
        }
        return infos
            .sortedBy { it.simSlotIndex }
            .map {
                SimInfo(
                    subscriptionId = it.subscriptionId,
                    slotIndex = it.simSlotIndex,
                    carrierName = it.carrierName?.toString().orEmpty(),
                    plmn = "${it.mccString.orEmpty()}${it.mncString.orEmpty()}",
                    isDefaultData = it.subscriptionId == defaultData,
                )
            }
    }

    private fun readCells(): List<CellReading> = runCatching { telephony.allCellInfo }.getOrNull().orEmpty().mapNotNull { it.toReading() }

    private fun requestCells(
        executor: java.util.concurrent.Executor,
        onResult: (List<CellReading>) -> Unit,
    ) {
        try {
            telephony.requestCellInfoUpdate(
                executor,
                object : TelephonyManager.CellInfoCallback() {
                    override fun onCellInfo(cellInfo: MutableList<CellInfo>) = onResult(cellInfo.mapNotNull { it.toReading() })

                    override fun onError(
                        errorCode: Int,
                        detail: Throwable?,
                    ) = Unit
                },
            )
        } catch (_: SecurityException) {
            // Permission was revoked while the screen was open. The next grants change restarts the flow.
        }
    }

    private fun SimRaw.withService(state: ServiceState): SimRaw =
        copy(registrations = state.networkRegistrationInfoList.servingIdentities())

    private fun SignalStrength.toSignals(): List<SignalReading> = cellSignalStrengths.map { it.toSignal() }

    private companion object {
        const val CELL_REFRESH_MS = 5_000L
    }
}
