package com.gt7telemetry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gt7telemetry.TelemetryRepository
import com.gt7telemetry.car.CarCatalog
import com.gt7telemetry.car.MeasuredSetup
import com.gt7telemetry.car.SetupProbe
import java.util.Locale

/**
 * The Setup screen: everything the telemetry stream reveals about the
 * current car's tune. Split honestly into what GT7 broadcasts as
 * settings-sheet fact (gear ratios, rev limiter, tyre radii …), what the
 * app measures from behaviour (final drive, ride height, suspension
 * travel …), and what the game simply never transmits — which stays a
 * driver-described text field shared with the AI engineer's briefing.
 */
@Composable
fun SetupScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onOpenEngineer: () -> Unit,
    onOpenSheet: () -> Unit,
) {
    val setup by SetupProbe.setup.collectAsStateWithLifecycle()
    val frame by TelemetryRepository.frame.collectAsStateWithLifecycle()
    val setupNotes by viewModel.setupNotes.collectAsStateWithLifecycle()
    val catalogRevision by viewModel.catalogRevision.collectAsStateWithLifecycle()

    val ordinal = setup?.carOrdinal?.takeIf { it != Int.MIN_VALUE } ?: frame?.carOrdinal
    val carName = if (catalogRevision > 0) CarCatalog.lookup(ordinal)?.name else null

    Column(Modifier.fillMaxSize().background(Palette.Asphalt).systemBarsPadding().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill("‹ DASH", onClick = onBack)
            Spacer(Modifier.width(10.dp))
            Text("SETUP", color = Palette.Paint, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Pill("AI ENGINEER ›", accent = true, onClick = onOpenEngineer)
        }
        Spacer(Modifier.height(10.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val s = setup
            if (s == null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Waiting for telemetry", color = Palette.Ink, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Drive a few corners and this screen fills itself in: gear ratios and " +
                                "the rev limiter arrive instantly, ride height needs a standstill " +
                                "(grid or pit lane), and the final-drive estimate needs some steady " +
                                "driving in a few gears.",
                            color = Palette.InkDim, fontSize = 13.sp,
                        )
                    }
                }
            } else {
                // ---- Car ---------------------------------------------------
                SetupCard("CAR") {
                    KV("Model", carName ?: ordinal?.takeIf { it != 0 }?.let { "Car #$it" } ?: "—")
                    KV("Drivetrain", when (s.electric) {
                        true -> "Electric"
                        false -> if (s.hasTurbo) "Combustion · turbo (max ${fmt1(s.maxBoostPsi)} psi seen)"
                        else "Combustion · no boost seen"
                        null -> if (s.hasTurbo) "Turbo (max ${fmt1(s.maxBoostPsi)} psi seen)" else "—"
                    })
                    KV("Rev limiter", if (s.revLimiterRpm > 0) "${s.revLimiterRpm} rpm" else "—")
                    KV("Shift light", if (s.shiftLightRpm > 0) "${s.shiftLightRpm} rpm" else "—")
                    KV("Calc. top speed", if (s.calcMaxSpeedKmh > 0) "${s.calcMaxSpeedKmh} km/h (game's estimate for this tune)" else "—")
                    KV("Fastest seen", if (s.maxSpeedSeenKmh > 1) "${fmt0(s.maxSpeedSeenKmh)} km/h this session" else "—")
                    KV("Fuel tank", if (s.fuelCapacityL > 0.5) "${fmt0(s.fuelCapacityL)} L" else "—")
                    KV("Aids observed", listOfNotNull(
                        "TCS".takeIf { s.tcsSeen }, "ASM".takeIf { s.asmSeen },
                    ).ifEmpty { listOf("none seen") }.joinToString(" · "))
                }
                Spacer(Modifier.height(10.dp))

                // ---- Transmission (broadcast settings-sheet values) --------
                SetupCard("TRANSMISSION — READ FROM THE STREAM") {
                    val fitted = s.gearRatios.withIndex().filter { it.value > 0.05 }
                    if (fitted.isEmpty()) {
                        Text("No gear data yet.", color = Palette.InkDim, fontSize = 13.sp)
                    } else {
                        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                            Cell("GEAR", 0.2f, header = true)
                            Cell("RATIO", 0.3f, header = true)
                            Cell("@ LIMITER", 0.5f, header = true)
                        }
                        for ((i, ratio) in fitted) {
                            val v = s.speedAtRedlineKmh.getOrNull(i) ?: 0.0
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Cell("${i + 1}", 0.2f)
                                Cell(fmt3(ratio), 0.3f)
                                Cell(if (v > 1) "${fmt0(v)} km/h" else "—", 0.5f)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        KV("Final drive (est.)", s.finalDriveEst?.let { fmt3(it) } ?: "measuring — drive steadily in a few gears")
                    }
                }
                Spacer(Modifier.height(10.dp))

                // ---- Chassis (measured) ------------------------------------
                SetupCard("CHASSIS — MEASURED") {
                    KV("Ride height (static)", s.rideHeightStaticMm?.let { "${fmt0(it)} mm (measured at standstill)" }
                        ?: "needs a standstill — grid, grid start or pit lane")
                    KV("Lowest under load", s.rideHeightMinMm?.let { "${fmt0(it)} mm" } ?: "—")
                    val t = s.suspTravelMm
                    if (t.any { it > 0.1 }) {
                        KV("Susp. travel used", "FL ${fmt0(t[0])} · FR ${fmt0(t[1])} · RL ${fmt0(t[2])} · RR ${fmt0(t[3])} mm")
                    }
                    val r = s.tyreRadiusM
                    if (r.getOrNull(0)?.let { it > 0.01 } == true) {
                        KV("Tyre radius", "F ${fmt0(r[0] * 1000)} mm · R ${fmt0(r[2] * 1000)} mm")
                    }
                    if (s.maxOilPressureBar > 0.1) KV("Oil pressure (peak)", "${fmt1(s.maxOilPressureBar)} bar")
                }
                Spacer(Modifier.height(10.dp))
            }

            // ---- The per-car setup sheet (GT7 settings-sheet replica) ------
            run {
                val sheets by viewModel.setupSheets.collectAsStateWithLifecycle()
                val sheetOrdinal = ordinal?.takeIf { it != 0 }
                val sheet = sheetOrdinal?.let { sheets[it] }
                SetupCard("SETUP SHEET — FULL GT7 SETTINGS REPLICA") {
                    if (sheet != null && sheet.hasAnyValues) {
                        Text(
                            "${sheet.carName.ifBlank { "Car #${sheet.carOrdinal}" }} — sheet saved. " +
                                "It's attached to every AI briefing for this car automatically.",
                            color = Palette.Ink, fontSize = 12.sp,
                        )
                    } else {
                        Text(
                            "Declare the car's parts and every unlocked setting exactly like the " +
                                "in-game sheet — parts decide which settings appear. Saved per car " +
                                "and pulled up automatically when you drive it.",
                            color = Palette.InkMute, fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Pill(if (sheet != null) "EDIT SHEET" else "CREATE SHEET", accent = true) {
                            viewModel.editorCarOrdinal = sheetOrdinal
                            onOpenSheet()
                        }
                        if (sheets.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text("${sheets.size} car${if (sheets.size == 1) "" else "s"} on file",
                                color = Palette.InkMute, fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.CenterVertically))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // ---- Tyres fitted (declared — GT7 doesn't broadcast compound) --
            val tyres by viewModel.tyres.collectAsStateWithLifecycle()
            SetupCard("TYRES FITTED — TAP TO DECLARE") {
                Text(
                    "GT7 doesn't broadcast the compound (or wear). Declare it here and every " +
                        "recorded lap, CSV export and AI briefing carries it.",
                    color = Palette.InkMute, fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))
                val compounds = listOf(
                    "Comfort Hard", "Comfort Medium", "Comfort Soft",
                    "Sports Hard", "Sports Medium", "Sports Soft",
                    "Racing Hard", "Racing Medium", "Racing Soft",
                    "Intermediate", "Heavy Wet", "Dirt",
                )
                FlowChips(options = compounds, selected = tyres) { pick ->
                    viewModel.setTyres(if (pick == tyres) "" else pick)
                }
            }
            Spacer(Modifier.height(10.dp))

            // ---- What GT7 never sends + the described setup ----------------
            SetupCard("NOT BROADCAST BY GT7 — DESCRIBE IT ONCE") {
                Text(
                    "The settings sheet itself (ARB levels, damper %, camber/toe, diff accel/braking, " +
                        "downforce clicks, power/ECU %, ballast) never leaves the console — no app can " +
                        "read it. Describe it here once; the Setup screen and every AI briefing carry it.",
                    color = Palette.InkMute, fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))
                var draft by remember(setupNotes) { mutableStateOf(setupNotes) }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    placeholder = { Text("e.g. ride height 90/95, ARB 5/6, comp 60/60 %, ext 70/70 %, camber 3.0/2.0, toe 0/0.15 in, diff 5/25/15, wing 500/750, TCS 0") },
                )
                if (draft != setupNotes) {
                    Spacer(Modifier.height(6.dp))
                    Pill("SAVE", accent = true) { viewModel.setSetupNotes(draft) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Simple wrapping chip row (Compose foundation has no FlowRow until later versions). */
@Composable
private fun FlowChips(options: List<String>, selected: String, onPick: (String) -> Unit) {
    // Three per row keeps labels readable on phones and tablets alike.
    options.chunked(3).forEach { rowItems ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            rowItems.forEach { label ->
                val isSel = label == selected
                Row(
                    Modifier.weight(1f).padding(horizontal = 2.dp)
                        .background(
                            if (isSel) Palette.Amber.copy(alpha = 0.18f) else Palette.Carbon2,
                            androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                        )
                        .clickable { onPick(label) }
                        .padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(label, color = if (isSel) Palette.Amber else Palette.Ink, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SetupCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Label(title)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun KV(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = Palette.InkDim, fontSize = 12.sp, modifier = Modifier.width(130.dp))
        Text(value, color = Palette.Paint, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, weight: Float, header: Boolean = false) {
    Text(
        text,
        color = if (header) Palette.InkMute else Palette.Paint,
        fontSize = if (header) 9.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = if (header) 1.sp else 0.sp,
        fontFamily = if (header) null else FontFamily.Monospace,
        modifier = Modifier.weight(weight),
    )
}

private fun fmt0(v: Double) = String.format(Locale.US, "%.0f", v)
private fun fmt1(v: Double) = String.format(Locale.US, "%.1f", v)
private fun fmt3(v: Double) = String.format(Locale.US, "%.3f", v)
