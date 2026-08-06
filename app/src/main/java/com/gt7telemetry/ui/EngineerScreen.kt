package com.gt7telemetry.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gt7telemetry.logger.LapRecorder
import com.gt7telemetry.settings.EngineerProvider

/**
 * The AI race engineer. Two ways to use the same telemetry briefing:
 *
 *  A — share it to any AI app the user already pays for (ChatGPT, Claude,
 *      Gemini, Copilot, …) via the system share sheet: zero API cost.
 *  B — the built-in engineer: the user's own API key makes exactly one
 *      bounded request per press. No loop, capped output — cost by
 *      construction is a few cents (Claude) or free tier (Gemini).
 */
@Composable
fun EngineerScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onOpenLogger: () -> Unit,
) {
    val laps by LapRecorder.laps.collectAsStateWithLifecycle()
    val provider by viewModel.engineerProvider.collectAsStateWithLifecycle()
    val apiKey by viewModel.engineerApiKey.collectAsStateWithLifecycle()
    val model by viewModel.engineerModel.collectAsStateWithLifecycle()
    val setupNotes by viewModel.setupNotes.collectAsStateWithLifecycle()
    val state by viewModel.engineerState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val best = laps.filter { it.lapTimeS > 0 }.minByOrNull { it.lapTimeS }
    val ready = laps.isNotEmpty()

    Column(
        Modifier.fillMaxSize().background(Palette.Asphalt).systemBarsPadding().padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill("‹ DASH", onClick = onBack)
            Spacer(Modifier.width(10.dp))
            Text("AI RACE ENGINEER", color = Palette.Paint, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Pill("DATA LOGGER ›", onClick = onOpenLogger)
        }
        Spacer(Modifier.height(10.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            // ---- Session state --------------------------------------------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Label("SESSION")
                    Spacer(Modifier.height(6.dp))
                    if (!ready) {
                        Text("No laps recorded yet — complete a few laps first. The logger " +
                            "records automatically while you drive.",
                            color = Palette.InkDim, fontSize = 13.sp)
                    } else {
                        Text(
                            "${laps.size} lap${if (laps.size == 1) "" else "s"} recorded" +
                                (best?.let { " · best ${Fmt.lap(it.lapTimeS)} (lap ${it.lapNumber})" } ?: ""),
                            color = Palette.Ink, fontSize = 13.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // ---- Setup notes ----------------------------------------------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Label("YOUR CURRENT SETUP (OPTIONAL)")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "GT7 doesn't broadcast the settings sheet, so describe it here — " +
                            "e.g. \"ride height 90/95 mm, ARB 5/6, comp 60/60 %, camber 3.0/2.0, " +
                            "diff accel 30\". The engineer's advice gets much more specific.",
                        color = Palette.InkMute, fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    var draft by remember(setupNotes) { mutableStateOf(setupNotes) }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        placeholder = { Text("Stock setup / describe your tune…") },
                    )
                    if (draft != setupNotes) {
                        Spacer(Modifier.height(6.dp))
                        Pill("SAVE NOTES", accent = true) { viewModel.setSetupNotes(draft) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // ---- Path A: share to any AI ----------------------------------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Label("USE THE AI YOU ALREADY PAY FOR — FREE")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Sends the full briefing (car, setup notes, lap table, best-lap trace) " +
                            "to any app via the share sheet — paste it into ChatGPT, Claude, " +
                            "Gemini or Copilot with your existing subscription.",
                        color = Palette.InkDim, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        BigButton("SHARE BRIEFING", enabled = ready) {
                            val text = viewModel.buildBriefing(laps)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "GT7 race engineer briefing")
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, "Send briefing to…"))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // ---- Path B: built-in engineer --------------------------------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Label("BUILT-IN ENGINEER — YOUR OWN API KEY")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "One analysis = one capped request (≈15k tokens in, 1.5k out): a few " +
                            "cents on Claude, free within Gemini's free tier. There is no " +
                            "background usage and no loop — it never calls the API except when " +
                            "you press Analyse. The key stays on this phone.",
                        color = Palette.InkDim, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        EngineerProvider.entries.forEach { p ->
                            val selected = p == provider
                            Box(
                                Modifier.padding(end = 6.dp).clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) Palette.Amber.copy(alpha = 0.18f) else Palette.Carbon2)
                                    .clickable { viewModel.setEngineerProvider(p) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(p.label, color = if (selected) Palette.Amber else Palette.Ink,
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    var keyDraft by remember(provider, apiKey) { mutableStateOf(apiKey) }
                    var showKey by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = keyDraft,
                        onValueChange = { keyDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("API key — ${provider.keyHint}") },
                        visualTransformation =
                            if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Text(if (showKey) "HIDE" else "SHOW", color = Palette.InkDim, fontSize = 11.sp,
                                modifier = Modifier.clickable { showKey = !showKey }.padding(8.dp))
                        },
                    )
                    if (keyDraft != apiKey) {
                        Spacer(Modifier.height(6.dp))
                        Pill("SAVE KEY", accent = true) { viewModel.setEngineerApiKey(keyDraft) }
                    }
                    Spacer(Modifier.height(8.dp))
                    var modelDraft by remember(provider, model) { mutableStateOf(model) }
                    OutlinedTextField(
                        value = modelDraft,
                        onValueChange = { modelDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Model (blank = ${provider.defaultModel})") },
                    )
                    if (modelDraft != model) {
                        Spacer(Modifier.height(6.dp))
                        Pill("SAVE MODEL", accent = true) { viewModel.setEngineerModel(modelDraft) }
                    }
                    Spacer(Modifier.height(12.dp))
                    BigButton(
                        if (state is DashboardViewModel.EngineerState.Working) "ANALYSING…" else "ANALYSE MY LAPS",
                        enabled = ready && apiKey.isNotBlank() &&
                            state !is DashboardViewModel.EngineerState.Working,
                    ) { viewModel.askEngineer(laps) }
                }
            }
            Spacer(Modifier.height(10.dp))

            // ---- Response --------------------------------------------------
            when (val s = state) {
                is DashboardViewModel.EngineerState.Working -> Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(22.dp).height(22.dp),
                            color = Palette.Amber, strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Your engineer is reading the telemetry…", color = Palette.Ink, fontSize = 13.sp)
                    }
                }
                is DashboardViewModel.EngineerState.Done -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Label("ENGINEER'S REPORT")
                            Spacer(Modifier.weight(1f))
                            Pill("SHARE") {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, s.text)
                                }
                                context.startActivity(Intent.createChooser(send, "Share report…"))
                            }
                            Spacer(Modifier.width(6.dp))
                            Pill("DISMISS") { viewModel.resetEngineer() }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(s.text, color = Palette.Ink, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
                is DashboardViewModel.EngineerState.Error -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Couldn't get an analysis", color = Palette.Bad, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(s.message, color = Palette.InkDim, fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(8.dp))
                        Pill("DISMISS") { viewModel.resetEngineer() }
                    }
                }
                DashboardViewModel.EngineerState.Idle -> {}
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BigButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (enabled) Palette.Amber else Palette.Carbon2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(text, color = if (enabled) Palette.AmberInk else Palette.InkMute, fontSize = 13.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}
