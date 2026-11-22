package com.devjugal.airscan.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.devjugal.airscan.R
import com.devjugal.airscan.permissions.Grants
import com.devjugal.airscan.permissions.readGrants
import com.devjugal.airscan.settings.ThemeMode

/** Shows what Airscan may read and asks for what is missing. It reads the grants again on every resume. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionScreen(
    contentPadding: PaddingValues,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    hapticsEnabled: Boolean,
    onHaptics: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var grants by remember { mutableStateOf(readGrants(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            grants = readGrants(context)
        }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { grants = readGrants(context) }

    // After a request, Android shows no dialog again for a permission when it gives no rationale. Block only when all of them are in that state.
    val blocked =
        asked &&
            grants.permissionsToRequest().let { list ->
                list.isNotEmpty() && list.all { activity?.shouldShowRequestPermissionRationale(it) == false }
            }

    Box(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(contentPadding).padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.access_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.access_intro), style = MaterialTheme.typography.bodyLarge)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccessRow(R.string.access_precise_location, R.string.access_precise_location_why, grants.fineLocation)
                    AccessRow(R.string.access_phone_state, R.string.access_phone_state_why, grants.phoneState)
                    AccessRow(R.string.access_location_switch, R.string.access_location_switch_why, grants.locationEnabled)
                }
            }

            if (grants.onlyApproximate) {
                Text(stringResource(R.string.access_approximate), style = MaterialTheme.typography.bodyMedium)
            }
            if (!grants.complete) {
                Text(stringResource(R.string.access_without), style = MaterialTheme.typography.bodyMedium)
            }

            if (!grants.fineLocation || !grants.phoneState) {
                if (blocked) {
                    Text(stringResource(R.string.access_blocked), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { context.startActivity(appSettingsIntent(context.packageName)) }) {
                        Text(stringResource(R.string.access_open_app_settings))
                    }
                } else {
                    Button(onClick = {
                        asked = true
                        launcher.launch(grants.permissionsToRequest().toTypedArray())
                    }) {
                        Text(stringResource(R.string.access_allow))
                    }
                }
            }
            if (grants.fineLocation && !grants.locationEnabled) {
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                    Text(stringResource(R.string.access_open_location_settings))
                }
            }
            if (grants.complete) {
                Text(stringResource(R.string.access_all_set), style = MaterialTheme.typography.bodyLarge)
            }

            Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = mode == themeMode,
                        onClick = { onThemeMode(mode) },
                        label = { Text(stringResource(mode.label())) },
                    )
                }
            }
            Text(stringResource(R.string.appearance_black_hint), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.haptics_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.haptics_hint), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = hapticsEnabled, onCheckedChange = onHaptics)
            }
        }
    }
}

@StringRes
private fun ThemeMode.label(): Int =
    when (this) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
        ThemeMode.Black -> R.string.theme_black
    }

@Composable
private fun AccessRow(
    @StringRes title: Int,
    @StringRes why: Int,
    allowed: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(why), style = MaterialTheme.typography.bodyMedium)
        }
        // Text, not only colour, so the state is clear without telling red from green.
        Text(
            stringResource(if (allowed) R.string.access_state_on else R.string.access_state_needed),
            style = MaterialTheme.typography.labelLarge,
            color = if (allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

private fun appSettingsIntent(packageName: String) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
