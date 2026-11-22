package com.devjugal.airscan.logging

import java.io.File
import java.io.RandomAccessFile
import java.time.Instant

val CSV_HEADER =
    "time,epoch_ms,source,name,tech,rsrp_dbm,rsrq_db,sinr_db,rssi_dbm,signal_dbm,band,pci,tac,cell_id,channel," +
        "wifi_bssid,wifi_channel,wifi_band,wifi_link_mbps,latitude,longitude,altitude_m,accuracy_m"

/** Quotes a CSV field when it holds a comma, a quote or a line break. */
fun csvField(value: Any?): String {
    val text = value?.toString().orEmpty()
    return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
}

/** One row for Wi-Fi if present, and one row per SIM. Empty cells mean the value was not available. */
fun csvRows(sample: SignalSample): List<String> {
    val time = Instant.ofEpochMilli(sample.timeMs).toString()
    val fix = sample.fix
    val place = listOf(fix?.latitude, fix?.longitude, fix?.altitudeM, fix?.accuracyM)

    fun row(vararg cells: Any?) = (listOf(time, sample.timeMs) + cells.toList() + place).joinToString(",") { csvField(it) }
    val rows = mutableListOf<String>()
    sample.wifi?.let {
        rows +=
            row(
                "wifi",
                it.ssid,
                null,
                null,
                null,
                null,
                null,
                it.rssi,
                null,
                null,
                null,
                null,
                null,
                it.bssid,
                it.channel,
                it.band,
                it.linkMbps,
            )
    }
    for (sim in sample.sims) {
        rows +=
            row(
                "sim${sim.slot + 1}",
                sim.carrier,
                sim.tech,
                sim.rsrp,
                sim.rsrq,
                sim.sinr,
                sim.rssi,
                sim.dbm,
                sim.band,
                sim.pci,
                sim.tac,
                sim.cellId,
                sim.channel,
                null,
                null,
                null,
                null,
            )
    }
    return rows
}

private fun xml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

const val GPX_FOOTER = "</trkseg></trk></gpx>\n"

fun gpxHeader(name: String) =
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<gpx version=\"1.1\" creator=\"Airscan\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:airscan=\"urn:airscan:gpx:1\">\n" +
        "<trk><name>${xml(name)}</name><trkseg>\n"

/** A track point with the signal numbers in extensions. Returns null when the sample has no position. */
fun gpxPoint(sample: SignalSample): String? {
    val fix = sample.fix ?: return null
    val ext = StringBuilder()
    sample.wifi?.let { ext.append("<airscan:wifi_rssi>${it.rssi}</airscan:wifi_rssi>") }
    for (sim in sample.sims) {
        val n = sim.slot + 1
        sim.rsrp?.let { ext.append("<airscan:sim${n}_rsrp>$it</airscan:sim${n}_rsrp>") }
        sim.rsrq?.let { ext.append("<airscan:sim${n}_rsrq>$it</airscan:sim${n}_rsrq>") }
        sim.sinr?.let { ext.append("<airscan:sim${n}_sinr>$it</airscan:sim${n}_sinr>") }
    }
    val ele = fix.altitudeM?.let { "<ele>$it</ele>" }.orEmpty()
    val time = "<time>${Instant.ofEpochMilli(sample.timeMs)}</time>"
    val extensions = if (ext.isEmpty()) "" else "<extensions>$ext</extensions>"
    return "<trkpt lat=\"${fix.latitude}\" lon=\"${fix.longitude}\">$ele$time$extensions</trkpt>\n"
}

/** A GPX file that is valid after every write, so a crash or a killed service never leaves a broken track. */
class GpxFile(
    private val file: File,
    name: String,
) {
    private val footer = GPX_FOOTER.toByteArray()

    init {
        if (!file.exists() || file.length() == 0L) file.writeBytes(gpxHeader(name).toByteArray() + footer)
    }

    fun append(point: String) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(raf.length() - footer.size)
            raf.write(point.toByteArray() + footer)
        }
    }
}
