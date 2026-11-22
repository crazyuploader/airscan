package com.devjugal.airscan.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.roundToInt

/** Maps a signal number to 0 (worst) .. 1 (best). The ranges are the usual limits for each kind of number. */
enum class SignalKind(
    val worst: Int,
    val best: Int,
) {
    WifiRssi(-90, -30),
    Rsrp(-140, -70),
    Rsrq(-20, -3),
    Sinr(-5, 30),
    Dbm(-110, -50),
}

fun fractionOf(
    value: Int,
    kind: SignalKind,
): Float = ((value - kind.worst).toFloat() / (kind.best - kind.worst)).coerceIn(0f, 1f)

fun fractionOf(
    value: Int,
    worst: Int,
    best: Int,
): Float = ((value - worst).toFloat() / (best - worst)).coerceIn(0f, 1f)

enum class Quality { VeryPoor, Poor, Fair, Good, Excellent }

/** Text and bars give the quality too, so colour is never the only cue. */
fun qualityOf(fraction: Float): Quality =
    when {
        fraction < 0.2f -> Quality.VeryPoor
        fraction < 0.4f -> Quality.Poor
        fraction < 0.6f -> Quality.Fair
        fraction < 0.8f -> Quality.Good
        else -> Quality.Excellent
    }

/** 0 to 4 bars. */
fun barsOf(fraction: Float): Int = (fraction * 4).roundToInt().coerceIn(0, 4)

// Viridis, evenly spaced. It reads in grey scale and for colour-blind eyes.
private val Viridis =
    listOf(
        Color(0xFF440154),
        Color(0xFF472D7B),
        Color(0xFF3B528B),
        Color(0xFF2C728E),
        Color(0xFF21908C),
        Color(0xFF27AD81),
        Color(0xFF5DC863),
        Color(0xFFAADC32),
        Color(0xFFFDE725),
    )

// The weak end of the ramp starts lifted, so it stays visible on an AMOLED black background.
private const val RAMP_FLOOR = 0.15f

fun signalColor(fraction: Float): Color {
    val t = (RAMP_FLOOR + (1f - RAMP_FLOOR) * fraction.coerceIn(0f, 1f)) * (Viridis.size - 1)
    val i = t.toInt().coerceAtMost(Viridis.size - 2)
    return lerp(Viridis[i], Viridis[i + 1], t - i)
}

/**
 * Keeps the previous quality until the value moves clearly past an edge, so a number that sits on a boundary
 * does not flip level (and buzz the haptics) every second.
 */
fun stableQuality(
    previous: Quality?,
    fraction: Float,
    margin: Float = 0.03f,
): Quality {
    if (previous != null) {
        val low = previous.ordinal * 0.2f - margin
        val high = (previous.ordinal + 1) * 0.2f + margin
        if (fraction >= low && fraction < high) return previous
    }
    return qualityOf(fraction)
}
