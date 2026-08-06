package com.gt7telemetry.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gt7telemetry.TelemetryRepository
import com.gt7telemetry.logger.LapRecorder
import com.gt7telemetry.logger.RecordedLap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Trace colours — the classic data-logger convention.
private val TraceThrottle = Color(0xFF3BD98A) // green
private val TraceBrake = Color(0xFFFF4D42)    // red
private val TraceSteer = Color(0xFFF2EDE3)    // white
private val TraceSpeed = Color(0xFFFFAE00)    // amber

/**
 * The Data Logger: live scrolling driver-input traces at the top, the
 * session's recorded laps below, and a full lap analysis (track map +
 * traces) for the selected lap. Works in both orientations — landscape
 * puts map and traces side by side, portrait stacks and scrolls.
 */
@Composable
fun LoggerScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onOpenEngineer: () -> Unit,
) {
    val laps by LapRecorder.laps.collectAsStateWithLifecycle()
    val recordingLap by LapRecorder.recordingLap.collectAsStateWithLifecycle()
    val status by TelemetryRepository.status.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf<RecordedLap?>(null) }
    val shown = selected ?: laps.lastOrNull()
    val best = laps.filter { it.lapTimeS > 0 }.minByOrNull { it.lapTimeS }

    Column(
        Modifier.fillMaxSize().background(Palette.Asphalt).systemBarsPadding().padding(12.dp)
    ) {
        // ---- Top bar -----------------------------------------------------
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill("‹ DASH", onClick = onBack)
            Spacer(Modifier.width(10.dp))
            Text("DATA LOGGER", color = Palette.Paint, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.width(10.dp))
            if (recordingLap > 0 && status.live) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(Palette.Bad.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("● REC LAP $recordingLap", color = Palette.Bad, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            if (laps.isNotEmpty()) {
                Pill("CLEAR", onClick = { selected = null; viewModel.clearLaps() })
                Spacer(Modifier.width(6.dp))
            }
            Pill("AI ENGINEER ›", accent = true, onClick = onOpenEngineer)
        }
        Spacer(Modifier.height(10.dp))

        // ---- Live input strip --------------------------------------------
        Card {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("LIVE INPUTS · 20 s")
                    if (status.live && !status.extendedPacket) {
                        Spacer(Modifier.width(8.dp))
                        Text("NO STEERING — LEGACY PACKET (SEE SETTINGS)", color = Palette.Hot,
                            fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Legend("THROTTLE", TraceThrottle)
                    Legend("BRAKE", TraceBrake)
                    Legend("STEERING", TraceSteer)
                }
                Spacer(Modifier.height(6.dp))
                LiveInputStrip(Modifier.fillMaxWidth().height(96.dp))
            }
        }
        Spacer(Modifier.height(10.dp))

        // ---- Laps + analysis ---------------------------------------------
        if (laps.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("No laps recorded yet", color = Palette.Ink, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Recording is automatic: cross the start line and every full lap is " +
                            "captured at 60 Hz — inputs, speed, line and G. Laps appear here as " +
                            "you complete them.",
                        color = Palette.InkDim, fontSize = 13.sp,
                    )
                }
            }
        } else {
            BoxWithConstraints(Modifier.weight(1f)) {
                val landscape = maxWidth > maxHeight
                if (landscape) Row(Modifier.fillMaxSize()) {
                    LapList(laps, best, shown, Modifier.width(230.dp).fillMaxHeight()) { selected = it }
                    Spacer(Modifier.width(10.dp))
                    shown?.let { LapDetail(it, best, landscape = true, Modifier.weight(1f)) }
                } else Column(Modifier.fillMaxSize()) {
                    LapList(laps, best, shown, Modifier.fillMaxWidth().height(120.dp)) { selected = it }
                    Spacer(Modifier.height(10.dp))
                    shown?.let { LapDetail(it, best, landscape = false, Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Live strip — a ring buffer fed straight from the telemetry flow.
// ---------------------------------------------------------------------------

private const val LIVE_SECONDS = 20
private const val LIVE_CAP = LIVE_SECONDS * 60

private class LiveHistory {
    val throttle = FloatArray(LIVE_CAP)
    val brake = FloatArray(LIVE_CAP)
    val steer = FloatArray(LIVE_CAP) // NaN when unavailable
    var head = 0; var size = 0
    fun add(t: Float, b: Float, s: Float) {
        throttle[head] = t; brake[head] = b; steer[head] = s
        head = (head + 1) % LIVE_CAP
        if (size < LIVE_CAP) size++
    }
    inline fun forEachIndexedOldestFirst(action: (i: Int, idx: Int) -> Unit) {
        val start = (head - size + LIVE_CAP) % LIVE_CAP
        for (i in 0 until size) action(i, (start + i) % LIVE_CAP)
    }
}

@Composable
private fun LiveInputStrip(modifier: Modifier) {
    val history = remember { LiveHistory() }
    val revision = remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        TelemetryRepository.frame.collect { f ->
            if (f != null && f.raceOn) {
                history.add(
                    f.throttlePct.toFloat(), f.brakePct.toFloat(),
                    f.steeringRad?.let { Math.toDegrees(it).toFloat() } ?: Float.NaN,
                )
                revision.intValue++
            }
        }
    }
    Canvas(modifier) {
        revision.intValue // subscribe the draw pass to new samples
        drawLine(Palette.Line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
        if (history.size < 2) return@Canvas
        val dx = size.width / (LIVE_CAP - 1)
        val x0 = size.width - (history.size - 1) * dx
        // Steering scale: at least ±45° so gentle inputs stay readable.
        var maxSteer = 45f
        history.forEachIndexedOldestFirst { _, idx ->
            val s = history.steer[idx]
            if (!s.isNaN()) maxSteer = max(maxSteer, abs(s))
        }
        val thr = Path(); val brk = Path(); val str = Path()
        var strStarted = false
        history.forEachIndexedOldestFirst { i, idx ->
            val x = x0 + i * dx
            val yT = size.height * (1f - history.throttle[idx] / 100f)
            val yB = size.height * (1f - history.brake[idx] / 100f)
            if (i == 0) { thr.moveTo(x, yT); brk.moveTo(x, yB) }
            else { thr.lineTo(x, yT); brk.lineTo(x, yB) }
            val s = history.steer[idx]
            if (!s.isNaN()) {
                val yS = size.height / 2 * (1f - s / maxSteer)
                if (!strStarted) { str.moveTo(x, yS); strStarted = true } else str.lineTo(x, yS)
            }
        }
        drawPath(str, TraceSteer.copy(alpha = 0.85f), style = Stroke(2f, cap = StrokeCap.Round))
        drawPath(brk, TraceBrake, style = Stroke(2.5f, cap = StrokeCap.Round))
        drawPath(thr, TraceThrottle, style = Stroke(2.5f, cap = StrokeCap.Round))
    }
}

// ---------------------------------------------------------------------------
// Lap list
// ---------------------------------------------------------------------------

@Composable
private fun LapList(
    laps: List<RecordedLap>,
    best: RecordedLap?,
    shown: RecordedLap?,
    modifier: Modifier,
    onSelect: (RecordedLap) -> Unit,
) {
    Card(modifier) {
        LazyColumn(Modifier.padding(6.dp)) {
            items(laps.asReversed(), key = { System.identityHashCode(it) }) { lap ->
                val isShown = lap === shown
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(if (isShown) Palette.Carbon2 else Color.Transparent)
                        .clickable { onSelect(lap) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("L${lap.lapNumber}", color = if (lap === best) Palette.Amber else Palette.InkDim,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
                    Text(Fmt.lap(lap.lapTimeS), color = Palette.Paint, fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${Fmt.n0(lap.maxSpeedKmh)} km/h", color = Palette.InkMute, fontSize = 11.sp)
                    if (lap === best) {
                        Spacer(Modifier.width(6.dp))
                        Text("BEST", color = Palette.Amber, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Lap detail: stats + track map + traces
// ---------------------------------------------------------------------------

@Composable
private fun LapDetail(lap: RecordedLap, best: RecordedLap?, landscape: Boolean, modifier: Modifier) {
    if (landscape) Row(modifier) {
        Card(Modifier.weight(0.42f).fillMaxHeight()) {
            Column(Modifier.padding(10.dp)) {
                Label("LAP ${lap.lapNumber} · LINE (COLOURED BY SPEED)")
                Spacer(Modifier.height(6.dp))
                TrackMap(lap, Modifier.fillMaxSize().weight(1f))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(0.58f).fillMaxHeight()) {
            StatsRow(lap, best)
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxSize()) {
                Column(Modifier.padding(10.dp)) {
                    TraceLegendRow(lap)
                    Spacer(Modifier.height(6.dp))
                    LapTraces(lap, Modifier.fillMaxSize().weight(1f))
                }
            }
        }
    } else Column(modifier.verticalScroll(rememberScrollState())) {
        StatsRow(lap, best)
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                TraceLegendRow(lap)
                Spacer(Modifier.height(6.dp))
                LapTraces(lap, Modifier.fillMaxWidth().height(190.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                Label("LAP ${lap.lapNumber} · LINE (COLOURED BY SPEED)")
                Spacer(Modifier.height(6.dp))
                TrackMap(lap, Modifier.fillMaxWidth().height(230.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun TraceLegendRow(lap: RecordedLap) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Label("LAP ${lap.lapNumber} · ${Fmt.lap(lap.lapTimeS)}")
        Spacer(Modifier.weight(1f))
        Legend("SPEED", TraceSpeed)
        Legend("THR", TraceThrottle)
        Legend("BRK", TraceBrake)
        if (lap.hasSteering) Legend("STEER", TraceSteer)
    }
}

@Composable
private fun StatsRow(lap: RecordedLap, best: RecordedLap?) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(vertical = 10.dp, horizontal = 6.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val (delta, deltaCol) =
                if (best != null && best !== lap) Fmt.delta(lap.lapTimeS, best.lapTimeS)
                else "–" to null
            Stat("TIME", Fmt.lap(lap.lapTimeS))
            Stat("Δ BEST", delta, deltaCol ?: Palette.Paint)
            Stat("MAX", "${Fmt.n0(lap.maxSpeedKmh)}")
            Stat("FULL THR", "${Fmt.n0(lap.fullThrottlePct)}%")
            Stat("BRAKING", "${Fmt.n0(lap.brakingPct)}%")
            Stat("COAST", "${Fmt.n0(lap.coastingPct)}%")
            Stat("LAT G", Fmt.n1(lap.maxLatG))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color = Palette.Paint) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace)
        Text(label, color = Palette.InkMute, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp)
    }
}

@Composable
private fun TrackMap(lap: RecordedLap, modifier: Modifier) {
    Canvas(modifier) {
        if (lap.sampleCount < 2) return@Canvas
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (i in 0 until lap.sampleCount) {
            minX = min(minX, lap.posX[i]); maxX = max(maxX, lap.posX[i])
            minZ = min(minZ, lap.posZ[i]); maxZ = max(maxZ, lap.posZ[i])
        }
        val spanX = max(maxX - minX, 1f)
        val spanZ = max(maxZ - minZ, 1f)
        val pad = 12f
        val scale = min((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanZ)
        val offX = (size.width - spanX * scale) / 2
        val offY = (size.height - spanZ * scale) / 2
        val vMax = max(lap.maxSpeedKmh.toFloat(), 1f)

        val stride = max(1, lap.sampleCount / 1200)
        var i = stride
        while (i < lap.sampleCount) {
            val p = i - stride
            val a = Offset(offX + (lap.posX[p] - minX) * scale, offY + (lap.posZ[p] - minZ) * scale)
            val b = Offset(offX + (lap.posX[i] - minX) * scale, offY + (lap.posZ[i] - minZ) * scale)
            // Blue when slow, amber when fast — heavy braking zones pop out.
            val v = (lap.speedKmh[i] / vMax).coerceIn(0f, 1f)
            drawLine(lerp(Palette.Cold, Palette.Amber, v), a, b, strokeWidth = 3f, cap = StrokeCap.Round)
            i += stride
        }
        // Start/finish marker.
        drawCircle(Palette.Paint,
            radius = 5f,
            center = Offset(offX + (lap.posX[0] - minX) * scale, offY + (lap.posZ[0] - minZ) * scale))
    }
}

@Composable
private fun LapTraces(lap: RecordedLap, modifier: Modifier) {
    Canvas(modifier) {
        if (lap.sampleCount < 2) return@Canvas
        val n = lap.sampleCount
        val tEnd = max(lap.t[n - 1], 0.001f)
        fun x(i: Int) = lap.t[i] / tEnd * size.width
        val vMax = max(lap.maxSpeedKmh.toFloat(), 1f)

        // Steering scale symmetric around the centre line.
        var maxSteer = 45f
        if (lap.hasSteering) for (i in 0 until n) {
            val s = lap.steerDeg[i]
            if (!s.isNaN()) maxSteer = max(maxSteer, abs(s))
        }

        drawLine(Palette.Line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)

        val stride = max(1, n / 1500)
        val speed = Path(); val thr = Path(); val brk = Path(); val str = Path()
        var first = true; var strStarted = false
        var i = 0
        while (i < n) {
            val xx = x(i)
            val yV = size.height * (1f - lap.speedKmh[i] / vMax)
            val yT = size.height * (1f - lap.throttlePct[i] / 100f)
            val yB = size.height * (1f - lap.brakePct[i] / 100f)
            if (first) { speed.moveTo(xx, yV); thr.moveTo(xx, yT); brk.moveTo(xx, yB); first = false }
            else { speed.lineTo(xx, yV); thr.lineTo(xx, yT); brk.lineTo(xx, yB) }
            val s = lap.steerDeg[i]
            if (!s.isNaN()) {
                val yS = size.height / 2 * (1f - s / maxSteer)
                if (!strStarted) { str.moveTo(xx, yS); strStarted = true } else str.lineTo(xx, yS)
            }
            i += stride
        }
        drawPath(str, TraceSteer.copy(alpha = 0.55f), style = Stroke(1.5f))
        drawPath(speed, TraceSpeed, style = Stroke(2f))
        drawPath(brk, TraceBrake, style = Stroke(2f))
        drawPath(thr, TraceThrottle, style = Stroke(2f))
    }
}

// ---------------------------------------------------------------------------
// Small shared pieces
// ---------------------------------------------------------------------------

@Composable
internal fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Carbon)) { content() }
}

@Composable
internal fun Label(text: String) {
    Text(text, color = Palette.InkMute, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp)
}

@Composable
internal fun Legend(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
        Box(Modifier.width(10.dp).height(3.dp).background(color))
        Spacer(Modifier.width(4.dp))
        Text(name, color = Palette.InkDim, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp)
    }
}

@Composable
internal fun Pill(text: String, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (accent) Palette.Amber.copy(alpha = 0.16f) else Palette.Carbon2)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = if (accent) Palette.Amber else Palette.Ink, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold)
    }
}
