package com.gt7telemetry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gt7telemetry.TelemetryRepository
import com.gt7telemetry.car.CarCatalog
import com.gt7telemetry.dash.ClusterHost
import com.gt7telemetry.dash.ClusterTheme
import com.gt7telemetry.dash.DashLayout
import com.gt7telemetry.settings.DashMode
import com.gt7telemetry.update.UpdateState

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenSettings: () -> Unit,
    onOpenLogger: () -> Unit,
    onOpenEngineer: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val frame by TelemetryRepository.frame.collectAsStateWithLifecycle()
    val status by TelemetryRepository.status.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val dashMode by viewModel.dashMode.collectAsStateWithLifecycle()
    val manualLayout by viewModel.manualLayout.collectAsStateWithLifecycle()
    val useMph by viewModel.useMph.collectAsStateWithLifecycle()
    val useFahrenheit by viewModel.useFahrenheit.collectAsStateWithLifecycle()
    val catalogRevision by viewModel.catalogRevision.collectAsStateWithLifecycle()
    val ps5Ip by viewModel.ps5Ip.collectAsStateWithLifecycle()

    val ordinal = frame?.carOrdinal
    val carInfo = if (catalogRevision > 0) CarCatalog.lookup(ordinal) else null
    val carName = carInfo?.name ?: ordinal?.takeIf { it != 0 }?.let { "Car #$it" }

    val autoLayout = DashLayout.forManufacturer(carInfo?.manufacturer) ?: DashLayout.DEFAULT
    val layout = if (dashMode == DashMode.AUTO) autoLayout else manualLayout
    val theme = layout.theme

    Column(Modifier.fillMaxSize().background(theme.bg).systemBarsPadding().padding(12.dp)) {
        TopBar(
            theme = theme,
            status = status,
            raceOn = frame?.raceOn == true,
            inMenus = frame?.onTrack == false,
            pkt = status.packetsPerSec,
            carName = carName,
            layoutLabel = if (dashMode == DashMode.AUTO) "${layout.label} · auto" else layout.label,
            useMph = useMph,
            useFahrenheit = useFahrenheit,
            onToggleSpeed = { viewModel.setUseMph(!useMph) },
            onToggleTemp = { viewModel.setUseFahrenheit(!useFahrenheit) },
            onOpenSettings = onOpenSettings,
            onOpenLogger = onOpenLogger,
            onOpenEngineer = onOpenEngineer,
            onOpenSetup = onOpenSetup,
        )
        Spacer(Modifier.height(10.dp))
        Box(Modifier.weight(1f)) {
            val f = frame
            if (!status.everReceived || f == null) {
                SetupCard(ps5Ip, theme, onSaveIp = viewModel::setPs5Ip)
            } else {
                // In menus/replays GT7 keeps streaming the LAST on-track
                // values — without this the dash impersonates a live
                // instrument frozen mid-corner. Dim it and say why.
                val idle = status.live && !f.raceOn
                Box(Modifier.fillMaxSize().alpha(if (idle) 0.25f else 1f)) {
                    ClusterHost(f, layout, useMph, useFahrenheit)
                }
                if (idle) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                .background(theme.panel.copy(alpha = 0.92f))
                                .padding(horizontal = 22.dp, vertical = 14.dp),
                        ) {
                            val title = when {
                                f.paused && f.onTrack -> "PAUSED"
                                !f.onTrack -> "IN MENUS"
                                else -> "LOADING…"
                            }
                            Text(title, color = theme.accent, fontSize = 18.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (!f.onTrack) "Last session's data shown dimmed — the dash goes live when you're on track"
                                else "The dash resumes when the game does",
                                color = theme.ink2, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    // Launch-time "update available" prompt (manual update controls live in Settings).
    (updateState as? UpdateState.Available)?.let { available ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUpdatePrompt,
            title = { Text("Update available") },
            text = {
                Text(buildString {
                    append("Version ${available.manifest.versionName} is available.")
                    if (available.manifest.notes.isNotBlank()) { append("\n\n"); append(available.manifest.notes) }
                })
            },
            confirmButton = { TextButton(onClick = viewModel::downloadUpdate) { Text("Update") } },
            dismissButton = { TextButton(onClick = viewModel::dismissUpdatePrompt) { Text("Later") } },
        )
    }
}

@Composable
private fun TopBar(
    theme: ClusterTheme,
    status: com.gt7telemetry.Status,
    raceOn: Boolean,
    inMenus: Boolean,
    pkt: Int,
    carName: String?,
    layoutLabel: String,
    useMph: Boolean,
    useFahrenheit: Boolean,
    onToggleSpeed: () -> Unit,
    onToggleTemp: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogger: () -> Unit,
    onOpenEngineer: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    // Six inline pills never fit next to a car name on a phone-width bar —
    // below this width the actions collapse into one ☰ menu.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 620.dp
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val (txt, col) = when {
                status.live && raceOn -> "LIVE" to theme.good
                status.live && inMenus -> "IN MENUS" to theme.mute
                status.live -> "PAUSED" to theme.warn
                status.everReceived -> "NO PACKETS" to theme.redline
                else -> "WAITING…" to theme.mute
            }
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(col.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(txt, color = col, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            if (!compact) {
                Spacer(Modifier.width(8.dp))
                Text("$pkt pkt/s", color = theme.ink2, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.weight(1f))
            if (carName != null) {
                // Never let a long car name shove the buttons off-screen.
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(3f, fill = false)) {
                    Text(carName, color = theme.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(layoutLabel, color = theme.mute, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(10.dp))
            }
            if (compact) {
                var open by remember { mutableStateOf(false) }
                Box {
                    Toggle("☰", theme) { open = true }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        MenuEntry("Data logger") { open = false; onOpenLogger() }
                        MenuEntry("Tuning / setup") { open = false; onOpenSetup() }
                        MenuEntry("AI race engineer") { open = false; onOpenEngineer() }
                        MenuEntry("Speed: ${if (useMph) "mph" else "km/h"}  ›  ${if (useMph) "km/h" else "mph"}") { onToggleSpeed() }
                        MenuEntry("Temps: ${if (useFahrenheit) "°F" else "°C"}  ›  ${if (useFahrenheit) "°C" else "°F"}") { onToggleTemp() }
                        MenuEntry("Settings") { open = false; onOpenSettings() }
                    }
                }
            } else {
                Toggle("LOG", theme, onOpenLogger)
                Spacer(Modifier.width(6.dp))
                Toggle("TUNE", theme, onOpenSetup)
                Spacer(Modifier.width(6.dp))
                Toggle("AI", theme, onOpenEngineer)
                Spacer(Modifier.width(6.dp))
                Toggle(if (useMph) "mph" else "km/h", theme, onToggleSpeed)
                Spacer(Modifier.width(6.dp))
                Toggle(if (useFahrenheit) "°F" else "°C", theme, onToggleTemp)
                Spacer(Modifier.width(6.dp))
                Toggle("⚙", theme, onOpenSettings)
            }
        }
    }
}

@Composable
private fun MenuEntry(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 14.sp) },
        onClick = onClick,
    )
}

@Composable
private fun Toggle(label: String, theme: ClusterTheme, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(theme.panel).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Text(label, color = theme.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun SetupCard(ps5Ip: String, theme: ClusterTheme, onSaveIp: (String) -> Unit) {
    var draft by remember(ps5Ip) { mutableStateOf(ps5Ip) }

    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(theme.panel)
        .padding(16.dp)) {
        Column {
            Text("NO TELEMETRY YET — POINT THIS APP AT YOUR PS5", color = theme.mute, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            SetupRow("1.", "On the PS5: Settings → Network → Connection Status", theme)
            SetupRow("2.", "Note the console's IPv4 address and enter it below", theme)
            SetupRow("3.", "Start Gran Turismo 7 and drive", theme)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("PS5 IP address") },
                    placeholder = { Text("192.168.1.20") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.width(10.dp))
                Button(onClick = { onSaveIp(draft) }, enabled = draft.isNotBlank()) { Text("Save") }
            }
            if (ps5Ip.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Requesting telemetry from ", color = theme.ink2, fontSize = 13.sp)
                    Text(ps5Ip, color = theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "The PS5 and this phone must be on the same Wi-Fi. GT7 streams automatically — " +
                    "there's nothing to enable in-game. This card disappears the moment the first packet lands.",
                color = theme.ink2, fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun SetupRow(num: String, text: String, theme: ClusterTheme, highlight: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(num, color = theme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
        Text(text, color = theme.ink, fontSize = 14.sp)
        if (highlight != null) {
            Spacer(Modifier.width(6.dp))
            Text(highlight, color = theme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}
