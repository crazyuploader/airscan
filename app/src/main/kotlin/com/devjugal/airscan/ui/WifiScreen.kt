package com.devjugal.airscan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.devjugal.airscan.R
import com.devjugal.airscan.permissions.Grants
import com.devjugal.airscan.radio.Radios
import com.devjugal.airscan.radio.wifi.AccessPoint
import com.devjugal.airscan.radio.wifi.WifiAccess
import com.devjugal.airscan.radio.wifi.WifiBand
import com.devjugal.airscan.radio.wifi.WifiConnection
import com.devjugal.airscan.radio.wifi.WifiState
import com.devjugal.airscan.radio.wifi.channelOccupancy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScreen(
    grants: Grants,
    contentPadding: PaddingValues,
    onOpenAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val haptics = rememberHaptics()
    val viewModel: WifiViewModel = viewModel(factory = viewModelFactory { initializer { WifiViewModel(Radios.wifi(context)) } })
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(grants) { viewModel.onGrants(grants) }
    val bands = WifiBand.entries
    // Start on the band of the connected network, until the user picks another.
    var chosenBand by rememberSaveable { mutableIntStateOf(-1) }
    val bandIndex = if (chosenBand >= 0) chosenBand else state?.connection?.band?.let { bands.indexOf(it) } ?: 0

    val current = state
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        modifier = modifier,
        contentPadding = contentPadding.withMargin(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        fullItem { Text(stringResource(R.string.wifi_title), style = MaterialTheme.typography.headlineMedium) }
        if (current == null) {
            fullItem { Text(stringResource(R.string.wifi_waiting)) }
            return@LazyVerticalGrid
        }
        item { ConnectionCard(current.connection, limited = current.access == WifiAccess.Limited) }
        item {
            ScanRow(current) {
                haptics.perform(HapticKind.Confirm)
                viewModel.scanNow()
            }
        }
        if (current.access == WifiAccess.Limited) {
            fullItem {
                LockedDetails(
                    labels = listOf("SSID", "BSSID", "Channel width", "Channel graph", stringResource(R.string.wifi_networks)),
                    message = stringResource(R.string.wifi_limited),
                    actionLabel = stringResource(R.string.locked_open_access),
                    onAction = onOpenAccess,
                )
            }
        } else {
            fullItem {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    bands.forEachIndexed { index, band ->
                        SegmentedButton(
                            selected = index == bandIndex,
                            onClick = { chosenBand = index },
                            shape = SegmentedButtonDefaults.itemShape(index, bands.size),
                        ) { Text(band.label) }
                    }
                }
            }
            fullItem {
                val band = bands[bandIndex]
                ChannelChart(band, current.accessPoints, current.connection?.bssid)
                val busiest = channelOccupancy(band, current.accessPoints).maxByOrNull { it.count }
                if (busiest != null && busiest.count > 1) {
                    Text(
                        stringResource(R.string.wifi_busiest, busiest.channel) + " " +
                            pluralStringResource(R.plurals.wifi_overlapping, busiest.count, busiest.count),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (current.accessPoints.isNotEmpty()) {
            fullItem { Text(stringResource(R.string.wifi_networks), style = MaterialTheme.typography.titleLarge) }
            items(current.accessPoints, key = { it.bssid }) { AccessPointRow(it) }
        }
    }
}

@Composable
private fun ConnectionCard(
    connection: WifiConnection?,
    limited: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (connection == null) {
                Text(stringResource(R.string.wifi_not_connected), style = MaterialTheme.typography.titleMedium)
            } else {
                Text(
                    connection.ssid ?: stringResource(if (limited) R.string.wifi_name_redacted else R.string.wifi_name_hidden),
                    style = MaterialTheme.typography.headlineSmall,
                )
                SignalValue("${connection.rssi} dBm", fractionOf(connection.rssi, SignalKind.WifiRssi))
                Pair2("BSSID", connection.bssid ?: NA)
                Pair2(stringResource(R.string.wifi_link_standard), connection.standard.label)
                Pair2("Band", connection.band?.label ?: NA)
                Pair2("Channel", connection.channel?.toString() ?: NA)
                Pair2("Frequency", "${connection.frequencyMhz} MHz")
                Pair2("Channel width", connection.widthMhz?.let { "$it MHz" } ?: NA)
                Pair2("Link speed", connection.linkSpeedMbps?.let { "$it Mbps" } ?: NA)
                Pair2("Receive / send", "${connection.rxMbps ?: NA} / ${connection.txMbps ?: NA} Mbps")
            }
        }
    }
}

@Composable
private fun ScanRow(
    state: WifiState,
    onScan: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val age = state.scanAgeMs
        Text(
            if (age == null) stringResource(R.string.wifi_no_scan) else stringResource(R.string.wifi_scan_age, age / 1000),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onScan, enabled = state.access == WifiAccess.Full && state.nextScanInMs == 0L) {
                Text(stringResource(R.string.wifi_scan_now))
            }
            Text(
                if (state.nextScanInMs > 0) {
                    stringResource(R.string.wifi_scan_wait, (state.nextScanInMs + 999) / 1000)
                } else {
                    stringResource(R.string.wifi_scans_left, state.scansLeft)
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AccessPointRow(ap: AccessPoint) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(ap.ssid.ifBlank { stringResource(R.string.wifi_name_hidden) }, style = MaterialTheme.typography.titleMedium)
            SignalValue("${ap.rssi} dBm", fractionOf(ap.rssi, SignalKind.WifiRssi))
            Text(
                "${ap.band?.label ?: "?"} · ch ${ap.channel ?: "?"} · ${ap.widthMhz} MHz · ${ap.standard.label} · ${ap.security.label}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(ap.bssid, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Pair2(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val NA = "n/a"
