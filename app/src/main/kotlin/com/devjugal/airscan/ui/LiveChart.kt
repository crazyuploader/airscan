package com.devjugal.airscan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A strip chart of the most recent samples. It draws a fixed number of slots, so the line scrolls left as samples arrive.
 * Gaps (null) break the line. Each segment takes the colour of its signal level.
 */
@Composable
fun LiveChart(
    samples: List<Int?>,
    kind: SignalKind,
    slots: Int,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val outline = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val pad = 4.dp.toPx()
        val width = size.width - 2 * pad
        val h = size.height - 2 * pad
        for (i in 0..4) {
            val y = pad + h * i / 4
            drawLine(grid, Offset(pad, y), Offset(size.width - pad, y), strokeWidth = 1.dp.toPx())
        }
        val step = width / (slots - 1).coerceAtLeast(1)
        val offset = slots - samples.size

        fun point(index: Int): Offset? {
            val v = samples[index] ?: return null
            return Offset(pad + (index + offset) * step, pad + h * (1f - fractionOf(v, kind)))
        }
        for (i in 1 until samples.size) {
            val a = point(i - 1) ?: continue
            val b = point(i) ?: continue
            val color = signalColor(fractionOf(samples[i] ?: continue, kind))
            drawLine(outline, a, b, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
            drawLine(color, a, b, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}
