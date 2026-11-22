package com.devjugal.airscan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devjugal.airscan.R
import com.devjugal.airscan.logging.LogFiles
import com.devjugal.airscan.logging.RecorderService
import com.devjugal.airscan.logging.SessionStore
import com.devjugal.airscan.logging.SignalSample
import com.devjugal.airscan.logging.logLabel
import com.devjugal.airscan.logging.statsOf
import com.devjugal.airscan.permissions.Grants
import java.io.File

private val RANGES = listOf(60, 300, 1_800, 3_600)

@Composable
fun HistoryScreen(
    grants: Grants,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val session by SessionStore.state.collectAsStateWithLifecycle()
    var source by rememberSaveable { mutableIntStateOf(SignalSample.WIFI_SOURCE) }
    var rangeIndex by rememberSaveable { mutableIntStateOf(1) }
    var files by remember { mutableStateOf(LogFiles.list(context)) }
    val notificationAsk =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            // The recording works without the notification, but Android then hides it from the shade.
            RecorderService.start(context)
        }
    // A finished recording adds files, so read the list again when the recording state changes.
    LaunchedEffect(session.recording, session.total / 10) { files = LogFiles.list(context) }

    val shown = session.samples.takeLast(RANGES[rangeIndex])
    val simIds =
        session.samples
            .lastOrNull()
            ?.sims
            .orEmpty()
    val values = shown.map { it.valueFor(source) }
    val kind =
        when {
            source == SignalSample.WIFI_SOURCE -> SignalKind.WifiRssi

            shown
                .lastOrNull()
                ?.sims
                ?.firstOrNull { it.subscriptionId == source }
                ?.rsrp != null -> SignalKind.Rsrp

            else -> SignalKind.Dbm
        }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        modifier = modifier,
        contentPadding = contentPadding.withMargin(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        fullItem { Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium) }
        fullItem {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (session.recording) {
                    Text(stringResource(R.string.history_recording, session.total), style = MaterialTheme.typography.titleMedium)
                    val fix = session.samples.lastOrNull()?.fix
                    Text(
                        if (fix ==
                            null
                        ) {
                            stringResource(R.string.history_gps_waiting)
                        } else {
                            stringResource(
                                R.string.history_gps_fix,
                                fix.accuracyM ?: 0f,
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = {
                            haptics.perform(HapticKind.Off)
                            RecorderService.stop(context)
                        },
                    ) { Text(stringResource(R.string.history_stop)) }
                } else {
                    Text(stringResource(R.string.history_intro), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        enabled = grants.fineLocation,
                        onClick = {
                            haptics.perform(HapticKind.Confirm)
                            // Only Android 13 and newer has the notification permission.
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationAsk.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                RecorderService.start(context)
                            }
                        },
                    ) { Text(stringResource(R.string.history_start)) }
                    if (!grants.fineLocation) {
                        Text(stringResource(R.string.history_needs_location), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (session.samples.isNotEmpty()) {
            fullItem {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = source == SignalSample.WIFI_SOURCE,
                            onClick = { source = SignalSample.WIFI_SOURCE },
                            label = { Text(stringResource(R.string.tab_wifi)) },
                        )
                        simIds.forEach { sim ->
                            FilterChip(
                                selected = source == sim.subscriptionId,
                                onClick = { source = sim.subscriptionId },
                                label = { Text("SIM ${sim.slot + 1}") },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RANGES.forEachIndexed { index, seconds ->
                            FilterChip(
                                selected = rangeIndex == index,
                                onClick = { rangeIndex = index },
                                label = { Text(if (seconds < 3_600) "${seconds / 60} min" else "1 h") },
                            )
                        }
                    }
                    LiveChart(values, kind, slots = values.size.coerceAtLeast(2))
                    statsOf(values)?.let {
                        Text(
                            stringResource(R.string.history_stats, it.min, it.avg, it.max),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        fullItem { Text(stringResource(R.string.history_files), style = MaterialTheme.typography.titleLarge) }
        if (files.isEmpty()) fullItem { Text(stringResource(R.string.history_no_files)) }
        items(files, key = { it.name }) { file ->
            FileRow(file, onShare = { LogFiles.share(context, file) }, onDelete = {
                file.delete()
                files = LogFiles.list(context)
            })
        }
    }
}

@Composable
private fun FileRow(
    file: File,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(logLabel(file.name), style = MaterialTheme.typography.titleMedium)
            Text(Formatter.formatShortFileSize(LocalContext.current, file.length()), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShare) { Text(stringResource(R.string.history_share)) }
                OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.history_delete)) }
            }
        }
    }
}
