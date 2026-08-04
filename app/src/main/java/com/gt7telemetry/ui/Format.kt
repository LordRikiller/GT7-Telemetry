package com.gt7telemetry.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt

object Fmt {
    /** Seconds -> "m:ss.mmm" or "--:--.---" for zero/no time. */
    fun lap(seconds: Double): String {
        if (seconds <= 0.0) return "--:--.---"
        val total = (seconds * 1000).roundToInt()
        val ms = total % 1000
        val s = (total / 1000) % 60
        val m = total / 60000
        return "%d:%02d.%03d".format(m, s, ms)
    }

    fun delta(last: Double, best: Double): Pair<String, Color?> {
        if (last <= 0 || best <= 0) return "–" to null
        val d = last - best
        val sign = if (d >= 0) "+" else "−"
        val txt = sign + "%.3f".format(abs(d))
        return txt to if (d > 0.0005) Palette.Bad else Palette.Good
    }

    fun n0(v: Double): String = v.roundToInt().toString()
    fun n1(v: Double): String = "%.1f".format(v)

    // Tyre temperature is shown as raw °C/°F only — matching the FH6 app's
    // no-verdict presentation (its working-window colouring was removed after
    // testing showed no verified temperature→grip coupling to display).
}
