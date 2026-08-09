package com.gt7telemetry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gt7telemetry.TelemetryService
import com.gt7telemetry.dash.DashLayout
import com.gt7telemetry.settings.DashMode
import com.gt7telemetry.update.UpdateChecker
import com.gt7telemetry.update.UpdateInstaller
import com.gt7telemetry.update.UpdateState

@Composable
fun SettingsScreen(viewModel: DashboardViewModel, onBack: () -> Unit, onOpenConnection: () -> Unit) {
    val ps5Ip by viewModel.ps5Ip.collectAsStateWithLifecycle()
    val dashMode by viewModel.dashMode.collectAsStateWithLifecycle()
    val manualLayout by viewModel.manualLayout.collectAsStateWithLifecycle()
    val useMph by viewModel.useMph.collectAsStateWithLifecycle()
    val useFahrenheit by viewModel.useFahrenheit.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹  Back") }
            Spacer(Modifier.width(4.dp))
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        // ---- Connection ----
        SectionLabel("Connection")
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .clickable(onClick = onOpenConnection).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("PlayStation connection", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (ps5Ip.isBlank()) "Not set up yet — tap to configure"
                    else "PS5 at $ps5Ip · telemetry on UDP ${TelemetryService.SEND_PORT}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionDivider()

        // ---- Dashboard ----
        SectionLabel("Dashboard")
        Text(
            "Choose the instrument layout. Auto picks a layout to match the car you're driving; " +
                "Manual keeps the one you choose.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModePill("Auto", "match the car", dashMode == DashMode.AUTO, Modifier.weight(1f)) { viewModel.setDashMode(DashMode.AUTO) }
            ModePill("Manual", "always this one", dashMode == DashMode.MANUAL, Modifier.weight(1f)) { viewModel.setDashMode(DashMode.MANUAL) }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            if (dashMode == DashMode.MANUAL) "Layout" else "Layout (used when Auto doesn't recognise the car)",
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Column {
            for (dl in DashLayout.selectable) {
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = manualLayout == dl, onClick = { viewModel.setManualLayout(dl) })
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = manualLayout == dl, onClick = { viewModel.setManualLayout(dl) })
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(dl.label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            dl.family.name.lowercase().replace('_', ' ') +
                                (dl.manufacturer?.let { " · auto for $it" } ?: ""),
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        SectionDivider()

        // ---- Units ----
        SectionLabel("Units")
        ToggleRow("Speed", if (useMph) "mph" else "km/h", useMph) { viewModel.setUseMph(it) }
        ToggleRow("Temperature", if (useFahrenheit) "°F" else "°C", useFahrenheit) { viewModel.setUseFahrenheit(it) }

        SectionDivider()

        // ---- Updates ----
        SectionLabel("App updates")
        Text(updateSubtitle(updateState, version), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        val downloading = updateState as? UpdateState.Downloading
        val available = updateState as? UpdateState.Available
        val ready = updateState as? UpdateState.ReadyToInstall
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::checkForUpdates, enabled = downloading == null) { Text("Check for updates") }
            if (available != null) {
                Button(onClick = viewModel::downloadUpdate) { Text("Download v${available.manifest.versionName}") }
            }
            if (ready != null) {
                // Retry entry point — needed after granting "install unknown apps",
                // since the automatic launch on download-complete won't re-fire.
                Button(onClick = { UpdateInstaller.install(context, ready.file) }) { Text("Install v${ready.manifest.versionName}") }
            }
        }
        if (ready != null) {
            Text(
                "If the installer didn't appear, allow \"install unknown apps\" for GT7 Telemetry when prompted, then tap Install.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
            )
        }

        SectionDivider()

        // ---- About ----
        SectionLabel("About")
        Text("GT7 Telemetry", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text("Version $version", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Standalone live telemetry for Gran Turismo 7. Not affiliated with Sony / Polyphony Digital.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 30.dp)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(Modifier.padding(vertical = 18.dp), color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun ModePill(title: String, subtitle: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(bg)
            .then(if (selected) Modifier.border(1.dp, borderColor, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick).padding(12.dp)
    ) {
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun updateSubtitle(state: UpdateState, version: String): String = when (state) {
    UpdateState.Idle -> "Version $version"
    UpdateState.Checking -> "Checking…"
    UpdateState.UpToDate -> "Up to date (v$version)"
    is UpdateState.Available -> "Update available: v${state.manifest.versionName}"
    is UpdateState.Downloading -> "Downloading… ${state.percent}%"
    is UpdateState.ReadyToInstall -> "Opening installer…"
    is UpdateState.Failed ->
        if (!UpdateChecker.isConfigured) "Updates aren't set up yet" else "Check failed: ${state.message}"
}
