package com.devjugal.airscan.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.devjugal.airscan.R

@StringRes
fun Quality.label(): Int =
    when (this) {
        Quality.VeryPoor -> R.string.quality_very_poor
        Quality.Poor -> R.string.quality_poor
        Quality.Fair -> R.string.quality_fair
        Quality.Good -> R.string.quality_good
        Quality.Excellent -> R.string.quality_excellent
    }

/** A value with a colour swatch and a quality word. The word and the filled bars carry the meaning, not only the colour. */
@Composable
fun SignalValue(
    text: String,
    fraction: Float?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (fraction != null) {
            Surface(
                modifier = Modifier.size(14.dp),
                shape = CircleShape,
                color = signalColor(fraction),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
            ) {}
            SignalBars(barsOf(fraction))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
        if (fraction != null) {
            Text(stringResource(qualityOf(fraction).label()), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Four rising bars. Filled bars show the level, hollow bars show what is missing. */
@Composable
fun SignalBars(
    filled: Int,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.size(width = 22.dp, height = 14.dp)) {
        val barWidth = 4.dp.toPx()
        val gap = (size.width - 4 * barWidth) / 3
        for (i in 0 until 4) {
            val h = size.height * (i + 1) / 4
            val topLeft = Offset(i * (barWidth + gap), size.height - h)
            if (i < filled) {
                drawRect(color, topLeft, Size(barWidth, h))
            } else {
                drawRect(color, topLeft, Size(barWidth, h), style = Stroke(1.dp.toPx()))
            }
        }
    }
}
