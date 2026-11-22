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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.devjugal.airscan.R
import com.devjugal.airscan.permissions.Grants
import com.devjugal.airscan.radio.Radios
import com.devjugal.airscan.radio.cellular.CellAccess
import com.devjugal.airscan.radio.cellular.CellId
import com.devjugal.airscan.radio.cellular.CellReading
import com.devjugal.airscan.radio.cellular.CellularState
import com.devjugal.airscan.radio.cellular.NrMode
import com.devjugal.airscan.radio.cellular.Rat
import com.devjugal.airscan.radio.cellular.SignalReading
import com.devjugal.airscan.radio.cellular.SimState
import com.devjugal.airscan.radio.cellular.bandLabel
import com.devjugal.airscan.radio.cellular.neighbourLabel
import com.devjugal.airscan.radio.cellular.techLabel

@Composable
fun CellularScreen(
    grants: Grants,
    contentPadding: PaddingValues,
    onOpenAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val viewModel: RadioViewModel<CellularState> =
        viewModel(key = "cellular", factory = viewModelFactory { initializer { RadioViewModel(Radios.cellular(context)) } })
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(grants) { viewModel.onGrants(grants) }

    var showAll by rememberSaveable { mutableStateOf(false) }
    val current = state
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        modifier = modifier,
        contentPadding = contentPadding.withMargin(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        fullItem { Text(stringResource(R.string.cellular_title), style = MaterialTheme.typography.headlineMedium) }
        if (current == null) {
            fullItem { Text(stringResource(R.string.cellular_waiting)) }
        } else {
            items(current.sims, key = { it.sim.subscriptionId }) { SimCard(it) }
            if (current.access == CellAccess.SignalOnly) {
                fullItem {
                    LockedDetails(
                        labels =
                            listOf(
                                "MCC-MNC",
                                "PCI",
                                "TAC",
                                "Cell ID",
                                "EARFCN / NRARFCN",
                                "Band",
                                "Bandwidth",
                                stringResource(R.string.cellular_neighbours),
                            ),
                        message = stringResource(R.string.cellular_signal_only),
                        actionLabel = stringResource(R.string.locked_open_access),
                        onAction = onOpenAccess,
                    )
                }
            }
            if (current.neighbours.isNotEmpty()) {
                fullItem { Text(stringResource(R.string.cellular_neighbours), style = MaterialTheme.typography.titleLarge) }
                val shown = if (showAll) current.neighbours else current.neighbours.take(NEIGHBOURS_SHOWN)
                items(shown) { NeighbourRow(it) }
                if (current.neighbours.size > NEIGHBOURS_SHOWN) {
                    fullItem {
                        TextButton(onClick = { showAll = !showAll }) {
                            Text(
                                if (showAll) {
                                    stringResource(R.string.cellular_show_fewer)
                                } else {
                                    stringResource(R.string.cellular_show_all, current.neighbours.size)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimCard(state: SimState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val name = state.sim.carrierName.ifBlank { stringResource(R.string.cellular_sim_unknown) }
            val data = if (state.sim.isDefaultData) " · " + stringResource(R.string.cellular_default_data) else ""
            Text("SIM ${state.sim.slotIndex + 1} · $name$data", style = MaterialTheme.typography.titleMedium)
            Text(techLabel(state.rat, state.nrMode), style = MaterialTheme.typography.headlineSmall)
            if (state.serving.isEmpty()) {
                state.signals.forEach { SignalRows(it) }
            }
            state.serving.forEach { cell ->
                Text(
                    stringResource(R.string.cellular_serving, techLabel(cell.id.rat, state.nrMode)),
                    style = MaterialTheme.typography.titleSmall,
                )
                SignalRows(cell.signal)
                IdentityRows(cell.id)
            }
        }
    }
}

@Composable
private fun SignalRows(signal: SignalReading) {
    if (signal.rat == Rat.Lte || signal.rat == Rat.Nr) {
        SignalRow("RSRP", signal.rsrp, SignalKind.Rsrp, "dBm")
        SignalRow("RSRQ", signal.rsrq, SignalKind.Rsrq, "dB")
        SignalRow("SINR", signal.sinr, SignalKind.Sinr, "dB")
        if (signal.rat == Rat.Lte) Row2("RSSI", dbm(signal.rssi, "dBm"))
        Row2("CQI", signal.cqi?.toString() ?: NA)
        Row2("Timing advance", signal.timingAdvance?.toString() ?: NA)
    } else {
        SignalRow("Signal", signal.dbm, SignalKind.Dbm, "dBm")
    }
}

@Composable
private fun SignalRow(
    label: String,
    value: Int?,
    kind: SignalKind,
    unit: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        if (value == null) Text(NA, style = MaterialTheme.typography.bodyMedium) else SignalValue("$value $unit", fractionOf(value, kind))
    }
}

@Composable
private fun IdentityRows(id: CellId) {
    Row2("MCC-MNC", listOfNotNull(id.mcc, id.mnc).joinToString("-").ifBlank { NA })
    Row2("PCI", id.pci?.toString() ?: NA)
    Row2("TAC", id.tac?.toString() ?: NA)
    Row2("Cell ID", id.cellId?.toString() ?: NA)
    id.enbId?.let { Row2("eNB / sector", "$it / ${id.sectorId}") }
    Row2(if (id.rat == Rat.Nr) "NRARFCN" else "EARFCN", id.channel?.toString() ?: NA)
    Row2("Band", bandLabel(id).ifBlank { NA })
    Row2("Bandwidth", id.bandwidthKhz?.let { "${it / 1000} MHz" } ?: NA)
}

@Composable
private fun NeighbourRow(cell: CellReading) {
    val id = cell.id
    val signal = cell.signal.rsrp ?: cell.signal.dbm
    Row2(neighbourLabel(id), dbm(signal, "dBm"))
}

@Composable
private fun Row2(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val NA = "n/a"

// Android can list a dozen or more weak cells. The strongest few matter most.
private const val NEIGHBOURS_SHOWN = 8

private fun dbm(
    value: Int?,
    unit: String,
) = value?.let { "$it $unit" } ?: NA
