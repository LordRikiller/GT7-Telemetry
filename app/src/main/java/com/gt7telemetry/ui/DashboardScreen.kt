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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    onOpenConnection: () -> Unit,
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
            onOpenSettings = onOpenSettings,
            onOpenLogger = onOpenLogger,
            onOpenEngineer = onOpenEngineer,
            onOpenSetup = onOpenSetup,
        )
        Spacer(Modifier.height(10.dp))
        Box(Modifier.weight(1f)) {
            val f = frame
            if (!status.everReceived || f == null) {
                HomeContent(
                    viewModel = viewModel,
                    ps5Ip = ps5Ip,
                    status = status,
                    onOpenLogger = onOpenLogger,
                    onOpenEngineer = onOpenEngineer,
                    onOpenSetup = onOpenSetup,
                    onOpenConnection = onOpenConnection,
                )
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
    onOpenSettings: () -> Unit,
    onOpenLogger: () -> Unit,
    onOpenEngineer: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    // Four inline pills never fit next to a car name on a phone-width bar —
    // below this width the actions collapse into one ☰ menu. (Units moved
    // to Settings; they don't need dash-bar real estate.)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 520.dp
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

// ---------------------------------------------------------------------------
// Home — what greets you when no telemetry is flowing. Instead of a setup
// form, it picks up where you left off: your last session, one tap from an
// AI engineer debrief, with the connection tucked into a status strip.
// ---------------------------------------------------------------------------

@Composable
private fun HomeContent(
    viewModel: DashboardViewModel,
    ps5Ip: String,
    status: com.gt7telemetry.Status,
    onOpenLogger: () -> Unit,
    onOpenEngineer: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenConnection: () -> Unit,
) {
    val history by viewModel.lapHistory.collectAsStateWithLifecycle()
    val catalogRevision by viewModel.catalogRevision.collectAsStateWithLifecycle()
    val trackRevision by viewModel.trackRevision.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val newest = history.firstOrNull()
        if (newest == null) {
            // First run — nothing recorded yet.
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Palette.HeroBrush).padding(20.dp)) {
                Column {
                    Text("WELCOME TO THE PIT WALL", color = Palette.Blue, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Point the app at your PS5 and drive", color = Palette.Paint,
                        fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Every lap you drive is recorded automatically — live instruments while " +
                            "you race, full data-logger traces after, and an AI race engineer to " +
                            "turn it all into setup changes.",
                        color = Palette.Ink, fontSize = 13.sp, lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    HomeCta("SET UP CONNECTION ›", accent = true, onClick = onOpenConnection)
                }
            }
        } else {
            // The last session, ready to pick back up.
            val session = history.filter {
                it.carOrdinal == newest.carOrdinal &&
                    kotlin.math.abs(it.recordedAtMs - newest.recordedAtMs) < 3 * 3600_000L
            }
            val bestMeta = session.filter { it.lapTimeS > 0 }.minByOrNull { it.lapTimeS }
            val carName = (if (catalogRevision > 0) CarCatalog.lookup(newest.carOrdinal)?.name else null)
                ?: newest.carOrdinal.takeIf { it != 0 }?.let { "Car #$it" } ?: "Unknown car"
            var trackName by remember(newest.file.name) { mutableStateOf<String?>(null) }
            LaunchedEffect(newest.file.name, trackRevision) {
                viewModel.loadStoredLap(newest) { lap ->
                    trackName = lap?.let { com.gt7telemetry.track.TrackStore.identify(it) }
                }
            }
            val dateFmt = remember { java.text.SimpleDateFormat("EEE d MMM · HH:mm", java.util.Locale.getDefault()) }

            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Palette.HeroBrush).padding(20.dp)) {
                Column {
                    Text("LAST SESSION", color = Palette.Blue, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(carName, color = Palette.Paint, fontSize = 21.sp,
                        fontWeight = FontWeight.Bold, lineHeight = 26.sp)
                    Text(
                        listOfNotNull(
                            trackName,
                            dateFmt.format(java.util.Date(newest.recordedAtMs)),
                            newest.tyres.takeIf { it.isNotBlank() },
                        ).joinToString("  ·  "),
                        color = Palette.InkDim, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row {
                        HomeStat("LAPS", session.size.toString())
                        Spacer(Modifier.width(22.dp))
                        HomeStat("BEST", bestMeta?.let { Fmt.lap(it.lapTimeS) } ?: "—", Palette.Amber)
                        Spacer(Modifier.width(22.dp))
                        HomeStat("TOP", "${Fmt.n0(session.maxOf { it.maxSpeedKmh })} km/h")
                    }
                    Spacer(Modifier.height(16.dp))
                    Row {
                        HomeCta("AI RACE ENGINEER ›", accent = true) {
                            viewModel.openEngineerWithStored(newest) { onOpenEngineer() }
                        }
                        Spacer(Modifier.width(8.dp))
                        HomeCta("LAP HISTORY ›", onClick = onOpenLogger)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                Pill("TUNING / SETUP ›", onClick = onOpenSetup)
            }
        }
        Spacer(Modifier.height(10.dp))

        // Connection status strip — the details live on their own page now.
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val (txt, col) = when {
                    status.live -> "Connected — receiving telemetry" to Palette.Good
                    status.everReceived -> "Connection lost — is GT7 running?" to Palette.Hot
                    ps5Ip.isBlank() -> "No PS5 configured yet" to Palette.InkMute
                    else -> "Waiting for $ps5Ip — start GT7 and drive" to Palette.InkDim
                }
                Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(col))
                Spacer(Modifier.width(10.dp))
                Text(txt, color = Palette.InkDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Pill("CONNECTION ›", onClick = onOpenConnection)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HomeStat(label: String, value: String, color: Color = Palette.Paint) {
    Column {
        Text(value, color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace)
        Text(label, color = Palette.InkMute, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp)
    }
}

@Composable
private fun HomeCta(text: String, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (accent) Palette.Amber else Palette.Carbon2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text, color = if (accent) Palette.AmberInk else Palette.Ink, fontSize = 12.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    }
}
