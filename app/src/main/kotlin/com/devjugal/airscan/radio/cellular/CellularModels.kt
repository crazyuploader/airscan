package com.devjugal.airscan.radio.cellular

import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager

/** Android reports "no value" as this number. */
private const val UNAVAILABLE_INT = Int.MAX_VALUE
private const val UNAVAILABLE_LONG = Long.MAX_VALUE

fun Int.orNull(): Int? = if (this == UNAVAILABLE_INT) null else this

fun Long.orNull(): Long? = if (this == UNAVAILABLE_LONG) null else this

enum class Rat { Lte, Nr, Wcdma, Gsm, Cdma, Unknown }

enum class NrMode { Sa, Nsa, None }

/** Whether cell identity can be read, or only the signal numbers that need no permission. */
enum class CellAccess { Full, SignalOnly }

data class SimInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrierName: String,
    val plmn: String,
    val isDefaultData: Boolean,
)

/** Signal numbers for one radio technology. Every field is null when the modem does not report it. */
data class SignalReading(
    val rat: Rat,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val sinr: Int? = null,
    val rssi: Int? = null,
    val cqi: Int? = null,
    val timingAdvance: Int? = null,
    val dbm: Int? = null,
    val level: Int? = null,
)

data class CellId(
    val rat: Rat,
    val mcc: String? = null,
    val mnc: String? = null,
    val pci: Int? = null,
    val tac: Int? = null,
    val cellId: Long? = null,
    /** EARFCN for LTE, NRARFCN for NR, UARFCN or ARFCN for 3G and 2G. */
    val channel: Int? = null,
    val bands: List<Int> = emptyList(),
    val bandwidthKhz: Int? = null,
) {
    /** LTE cell ID is 20 bits of eNB ID and 8 bits of sector. */
    val enbId: Long? get() = if (rat == Rat.Lte) cellId?.shr(8) else null
    val sectorId: Long? get() = if (rat == Rat.Lte) cellId?.and(0xFF) else null
}

data class CellReading(
    val id: CellId,
    val signal: SignalReading,
    val registered: Boolean,
)

/** Everything for one SIM, as read from Android. [registrations] are the serving cell identities from the service state. */
data class SimRaw(
    val sim: SimInfo,
    val displayNetworkType: Int? = null,
    val displayOverride: Int? = null,
    val registrations: List<CellId> = emptyList(),
    val signals: List<SignalReading> = emptyList(),
)

data class SimState(
    val sim: SimInfo,
    val rat: Rat,
    val nrMode: NrMode,
    val signals: List<SignalReading>,
    val serving: List<CellReading>,
)

data class CellularState(
    val access: CellAccess,
    val sims: List<SimState>,
    /** Cells that are not the serving cell of any SIM. Android gives one device-wide list. */
    val neighbours: List<CellReading>,
)

/**
 * 5G mode from the display info. A network type of NR means standalone.
 * An LTE network type with an NR override means non-standalone.
 */
fun nrModeOf(
    networkType: Int?,
    override: Int?,
): NrMode =
    when {
        networkType == TelephonyManager.NETWORK_TYPE_NR -> NrMode.Sa
        override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> NrMode.Nsa
        override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> NrMode.Nsa
        override == OVERRIDE_NR_NSA_MMWAVE -> NrMode.Nsa
        else -> NrMode.None
    }

// Deprecated platform constant OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE.
private const val OVERRIDE_NR_NSA_MMWAVE = 4

/** Short label such as "5G SA", "LTE + 5G NSA" or "LTE". */
fun techLabel(
    rat: Rat,
    nrMode: NrMode,
): String =
    when {
        rat == Rat.Nr -> "5G SA"
        rat == Rat.Lte && nrMode == NrMode.Nsa -> "LTE + 5G NSA"
        rat == Rat.Lte -> "LTE"
        rat == Rat.Wcdma -> "3G"
        rat == Rat.Gsm -> "2G"
        rat == Rat.Cdma -> "CDMA"
        else -> "Unknown"
    }

/** "n78" for 5G, "B3" for LTE, empty when the band is not known. */
fun bandLabel(id: CellId): String {
    val prefix =
        when (id.rat) {
            Rat.Nr -> "n"
            Rat.Lte -> "B"
            else -> ""
        }
    return id.bands.joinToString(", ") { "$prefix$it" }
}

/** Two readings describe the same cell when technology, PCI and channel agree, and the cell IDs agree when both are known. */
fun sameCell(
    a: CellId,
    b: CellId,
): Boolean =
    a.rat == b.rat &&
        a.pci == b.pci &&
        a.channel == b.channel &&
        (a.cellId == null || b.cellId == null || a.cellId == b.cellId)

/**
 * Builds the UI state. The serving cell of a SIM is its service-state registration identity.
 * Signal numbers come from the matching entry in the device-wide cell list, or else from that SIM's own signal strength.
 */
fun assembleCellularState(
    access: CellAccess,
    sims: List<SimRaw>,
    cells: List<CellReading>,
): CellularState {
    val simStates =
        sims.map { raw ->
            val serving =
                raw.registrations.map { reg ->
                    cells.firstOrNull { it.registered && sameCell(it.id, reg) }
                        ?: CellReading(
                            id = reg,
                            signal = raw.signals.firstOrNull { it.rat == reg.rat } ?: SignalReading(reg.rat),
                            registered = true,
                        )
                }
            val mode = nrModeOf(raw.displayNetworkType, raw.displayOverride)
            val rat =
                when {
                    raw.displayNetworkType == TelephonyManager.NETWORK_TYPE_NR -> Rat.Nr
                    serving.isNotEmpty() -> serving.first().id.rat
                    else -> raw.signals.firstOrNull()?.rat ?: Rat.Unknown
                }
            SimState(raw.sim, rat, mode, raw.signals, serving)
        }
    val servingIds = simStates.flatMap { s -> s.serving.map { it.id } }
    val neighbours =
        cells
            .filter { cell -> servingIds.none { sameCell(it, cell.id) } }
            .sortedByDescending { it.signal.rsrp ?: it.signal.dbm ?: Int.MIN_VALUE }
    return CellularState(access, simStates, neighbours)
}

/** A one-line name for a neighbour cell. 2G and 3G cells have no PCI, so they show their channel number instead. */
fun neighbourLabel(id: CellId): String {
    val pci = id.pci?.toString() ?: "n/a"
    val channel = id.channel?.toString() ?: "n/a"
    return when (id.rat) {
        Rat.Lte -> "LTE PCI $pci · ${bandLabel(id).ifBlank { "?" }}"
        Rat.Nr -> "5G PCI $pci · ${bandLabel(id).ifBlank { "?" }}"
        Rat.Wcdma -> "3G PSC $pci · UARFCN $channel"
        Rat.Gsm -> "2G ARFCN $channel"
        else -> techLabel(id.rat, NrMode.None) + " channel $channel"
    }
}
