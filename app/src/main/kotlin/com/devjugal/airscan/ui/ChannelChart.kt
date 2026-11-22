package com.devjugal.airscan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devjugal.airscan.radio.wifi.AccessPoint
import com.devjugal.airscan.radio.wifi.WifiBand
import com.devjugal.airscan.radio.wifi.channelsOf
import com.devjugal.airscan.radio.wifi.frequencyOf

/** Each network is an arch over the channels it covers. Taller means stronger. The connected network has a thick outline. */
@Composable
fun ChannelChart(
    band: WifiBand,
    accessPoints: List<AccessPoint>,
    connectedBssid: String?,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.onSurface
    val labelStyle = TextStyle(color = axis, fontSize = 10.sp)
    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val span = (band.endMhz - band.startMhz).toFloat()

        fun x(mhz: Int) = left + (mhz - band.startMhz) / span * (right - left)

        drawLine(axis, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
        val channels = channelsOf(band)
        val labelEvery = if (channels.size > 14) 4 else 1
        channels.forEachIndexed { index, channel ->
            if (index % labelEvery == 0) {
                val label = measurer.measure(channel.toString(), labelStyle)
                val cx = x(frequencyOf(band, channel))
                drawText(label, topLeft = Offset(cx - label.size.width / 2f, bottom + 4.dp.toPx()))
            }
        }
        accessPoints
            .filter { it.band == band }
            .sortedBy { it.rssi }
            .forEach { ap ->
                val fraction = fractionOf(ap.rssi, SignalKind.WifiRssi)
                val half = if (band == WifiBand.Ghz24 && ap.widthMhz == 20) 11 else ap.widthMhz / 2
                val x0 = x(ap.centerMhz - half)
                val x1 = x(ap.centerMhz + half)
                val peak = bottom - fraction.coerceAtLeast(0.05f) * (bottom - top)
                val path =
                    Path().apply {
                        moveTo(x0, bottom)
                        // The control point sits so the curve peaks at `peak`.
                        quadraticTo((x0 + x1) / 2, 2 * peak - bottom, x1, bottom)
                        close()
                    }
                val color = signalColor(fraction)
                drawPath(path, color.copy(alpha = 0.45f))
                val connected = connectedBssid != null && ap.bssid.equals(connectedBssid, ignoreCase = true)
                drawPath(
                    path,
                    outline.copy(alpha = if (connected) 1f else 0.5f),
                    style = Stroke(width = (if (connected) 3 else 1).dp.toPx()),
                )
            }
    }
}
