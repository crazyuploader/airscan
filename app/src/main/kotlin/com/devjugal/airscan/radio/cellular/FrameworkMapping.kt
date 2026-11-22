package com.devjugal.airscan.radio.cellular

import android.os.Build
import android.telephony.AccessNetworkConstants
import android.telephony.CellIdentity
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrength
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthWcdma
import android.telephony.NetworkRegistrationInfo

/** Thin adapters from Android classes to Airscan models. They hold no logic beyond field copying. */

fun CellInfo.toReading(): CellReading? {
    val id = cellIdentity.toCellId() ?: return null
    return CellReading(id, cellSignalStrength.toSignal(), isRegistered)
}

fun CellIdentity.toCellId(): CellId? =
    when (this) {
        is CellIdentityLte -> {
            CellId(
                rat = Rat.Lte,
                mcc = mccString,
                mnc = mncString,
                pci = pci.orNull(),
                tac = tac.orNull(),
                cellId = ci.orNull()?.toLong(),
                channel = earfcn.orNull(),
                bands = bands.toList(),
                bandwidthKhz = bandwidth.orNull(),
            )
        }

        is CellIdentityNr -> {
            CellId(
                rat = Rat.Nr,
                mcc = mccString,
                mnc = mncString,
                pci = pci.orNull(),
                tac = tac.orNull(),
                cellId = nci.orNull(),
                channel = nrarfcn.orNull(),
                bands = bands.toList(),
            )
        }

        is CellIdentityWcdma -> {
            CellId(
                rat = Rat.Wcdma,
                mcc = mccString,
                mnc = mncString,
                pci = psc.orNull(),
                tac = lac.orNull(),
                cellId = cid.orNull()?.toLong(),
                channel = uarfcn.orNull(),
            )
        }

        is CellIdentityGsm -> {
            CellId(
                rat = Rat.Gsm,
                mcc = mccString,
                mnc = mncString,
                tac = lac.orNull(),
                cellId = cid.orNull()?.toLong(),
                channel = arfcn.orNull(),
            )
        }

        else -> {
            null
        }
    }

fun CellSignalStrength.toSignal(): SignalReading =
    when (this) {
        is CellSignalStrengthLte -> {
            SignalReading(
                rat = Rat.Lte,
                rsrp = rsrp.orNull(),
                rsrq = rsrq.orNull(),
                sinr = rssnr.orNull(),
                rssi = rssi.orNull(),
                cqi = cqi.orNull(),
                timingAdvance = timingAdvance.orNull(),
                dbm = dbm.orNull(),
                level = level,
            )
        }

        is CellSignalStrengthNr -> {
            SignalReading(
                rat = Rat.Nr,
                rsrp = ssRsrp.orNull() ?: csiRsrp.orNull(),
                rsrq = ssRsrq.orNull() ?: csiRsrq.orNull(),
                sinr = ssSinr.orNull() ?: csiSinr.orNull(),
                cqi = csiCqiReport.firstOrNull(),
                timingAdvance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) timingAdvanceMicros.orNull() else null,
                dbm = dbm.orNull(),
                level = level,
            )
        }

        is CellSignalStrengthWcdma -> {
            SignalReading(Rat.Wcdma, dbm = dbm.orNull(), level = level)
        }

        is CellSignalStrengthGsm -> {
            SignalReading(Rat.Gsm, dbm = dbm.orNull(), level = level, timingAdvance = timingAdvance.orNull())
        }

        else -> {
            SignalReading(Rat.Unknown, dbm = dbm.orNull(), level = level)
        }
    }

/** Serving cell identity for a SIM: the cellular packet-data registration, or else any cellular registration. */
fun List<NetworkRegistrationInfo>.servingIdentities(): List<CellId> =
    filter { it.transportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN }
        .sortedByDescending { it.domain == NetworkRegistrationInfo.DOMAIN_PS }
        .mapNotNull { it.cellIdentity?.toCellId() }
        .distinctBy { Triple(it.rat, it.pci, it.channel) }
