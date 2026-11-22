package com.devjugal.airscan.ui

import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.devjugal.airscan.R
import com.devjugal.airscan.history.RingSeries
import com.devjugal.airscan.permissions.Grants
import com.devjugal.airscan.radio.Radios
import com.devjugal.airscan.radio.cellular.CellularState
import kotlinx.coroutines.delay

private const val WIFI = -1
private const val SLOTS = 120

/** Source: -1 is Wi-Fi, anything else is a SIM subscription ID. Only the chosen radio listens, which saves battery. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeterScreen(
    grants: Grants,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    KeepScreenOn()
    val wide = rememberIsWide()
    val context = LocalContext.current.applicationContext
    val cellularVm: RadioViewModel<CellularState> =
        viewModel(key = "cellular", factory = viewModelFactory { initializer { RadioViewModel(Radios.cellular(context)) } })
    val wifiVm: WifiViewModel = viewModel(factory = viewModelFactory { initializer { WifiViewModel(Radios.wifi(context)) } })
    var source by rememberSaveable { mutableStateOf(WIFI) }
    LaunchedEffect(grants) {
        cellularVm.onGrants(grants)
        wifiVm.onGrants(grants)
    }

    // The SIM list comes from the cellular state, so the cellular radio must be collected to build the chips.
    val cellular by cellularVm.state.collectAsStateWithLifecycle()
    val wifi = if (source == WIFI) wifiVm.state.collectAsStateWithLifecycle().value else null

    val reading =
        if (source == WIFI) {
            wifiMeterReading(wifi?.connection?.rssi)
        } else {
            cellular?.sims?.firstOrNull { it.sim.subscriptionId == source }?.let(::simMeterReading)
        }

    // A short tap when the level changes lets you walk and feel the signal without looking at the screen.
    val haptics = rememberHaptics()
    var level by remember(source) { mutableStateOf<Quality?>(null) }
    val fraction = reading?.let { fractionOf(it.value, it.kind) }
    LaunchedEffect(fraction) {
        if (fraction == null) {
            level = null
        } else {
            val next = stableQuality(level, fraction)
            hapticFor(level, next)?.let(haptics::perform)
            level = next
        }
    }

    val series = remember(source) { RingSeries<Int>(SLOTS) }
    var samples by remember(source) { mutableStateOf(emptyList<Int?>()) }
    val latest by rememberUpdatedState(reading)
    LaunchedEffect(source) {
        while (true) {
            series.add(latest?.value)
            samples = series.snapshot()
            delay(1_000)
        }
    }

    Column(
        modifier = modifier.padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.meter_title), style = MaterialTheme.typography.headlineMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = source == WIFI, onClick = { source = WIFI }, label = { Text(stringResource(R.string.tab_wifi)) })
            cellular?.sims?.forEach { sim ->
                val label = "SIM ${sim.sim.slotIndex + 1} " + sim.sim.carrierName
                FilterChip(
                    selected = source == sim.sim.subscriptionId,
                    onClick = { source = sim.sim.subscriptionId },
                    label = { Text(label.trim()) },
                )
            }
        }
        val gauge: @Composable () -> Unit = {
            if (reading == null) {
                Text(stringResource(R.string.meter_no_signal), style = MaterialTheme.typography.bodyLarge)
            } else {
                Gauge(reading)
            }
        }
        val chart: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.meter_last_two_minutes), style = MaterialTheme.typography.labelLarge)
                LiveChart(samples, reading?.kind ?: if (source == WIFI) SignalKind.WifiRssi else SignalKind.Rsrp, SLOTS)
            }
        }
        // In landscape the gauge and the graph sit side by side, so nothing needs scrolling.
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { gauge() }
                Box(modifier = Modifier.weight(1f)) { chart() }
            }
        } else {
            gauge()
            chart()
        }
    }
}

@Composable
private fun Gauge(reading: MeterReading) {
    val fraction = fractionOf(reading.value, reading.kind)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.onSurface
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f).padding(bottom = 16.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(2f)) {
            val stroke = 28.dp.toPx()
            val inset = stroke / 2
            val diameter = size.width - stroke
            val topLeft = Offset(inset, inset)
            val arcSize = Size(diameter, diameter)
            drawArc(track, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (fraction > 0f) {
                drawArc(
                    outline,
                    180f,
                    180f * fraction,
                    false,
                    topLeft,
                    arcSize,
                    style = Stroke(stroke + 4.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    signalColor(fraction),
                    180f,
                    180f * fraction,
                    false,
                    topLeft,
                    arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${reading.value}", fontSize = 72.sp, style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center)
            Text("${reading.unit} · " + stringResource(qualityOf(fraction).label()), style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** A walking meter is easier to read when the screen stays on. The flag goes away when the meter leaves the screen. */
@Composable
private fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
