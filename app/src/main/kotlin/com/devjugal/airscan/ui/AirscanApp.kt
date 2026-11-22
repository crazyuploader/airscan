package com.devjugal.airscan.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.devjugal.airscan.R
import com.devjugal.airscan.permissions.readGrants
import com.devjugal.airscan.settings.ThemeMode

private enum class Tab(
    val icon: ImageVector,
    @StringRes val label: Int,
) {
    Mobile(Icons.Filled.Phone, R.string.tab_mobile),
    Wifi(Icons.Filled.Share, R.string.tab_wifi),
    Meter(Icons.Filled.Place, R.string.tab_meter),
    History(Icons.AutoMirrored.Filled.List, R.string.tab_history),
    Access(Icons.Filled.Settings, R.string.tab_access),
}

/** Portrait uses a bottom bar. A wide window uses a side rail, so the screen keeps its height. */
@Composable
fun AirscanApp(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    hapticsEnabled: Boolean,
    onHaptics: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var grants by remember { mutableStateOf(readGrants(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { grants = readGrants(context) }
    var tab by rememberSaveable { mutableIntStateOf(Tab.Mobile.ordinal) }
    val wide = rememberIsWide()
    val openAccess = { tab = Tab.Access.ordinal }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        Tab.entries.forEach {
                            NavigationBarItem(
                                selected = tab == it.ordinal,
                                onClick = { tab = it.ordinal },
                                icon = { Icon(it.icon, contentDescription = null) },
                                label = { Text(stringResource(it.label)) },
                            )
                        }
                    }
                }
            },
        ) { scaffoldPadding ->
            Row(modifier = Modifier.fillMaxSize()) {
                if (wide) {
                    // The rail handles the start inset itself, so the content must not add it again.
                    NavigationRail {
                        Tab.entries.forEach {
                            NavigationRailItem(
                                selected = tab == it.ordinal,
                                onClick = { tab = it.ordinal },
                                icon = { Icon(it.icon, contentDescription = null) },
                                label = { Text(stringResource(it.label)) },
                            )
                        }
                    }
                }
                val direction = LocalLayoutDirection.current
                val padding =
                    PaddingValues(
                        start = if (wide) 0.dp else scaffoldPadding.calculateStartPadding(direction),
                        top = scaffoldPadding.calculateTopPadding(),
                        end = scaffoldPadding.calculateEndPadding(direction),
                        bottom = scaffoldPadding.calculateBottomPadding(),
                    )
                val content = Modifier.weight(1f).fillMaxSize()
                when (tab) {
                    Tab.Wifi.ordinal -> WifiScreen(grants, padding, openAccess, content)
                    Tab.Meter.ordinal -> MeterScreen(grants, padding, content)
                    Tab.History.ordinal -> HistoryScreen(grants, padding, content)
                    Tab.Access.ordinal -> PermissionScreen(padding, themeMode, onThemeMode, hapticsEnabled, onHaptics, content)
                    else -> CellularScreen(grants, padding, openAccess, content)
                }
            }
        }
        StatusBarProtection()
    }
}

/** A soft strip under the status bar, so scrolled content never runs into the clock and the icons. */
@Composable
private fun StatusBarProtection(color: Color = MaterialTheme.colorScheme.background) {
    val height = with(LocalDensity.current) { (WindowInsets.statusBars.getTop(this) * 1.2f).toDp() }
    Spacer(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
                .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.8f), Color.Transparent))),
    )
}
