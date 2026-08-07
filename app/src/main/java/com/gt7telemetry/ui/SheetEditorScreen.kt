package com.gt7telemetry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gt7telemetry.TelemetryRepository
import com.gt7telemetry.car.CarCatalog
import com.gt7telemetry.car.SetupProbe
import com.gt7telemetry.setup.SetupSheet
import java.util.Locale

/**
 * The GT7 settings-sheet replica. Pick the car, declare the parts fitted,
 * and only the settings those parts unlock appear — the same gating rules
 * as the game's tuning shop. Saved per car; the sheet is pulled up
 * automatically when that car is driven and attached to every AI briefing.
 */
@Composable
fun SheetEditorScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
) {
    val frame by TelemetryRepository.frame.collectAsStateWithLifecycle()
    val sheets by viewModel.setupSheets.collectAsStateWithLifecycle()
    val catalogRevision by viewModel.catalogRevision.collectAsStateWithLifecycle()

    // The car being edited: explicit pick > live car > first saved sheet.
    var carOrdinal by remember {
        mutableStateOf(viewModel.editorCarOrdinal ?: frame?.carOrdinal?.takeIf { it != 0 }
            ?: viewModel.setupSheets.value.keys.firstOrNull())
    }
    // Bump to reset every text field (car switch, prefill).
    var epoch by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf(loadDraft(carOrdinal, sheets, catalogRevision)) }
    var showPicker by remember { mutableStateOf(false) }
    var savedFlash by remember { mutableStateOf(false) }

    fun switchCar(ordinal: Int) {
        carOrdinal = ordinal
        viewModel.editorCarOrdinal = ordinal
        draft = loadDraft(ordinal, sheets, catalogRevision)
        epoch++
    }

    Column(Modifier.fillMaxSize().background(Palette.Asphalt).systemBarsPadding().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill("‹ SETUP", onClick = onBack)
            Spacer(Modifier.width(10.dp))
            Text("SETUP SHEET", color = Palette.Paint, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Pill(if (savedFlash) "SAVED ✓" else "SAVE", accent = true) {
                draft?.let { viewModel.saveSetupSheet(it); savedFlash = true }
            }
        }
        Spacer(Modifier.height(10.dp))

        val d = draft
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            // ---- Car ------------------------------------------------------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Label("CAR")
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            d?.carName?.ifBlank { null }
                                ?: carOrdinal?.let { "Car #$it" } ?: "No car selected",
                            color = Palette.Paint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Pill("CHANGE CAR") { showPicker = true }
                    }
                    val liveOrdinal = frame?.carOrdinal?.takeIf { it != 0 }
                    if (liveOrdinal != null && liveOrdinal != carOrdinal) {
                        Spacer(Modifier.height(6.dp))
                        Pill("USE CURRENT CAR (${CarCatalog.lookup(liveOrdinal)?.name ?: "#$liveOrdinal"})") {
                            switchCar(liveOrdinal)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            if (d == null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Pick a car to start its setup sheet — drive it once, or use CHANGE CAR to search the catalog.",
                            color = Palette.InkDim, fontSize = 13.sp)
                    }
                }
            } else {
                fun update(next: SetupSheet) { draft = next; savedFlash = false }

                // ---- Suspension --------------------------------------------
                SheetCard("SUSPENSION") {
                    KindChips(SetupSheet.SuspensionKind.entries.map { it.label },
                        d.parts.suspension.ordinal) { i ->
                        update(d.copy(parts = d.parts.copy(suspension = SetupSheet.SuspensionKind.entries[i])))
                    }
                    val s = d.parts.suspension
                    if (s == SetupSheet.SuspensionKind.HEIGHT_ADJUSTABLE || s == SetupSheet.SuspensionKind.FULLY_CUSTOM) {
                        FRRow("Ride height (mm)", epoch, d.suspension.rideHeightF, d.suspension.rideHeightR) { f, r ->
                            update(d.copy(suspension = d.suspension.copy(rideHeightF = f, rideHeightR = r)))
                        }
                    }
                    if (s == SetupSheet.SuspensionKind.FULLY_CUSTOM) {
                        FRRow("Natural freq (Hz)", epoch, d.suspension.freqF, d.suspension.freqR) { f, r ->
                            update(d.copy(suspension = d.suspension.copy(freqF = f, freqR = r)))
                        }
                        FRRowInt("Anti-roll bar (1–10)", epoch, d.suspension.arbF, d.suspension.arbR) { f, r ->
                            update(d.copy(suspension = d.suspension.copy(arbF = f, arbR = r)))
                        }
                        FRRowInt("Damping comp. (%)", epoch, d.suspension.compF, d.suspension.compR) { f, r ->
                            update(d.copy(suspension = d.suspension.copy(compF = f, compR = r)))
                        }
                        FRRowInt("Damping exp. (%)", epoch, d.suspension.extF, d.suspension.extR) { f, r ->
                            update(d.copy(suspension = d.suspension.copy(extF = f, extR = r)))
                        }
                        FRRow("Camber (°)", epoch, d.suspension.camberF, d.suspension.camberR) { f, r ->
                            update(d.copy(suspension = d.suspension.copy(camberF = f, camberR = r)))
                        }
                        FRRow("Toe (°)", epoch, d.suspension.toeF, d.suspension.toeR) { f, r ->
                            update(d.copy(suspension = d.suspension.copy(toeF = f, toeR = r)))
                        }
                    }
                }

                // ---- Transmission ------------------------------------------
                SheetCard("TRANSMISSION") {
                    KindChips(SetupSheet.TransmissionKind.entries.map { it.label },
                        d.parts.transmission.ordinal) { i ->
                        update(d.copy(parts = d.parts.copy(transmission = SetupSheet.TransmissionKind.entries[i])))
                    }
                    val custom = d.parts.transmission == SetupSheet.TransmissionKind.FULLY_CUSTOM ||
                        d.parts.transmission == SetupSheet.TransmissionKind.FULLY_CUSTOM_RACING
                    if (custom) {
                        val probe = SetupProbe.setup.collectAsStateWithLifecycle().value
                        if (probe != null && probe.carOrdinal == d.carOrdinal &&
                            probe.gearRatios.any { it > 0.05 }
                        ) {
                            Pill("PREFILL FROM TELEMETRY", accent = true) {
                                update(d.copy(transmission = d.transmission.copy(
                                    finalDrive = probe.finalDriveEst?.let { Math.round(it * 1000.0) / 1000.0 },
                                    gears = List(8) { i ->
                                        probe.gearRatios.getOrNull(i)?.takeIf { it > 0.05 }
                                            ?.let { Math.round(it * 1000.0) / 1000.0 }
                                    },
                                )))
                                epoch++
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        FRRow("Final drive / top speed (km/h)", epoch,
                            d.transmission.finalDrive, d.transmission.topSpeedKmh?.toDouble(),
                            labels = "FINAL" to "TOP SPD") { f, r ->
                            update(d.copy(transmission = d.transmission.copy(
                                finalDrive = f, topSpeedKmh = r?.toInt())))
                        }
                        // Gears, two per row.
                        for (g in 0 until 8 step 2) {
                            FRRow("Gear ${g + 1} / ${g + 2}", epoch,
                                d.transmission.gears.getOrNull(g), d.transmission.gears.getOrNull(g + 1),
                                labels = "${g + 1}" to "${g + 2}") { a, b ->
                                val gears = d.transmission.gears.toMutableList()
                                while (gears.size < 8) gears.add(null)
                                gears[g] = a; gears[g + 1] = b
                                update(d.copy(transmission = d.transmission.copy(gears = gears)))
                            }
                        }
                    }
                }

                // ---- Differential ------------------------------------------
                SheetCard("DIFFERENTIAL") {
                    KindChips(SetupSheet.DiffKind.entries.map { it.label },
                        d.parts.differential.ordinal) { i ->
                        update(d.copy(parts = d.parts.copy(differential = SetupSheet.DiffKind.entries[i])))
                    }
                    if (d.parts.differential != SetupSheet.DiffKind.STOCK) {
                        FRRowInt("Initial torque", epoch, d.differential.initialF, d.differential.initialR) { f, r ->
                            update(d.copy(differential = d.differential.copy(initialF = f, initialR = r)))
                        }
                        FRRowInt("Accel sensitivity", epoch, d.differential.accelF, d.differential.accelR) { f, r ->
                            update(d.copy(differential = d.differential.copy(accelF = f, accelR = r)))
                        }
                        FRRowInt("Braking sensitivity", epoch, d.differential.brakeF, d.differential.brakeR) { f, r ->
                            update(d.copy(differential = d.differential.copy(brakeF = f, brakeR = r)))
                        }
                        TextRow("F/R torque split (AWD, e.g. 30:70)", epoch, d.differential.frontRearSplit ?: "") {
                            update(d.copy(differential = d.differential.copy(frontRearSplit = it.ifBlank { null })))
                        }
                    }
                }

                // ---- Aero --------------------------------------------------
                SheetCard("AERODYNAMICS") {
                    ToggleRow("Front downforce adjustable", d.parts.frontAero) {
                        update(d.copy(parts = d.parts.copy(frontAero = it)))
                    }
                    ToggleRow("Rear wing fitted", d.parts.rearWing) {
                        update(d.copy(parts = d.parts.copy(rearWing = it)))
                    }
                    if (d.parts.frontAero || d.parts.rearWing) {
                        FRRowInt("Downforce level", epoch,
                            if (d.parts.frontAero) d.aero.front else null,
                            if (d.parts.rearWing) d.aero.rear else null) { f, r ->
                            update(d.copy(aero = d.aero.copy(
                                front = if (d.parts.frontAero) f else d.aero.front,
                                rear = if (d.parts.rearWing) r else d.aero.rear)))
                        }
                    }
                }

                // ---- Power / weight ---------------------------------------
                SheetCard("POWER & WEIGHT") {
                    KindChips(SetupSheet.ForcedInduction.entries.map { it.label },
                        d.parts.forcedInduction.ordinal) { i ->
                        update(d.copy(parts = d.parts.copy(forcedInduction = SetupSheet.ForcedInduction.entries[i])))
                    }
                    ToggleRow("ECU / power restrictor fitted", d.parts.ecuTuned) {
                        update(d.copy(parts = d.parts.copy(ecuTuned = it)))
                    }
                    if (d.parts.ecuTuned) {
                        FRRowInt("ECU output / restrictor (%)", epoch, d.power.ecuOutputPct, d.power.powerRestrictorPct,
                            labels = "ECU" to "RESTR") { f, r ->
                            update(d.copy(power = d.power.copy(ecuOutputPct = f, powerRestrictorPct = r)))
                        }
                    }
                    ToggleRow("Ballast fitted", d.parts.ballastFitted) {
                        update(d.copy(parts = d.parts.copy(ballastFitted = it)))
                    }
                    if (d.parts.ballastFitted) {
                        FRRowInt("Ballast kg / position (−50 F … +50 R)", epoch,
                            d.power.ballastKg, d.power.ballastPosition,
                            labels = "KG" to "POS") { f, r ->
                            update(d.copy(power = d.power.copy(ballastKg = f, ballastPosition = r)))
                        }
                    }
                }

                // ---- Brakes ------------------------------------------------
                SheetCard("BRAKES") {
                    ToggleRow("Brake balance controller fitted", d.parts.brakeBalanceController) {
                        update(d.copy(parts = d.parts.copy(brakeBalanceController = it)))
                    }
                    if (d.parts.brakeBalanceController) {
                        FRRowInt("Balance (−5 front … +5 rear)", epoch, d.brakes.balance, null,
                            labels = "BAL" to "") { f, _ ->
                            update(d.copy(brakes = d.brakes.copy(balance = f)))
                        }
                    }
                }

                // ---- Notes -------------------------------------------------
                SheetCard("NOTES") {
                    TextRow("Anything else the engineer should know", epoch, d.notes) {
                        update(d.copy(notes = it))
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    if (showPicker) CarPickerDialog(
        catalogRevision = catalogRevision,
        onDismiss = { showPicker = false },
        onPick = { ordinal -> showPicker = false; switchCar(ordinal) },
    )
}

private fun loadDraft(
    ordinal: Int?,
    sheets: Map<Int, SetupSheet>,
    @Suppress("UNUSED_PARAMETER") catalogRevision: Int,
): SetupSheet? {
    if (ordinal == null) return null
    sheets[ordinal]?.let { return it }
    val name = CarCatalog.lookup(ordinal)?.name ?: ""
    return SetupSheet(carOrdinal = ordinal, carName = name)
}

// ---------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------

@Composable
private fun SheetCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Label(title)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun KindChips(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
    labels.chunked(2).forEachIndexed { rowIdx, row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            row.forEachIndexed { colIdx, label ->
                val i = rowIdx * 2 + colIdx
                val isSel = i == selected
                Row(
                    Modifier.weight(1f).padding(horizontal = 2.dp)
                        .background(if (isSel) Palette.Amber.copy(alpha = 0.18f) else Palette.Carbon2,
                            RoundedCornerShape(6.dp))
                        .clickable { onPick(i) }
                        .padding(vertical = 7.dp, horizontal = 6.dp),
                ) {
                    Text(label, color = if (isSel) Palette.Amber else Palette.Ink,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    Spacer(Modifier.height(4.dp))
}

/** Front/rear numeric pair. One-way binding: the fields own their text. */
@Composable
private fun FRRow(
    label: String,
    epoch: Int,
    front: Double?,
    rear: Double?,
    labels: Pair<String, String> = "F" to "R",
    onChange: (Double?, Double?) -> Unit,
) {
    var fVal by remember(epoch) { mutableStateOf(front) }
    var rVal by remember(epoch) { mutableStateOf(rear) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Palette.InkDim, fontSize = 11.sp)
        Spacer(Modifier.height(3.dp))
        Row {
            NumField(labels.first, epoch, front, Modifier.weight(1f)) { fVal = it; onChange(fVal, rVal) }
            Spacer(Modifier.width(8.dp))
            if (labels.second.isNotEmpty())
                NumField(labels.second, epoch, rear, Modifier.weight(1f)) { rVal = it; onChange(fVal, rVal) }
            else Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun FRRowInt(
    label: String,
    epoch: Int,
    front: Int?,
    rear: Int?,
    labels: Pair<String, String> = "F" to "R",
    onChange: (Int?, Int?) -> Unit,
) = FRRow(label, epoch, front?.toDouble(), rear?.toDouble(), labels) { f, r ->
    onChange(f?.toInt(), r?.toInt())
}

@Composable
private fun NumField(
    label: String,
    epoch: Int,
    initial: Double?,
    modifier: Modifier,
    onValue: (Double?) -> Unit,
) {
    var text by remember(epoch) {
        mutableStateOf(initial?.let {
            if (it % 1.0 == 0.0) it.toLong().toString() else String.format(Locale.US, "%s", it)
        } ?: "")
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onValue(it.trim().toDoubleOrNull()) },
        modifier = modifier,
        singleLine = true,
        label = { Text(label, fontSize = 10.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun TextRow(label: String, epoch: Int, initial: String, onValue: (String) -> Unit) {
    var text by remember(epoch) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onValue(it) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        label = { Text(label, fontSize = 10.sp) },
    )
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .background(Palette.Carbon2, RoundedCornerShape(6.dp))
            .clickable { onToggle(!value) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.Ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(if (value) "FITTED" else "—",
            color = if (value) Palette.Amber else Palette.InkMute,
            fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CarPickerDialog(
    catalogRevision: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, catalogRevision) {
        if (query.length >= 2) CarCatalog.search(query) else emptyList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Choose a car") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search the ${if (catalogRevision > 0) "catalog" else "catalog (loading…)"}") },
                    placeholder = { Text("e.g. GT3 RS, Supra, 787B") },
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(280.dp)) {
                    items(results, key = { it.first }) { (ordinal, info) ->
                        Text(
                            info.name,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onPick(ordinal) }
                                .padding(vertical = 9.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        },
    )
}
