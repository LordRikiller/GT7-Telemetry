package com.gt7telemetry.dash

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gt7telemetry.Frame
import com.gt7telemetry.ui.Fmt
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** A frame reduced to display-ready strings/fractions for the cluster families. */
data class ClusterData(
    val speed: Int,
    val speedUnit: String,
    val gear: String,
    val rpm: Int,
    val rpmMax: Int,
    val rpmFrac: Float,
    val redlineFrac: Float,
    val lap: String,
    val last: String,
    val best: String,
    val delta: String,
    val deltaBad: Boolean,
    val lapNo: String,
    val tyres: List<String>,
    val tempUnit: String,
    val fuel: Int,
    val oilTemp: Int,
    val water: Int,
    val boost: String,
    val throttle: Int,
    val brake: Int,
    val boostVal: Double,
    /** Laps still to run (incl. the current one); null when not a lapped race. */
    val lapsLeft: Int?,
    /** Estimated laps of fuel remaining; null until one clean lap is measured. */
    val fuelLaps: Double?,
    /** Suggested gear when the game recommends one different from current. */
    val suggested: String?,
    val tcs: Boolean,
    val asm: Boolean,
    val handbrake: Boolean,
    val limiter: Boolean,
)

fun clusterData(f: Frame, useMph: Boolean, useFahrenheit: Boolean): ClusterData {
    val d = if (f.lastLap > 0 && f.bestLap > 0) f.lastLap - f.bestLap else 0.0
    val deltaTxt = if (f.lastLap <= 0 || f.bestLap <= 0) "–"
    else (if (d >= 0) "+" else "−") + "%.3f".format(kotlin.math.abs(d))
    fun temp(cC: Double): Double = if (useFahrenheit) cC * 9 / 5 + 32 else cC
    val tyres = (0 until 4).map { i ->
        val cC = f.tyreTempC.getOrElse(i) { Double.NaN }
        if (cC.isFinite()) "${temp(cC).roundToInt()}°" else "–"
    }
    return ClusterData(
        speed = (if (useMph) f.speedMph else f.speedKmh).roundToInt(),
        speedUnit = if (useMph) "MPH" else "KM/H",
        gear = f.gearLabel,
        rpm = f.rpm.roundToInt(),
        rpmMax = f.rpmMax.roundToInt(),
        rpmFrac = (f.rpmPct / 100.0).toFloat().coerceIn(0f, 1f),
        redlineFrac = f.redlineFrac.toFloat().coerceIn(0f, 1f),
        lap = Fmt.lap(f.curLap),
        last = Fmt.lap(f.lastLap),
        best = Fmt.lap(f.bestLap),
        delta = deltaTxt,
        deltaBad = d > 0.0005,
        lapNo = when {
            f.lapNumber > 0 && f.totalLaps > 0 -> "L${f.lapNumber}/${f.totalLaps}"
            f.lapNumber > 0 -> "L${f.lapNumber}"
            else -> "L–"
        },
        tyres = tyres,
        tempUnit = if (useFahrenheit) "°F" else "°C",
        fuel = f.fuelPct.roundToInt(),
        oilTemp = temp(f.oilTempC).roundToInt(),
        water = temp(f.waterTempC).roundToInt(),
        boost = Fmt.n1(f.boostPsi),
        throttle = f.throttlePct.roundToInt(),
        brake = f.brakePct.roundToInt(),
        boostVal = f.boostPsi,
        lapsLeft = if (f.totalLaps > 0 && f.lapNumber in 1..f.totalLaps)
            f.totalLaps - f.lapNumber + 1 else null,
        fuelLaps = if (f.fuelPerLapPct > 0.05) f.fuelPct / f.fuelPerLapPct else null,
        suggested = f.suggestedGear?.takeIf { it != f.gear }?.toString(),
        tcs = f.tcsActive,
        asm = f.asmActive,
        handbrake = f.handbrake,
        limiter = f.revLimiterActive,
    )
}

// ---------------------------------------------------------------------------
// Gauges
// ---------------------------------------------------------------------------

/** Analog radial gauge (tach/speedo). Value maps over a 270° sweep. */
@Composable
fun RadialGauge(
    value: Float,
    max: Float,
    theme: ClusterTheme,
    modifier: Modifier = Modifier,
    min: Float = 0f,
    redline: Float? = null,
    majorStep: Float = if (max <= 12) 1f else (max / 6f),
    drawNumbers: Boolean = true,
    bezel: Boolean = false,
) {
    val start = 135f
    val sweep = 270f
    val numPaint = remember0(theme.dialText, theme.italic)
    Canvas(modifier) {
        val dmin = size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(cx, cy)
        val stroke = dmin * 0.04f
        val arcR = dmin / 2f - dmin * 0.09f
        fun ang(v: Float) = start + ((v - min) / (max - min)) * sweep
        fun pt(radius: Float, aDeg: Float): Offset {
            val a = Math.toRadians(aDeg.toDouble())
            return Offset(cx + radius * cos(a).toFloat(), cy + radius * sin(a).toFloat())
        }
        if (bezel && theme.ring != null) {
            drawCircle(theme.ring, dmin / 2f - stroke * 0.3f, center, style = Stroke(dmin * 0.028f))
        }
        drawCircle(theme.dialFace, arcR + dmin * 0.05f, center)
        // track + redline
        val box = Size(arcR * 2, arcR * 2)
        val tl = Offset(cx - arcR, cy - arcR)
        drawArc(theme.line, start, sweep, false, tl, box, style = Stroke(stroke, cap = StrokeCap.Round))
        if (redline != null) {
            val rs = ang(redline)
            drawArc(theme.redline, rs, start + sweep - rs, false, tl, box, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        // ticks + numbers
        val steps = ((max - min) / majorStep).roundToInt().coerceAtLeast(1)
        numPaint.textSize = dmin * 0.085f
        for (i in 0..steps) {
            val v = min + i * majorStep
            val a = ang(v)
            val past = redline != null && v >= redline
            val col = if (past) theme.redline else theme.dialText
            drawLine(col, pt(arcR + dmin * 0.02f, a), pt(arcR - dmin * 0.05f, a), strokeWidth = dmin * 0.012f)
            if (drawNumbers) {
                val p = pt(arcR - dmin * 0.15f, a)
                numPaint.color = col.toArgb()
                val fm = numPaint.fontMetrics
                drawContext.canvas.nativeCanvas.drawText(
                    v.roundToInt().toString(), p.x, p.y - (fm.ascent + fm.descent) / 2f, numPaint
                )
            }
        }
        // needle
        val na = ang(value.coerceIn(min, max))
        drawLine(theme.needle, pt(-dmin * 0.06f, na), pt(arcR - dmin * 0.06f, na), strokeWidth = dmin * 0.022f, cap = StrokeCap.Round)
        drawCircle(theme.needle, dmin * 0.045f, center)
        drawCircle(theme.dialFace, dmin * 0.018f, center)
    }
}

/** Segmented rev ring (Lexus LFA / Audi virtual-cockpit style). */
@Composable
fun SegmentedRing(value: Float, max: Float, theme: ClusterTheme, modifier: Modifier = Modifier, segments: Int = 36) {
    val start = 135f
    val sweep = 270f
    Canvas(modifier) {
        val dmin = size.minDimension
        val cx = size.width / 2f; val cy = size.height / 2f
        val arcR = dmin / 2f - dmin * 0.10f
        val box = Size(arcR * 2, arcR * 2)
        val tl = Offset(cx - arcR, cy - arcR)
        val stroke = dmin * 0.055f
        val gap = sweep / segments * 0.28f
        val litFrac = (value / max).coerceIn(0f, 1f)
        for (i in 0 until segments) {
            val fc = (i + 0.5f) / segments
            val a0 = start + (i.toFloat() / segments) * sweep + gap
            val lit = fc <= litFrac
            val col = when {
                !lit -> theme.line
                fc > 0.85f -> theme.redline
                fc > 0.70f -> theme.warn
                else -> theme.accent
            }
            drawArc(col, a0, sweep / segments - gap * 2, false, tl, box, style = Stroke(stroke, cap = StrokeCap.Butt))
        }
    }
}

/** A row of F1-style shift lights, lit up to [frac]. */
@Composable
fun ShiftLights(frac: Float, theme: ClusterTheme, modifier: Modifier = Modifier, count: Int = 18) {
    val lit = (frac.coerceIn(0f, 1f) * count).roundToInt()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until count) {
            val on = i < lit
            val col = when {
                !on -> theme.line
                i < count * 0.55f -> Color(0xFF00E078)
                i < count * 0.80f -> theme.warn
                else -> theme.redline
            }
            Box(Modifier.weight(1f).height(11.dp).clip(RoundedCornerShape(2.dp)).background(col))
        }
    }
}

/** Horizontal rev bar with a redline marker (used by the default + minimal). */
@Composable
fun RevBar(data: ClusterData, theme: ClusterTheme, modifier: Modifier = Modifier) {
    Box(modifier.height(30.dp).clip(RoundedCornerShape(8.dp)).background(theme.panel).border(1.dp, theme.line, RoundedCornerShape(8.dp))) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(data.rpmFrac.coerceIn(0f, 1f))
                .background(Brush.horizontalGradient(listOf(theme.accent, theme.redline)))
        )
        Row(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(data.redlineFrac.coerceAtLeast(0.01f)))
            Box(Modifier.fillMaxHeight().width(2.dp).background(theme.redline))
            Spacer(Modifier.weight((1f - data.redlineFrac).coerceAtLeast(0.01f)))
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${data.rpm}", color = theme.ink, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text("/ ${data.rpmMax} rpm", color = theme.ink2, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// Text + panel helpers
// ---------------------------------------------------------------------------

@Composable
fun Lab(text: String, theme: ClusterTheme, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, color = theme.mute, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
}

@Composable
fun Tile(theme: ClusterTheme, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(9.dp)).background(theme.panel).border(1.dp, theme.line, RoundedCornerShape(9.dp)).padding(9.dp)) { content() }
}

@Composable
fun Num(text: String, theme: ClusterTheme, size: Int, color: Color = theme.ink, weight: FontWeight = FontWeight.Bold, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier, color = color, fontSize = size.sp, fontWeight = weight,
        fontFamily = FontFamily.Monospace, fontStyle = if (theme.italic) FontStyle.Italic else FontStyle.Normal,
    )
}

/** Raw tyre surface temperatures, straight off the wire — just the numbers in
 *  the theme's ink, matching the FH6 app's no-verdict presentation. */
@Composable
fun TyrePods(data: ClusterData, theme: ClusterTheme, modifier: Modifier = Modifier) {
    val labels = listOf("FL", "FR", "RL", "RR")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (rowStart in listOf(0, 2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (i in rowStart until rowStart + 2) {
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).background(theme.panel)
                            .border(1.dp, theme.line, RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(labels[i], color = theme.mute, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Num(data.tyres[i], theme, 13)
                    }
                }
            }
        }
    }
}

@Composable
fun TimingBlock(data: ClusterData, theme: ClusterTheme, modifier: Modifier = Modifier) {
    Tile(theme, modifier) {
        Column {
            Lab("Lap ${data.lapNo}", theme)
            Num(data.lap, theme, 26, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Lab("Last", theme); Num(data.last, theme, 12) }
                Column { Lab("Best", theme); Num(data.best, theme, 12, color = theme.good) }
                Column { Lab("Δ", theme); Num(data.delta, theme, 12, color = if (data.deltaBad) theme.redline else theme.good) }
            }
        }
    }
}

/** Fuel / water / oil / boost — everything GT7 reports about the drivetrain. */
@Composable
fun VitalsStrip(data: ClusterData, theme: ClusterTheme, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Stat("Fuel", "${data.fuel}", "%", theme, Modifier.weight(1f))
        Stat("Water", "${data.water}", data.tempUnit, theme, Modifier.weight(1f))
        Stat("Oil", "${data.oilTemp}", data.tempUnit, theme, Modifier.weight(1f))
        Stat("Boost", data.boost, "psi", theme, Modifier.weight(1f))
    }
}

/** Race-management chips: laps remaining and estimated laps of fuel. */
@Composable
fun RaceStrip(data: ClusterData, theme: ClusterTheme, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Stat("Laps left", data.lapsLeft?.toString() ?: "–", "", theme, Modifier.weight(1f))
        Stat("Fuel range", data.fuelLaps?.let { "%.1f".format(it) } ?: "–", "laps", theme, Modifier.weight(1f))
    }
}

/** TCS / ASM / handbrake / rev-limiter status lamps. */
@Composable
fun IndicatorRow(data: ClusterData, theme: ClusterTheme, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Indicator("TCS", data.tcs, theme.accent, theme, Modifier.weight(1f))
        Indicator("ASM", data.asm, theme.accent, theme, Modifier.weight(1f))
        Indicator("HANDBRAKE", data.handbrake, theme.warn, theme, Modifier.weight(1f))
        Indicator("LIMITER", data.limiter, theme.redline, theme, Modifier.weight(1f))
    }
}

@Composable
private fun Indicator(label: String, on: Boolean, onColor: Color, theme: ClusterTheme, modifier: Modifier = Modifier) {
    val col = if (on) onColor else theme.mute
    Box(
        modifier.clip(RoundedCornerShape(6.dp))
            .background(if (on) onColor.copy(alpha = 0.16f) else theme.panel)
            .border(1.dp, if (on) onColor else theme.line, RoundedCornerShape(6.dp))
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = col, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    }
}

/** "▲ 4" hint under the gear when the game suggests a different one. */
@Composable
fun GearHint(data: ClusterData, theme: ClusterTheme, modifier: Modifier = Modifier) {
    val s = data.suggested ?: return
    Text(
        "▲ $s", modifier = modifier, color = theme.accent, fontSize = 13.sp,
        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
    )
}

@Composable
fun Stat(label: String, value: String, unit: String, theme: ClusterTheme, modifier: Modifier = Modifier) {
    Tile(theme, modifier) {
        Column {
            Lab(label, theme)
            Row(verticalAlignment = Alignment.Bottom) {
                Num(value, theme, 17)
                Spacer(Modifier.width(3.dp))
                Text(unit, color = theme.ink2, fontSize = 9.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}

// Paint helper — a mutable Paint kept across recompositions of a gauge.
@Composable
private fun remember0(color: Color, italic: Boolean): Paint =
    androidx.compose.runtime.remember(color, italic) {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, if (italic) Typeface.BOLD_ITALIC else Typeface.BOLD)
            this.color = color.toArgb()
        }
    }
