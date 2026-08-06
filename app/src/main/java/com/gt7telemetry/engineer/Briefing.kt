package com.gt7telemetry.engineer

import com.gt7telemetry.logger.RecordedLap
import java.util.Locale
import kotlin.math.max

/**
 * Builds the "race engineer briefing": a self-contained plain-text prompt
 * carrying the car, the driver's setup description, per-lap statistics and
 * a downsampled trace of the best lap. The same text is used for both
 * export paths — shared to any AI app the user already pays for, or sent
 * as the single bounded request the built-in engineer makes.
 */
object Briefing {

    /** Cap the trace table so the briefing stays a few thousand tokens. */
    private const val MAX_TRACE_ROWS = 150

    fun build(
        laps: List<RecordedLap>,
        carName: String?,
        setupNotes: String,
    ): String = buildString {
        val best = laps.filter { it.lapTimeS > 0 }.minByOrNull { it.lapTimeS } ?: laps.lastOrNull()

        appendLine("# GT7 race engineer briefing")
        appendLine()
        appendLine("You are a professional race engineer for Gran Turismo 7. Below is real telemetry")
        appendLine("captured from my session. Analyse it and give me concrete, prioritised tuning")
        appendLine("changes for this car — exact GT7 settings-sheet adjustments (ride height in mm,")
        appendLine("anti-roll bar levels, damper percentages, camber/toe, differential accel/braking,")
        appendLine("downforce, gearing, ballast) — each one tied to evidence you can point to in the")
        appendLine("data. Also comment on my driving (braking points, throttle application, coasting).")
        appendLine("Finish with the single change you'd make first and what improvement to expect.")
        appendLine()
        appendLine("## Car")
        appendLine("- ${carName ?: "Unknown car"}")
        appendLine()
        appendLine("## Current setup (as described by the driver)")
        if (setupNotes.isBlank()) {
            appendLine("Not provided — assume the stock setup unless the data suggests otherwise.")
        } else {
            appendLine(setupNotes.trim())
        }
        appendLine()
        appendLine("(GT7's telemetry stream does not broadcast settings-sheet values, so the setup")
        appendLine("above is the driver's own description; the telemetry below is measured.)")
        appendLine()
        appendLine("## Laps recorded (${laps.size})")
        appendLine()
        appendLine("| Lap | Time | Max km/h | Min km/h | Full throttle | Braking | Coasting | Max lat G | Max brake G |")
        appendLine("|---|---|---|---|---|---|---|---|---|")
        for (lap in laps) {
            appendLine(
                "| ${lap.lapNumber}${if (lap === best) " (best)" else ""} " +
                    "| ${fmtLap(lap.lapTimeS)} " +
                    "| ${"%.0f".fmt(lap.maxSpeedKmh)} | ${"%.0f".fmt(lap.minSpeedKmh)} " +
                    "| ${"%.0f%%".fmt(lap.fullThrottlePct)} | ${"%.0f%%".fmt(lap.brakingPct)} " +
                    "| ${"%.0f%%".fmt(lap.coastingPct)} " +
                    "| ${"%.2f".fmt(lap.maxLatG)} | ${"%.2f".fmt(lap.maxBrakingG)} |"
            )
        }
        if (best != null) {
            appendLine()
            appendLine("## Best lap trace — lap ${best.lapNumber}, ${fmtLap(best.lapTimeS)} (downsampled)")
            appendLine()
            if (!best.hasSteering) {
                appendLine("(Steering channel unavailable — the console sent the legacy packet this session.)")
                appendLine()
            }
            appendLine("t_s,speed_kmh,throttle_pct,brake_pct,steer_deg,gear,lat_g,long_g")
            val step = max(1, best.sampleCount / MAX_TRACE_ROWS)
            var i = 0
            while (i < best.sampleCount) {
                val steer = best.steerDeg[i]
                appendLine(
                    "%.1f,%.0f,%.0f,%.0f,%s,%.0f,%.2f,%.2f".fmt(
                        best.t[i].toDouble(), best.speedKmh[i].toDouble(),
                        best.throttlePct[i].toDouble(), best.brakePct[i].toDouble(),
                        if (steer.isNaN()) "" else "%.0f".fmt(steer.toDouble()),
                        best.gear[i].toDouble(), best.latG[i].toDouble(), best.longG[i].toDouble(),
                    )
                )
                i += step
            }
        }
        appendLine()
        appendLine("Channels: speed km/h · pedals 0–100 % · steer = wheel angle in degrees")
        appendLine("(negative = left) · lat_g signed cornering load · long_g + accel / − braking.")
    }

    fun fmtLap(s: Double): String = if (s <= 0) "—" else {
        val m = (s / 60).toInt()
        String.format(Locale.US, "%d:%06.3f", m, s - m * 60)
    }

    private fun String.fmt(vararg args: Any?): String = String.format(Locale.US, this, *args)
}
