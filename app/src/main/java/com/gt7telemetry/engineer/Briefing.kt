package com.gt7telemetry.engineer

import com.gt7telemetry.car.MeasuredSetup
import com.gt7telemetry.logger.RecordedLap
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

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
        measured: MeasuredSetup? = null,
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
        appendLine("(GT7's telemetry stream does not broadcast most settings-sheet values, so the")
        appendLine("setup above is the driver's own description; everything below is measured.)")
        appendLine()
        if (measured != null) {
            appendLine("## Setup data read/measured from the stream")
            val fitted = measured.gearRatios.withIndex().filter { it.value > 0.05 }
            if (fitted.isNotEmpty()) {
                append("- Gear ratios (fitted): ")
                appendLine(fitted.joinToString(" · ") { (i, r) -> "${i + 1}: ${"%.3f".fmt(r)}" })
            }
            measured.finalDriveEst?.let { appendLine("- Final drive (estimated): ${"%.3f".fmt(it)}") }
            if (measured.revLimiterRpm > 0) appendLine("- Rev limiter: ${measured.revLimiterRpm} rpm")
            if (measured.calcMaxSpeedKmh > 0)
                appendLine("- Game's calculated top speed for this tune: ${measured.calcMaxSpeedKmh} km/h" +
                    " (fastest actually reached: ${"%.0f".fmt(measured.maxSpeedSeenKmh)} km/h)")
            measured.rideHeightStaticMm?.let {
                append("- Ride height, static (measured at standstill): ${"%.0f".fmt(it)} mm")
                measured.rideHeightMinMm?.let { mn -> append(" — compresses to ${"%.0f".fmt(mn)} mm under load") }
                appendLine()
            }
            val t = measured.suspTravelMm
            if (t.any { it > 0.1 })
                appendLine("- Suspension travel used (mm): FL ${"%.0f".fmt(t[0])} / FR ${"%.0f".fmt(t[1])} / RL ${"%.0f".fmt(t[2])} / RR ${"%.0f".fmt(t[3])}")
            if (measured.tyreRadiusM.getOrNull(0)?.let { it > 0.01 } == true)
                appendLine("- Tyre radius: front ${"%.0f".fmt(measured.tyreRadiusM[0] * 1000)} mm, rear ${"%.0f".fmt(measured.tyreRadiusM[2] * 1000)} mm")
            if (measured.hasTurbo) appendLine("- Turbo fitted — peak boost seen ${"%.1f".fmt(measured.maxBoostPsi)} psi")
            if (measured.electric == true) appendLine("- Electric drivetrain")
            if (measured.fuelCapacityL > 0.5) appendLine("- Fuel tank: ${"%.0f".fmt(measured.fuelCapacityL)} L")
            val aids = listOfNotNull("TCS".takeIf { measured.tcsSeen }, "ASM".takeIf { measured.asmSeen })
            if (aids.isNotEmpty()) appendLine("- Driver aids seen active: ${aids.joinToString(", ")}")
            appendLine()
        }
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
            // Each row AGGREGATES its whole window from the 60 Hz log rather
            // than point-sampling one instant — a 0.2 s shift cut then reads
            // as slightly reduced mean throttle instead of a misleading 0.
            val step = max(1, best.sampleCount / MAX_TRACE_ROWS)
            var i = 0
            while (i < best.sampleCount) {
                val end = min(i + step, best.sampleCount)
                var thr = 0.0; var brk = 0.0; var steerSum = 0.0; var steerN = 0
                var latPeak = 0.0; var lonPeak = 0.0
                for (j in i until end) {
                    thr += best.throttlePct[j]
                    brk += best.brakePct[j]
                    val s = best.steerDeg[j]
                    if (!s.isNaN()) { steerSum += s; steerN++ }
                    if (kotlin.math.abs(best.latG[j].toDouble()) > kotlin.math.abs(latPeak)) latPeak = best.latG[j].toDouble()
                    if (kotlin.math.abs(best.longG[j].toDouble()) > kotlin.math.abs(lonPeak)) lonPeak = best.longG[j].toDouble()
                }
                val n = (end - i).toDouble()
                appendLine(
                    "%.1f,%.0f,%.0f,%.0f,%s,%.0f,%.2f,%.2f".fmt(
                        best.t[i].toDouble(), best.speedKmh[i].toDouble(),
                        thr / n, brk / n,
                        if (steerN == 0) "" else "%.0f".fmt(steerSum / steerN),
                        best.gear[i].toDouble(), latPeak, lonPeak,
                    )
                )
                i += step
            }
        }
        appendLine()
        appendLine("Channels: speed km/h · pedals 0–100 % · steer = wheel angle in degrees")
        appendLine("(negative = left) · lat_g signed cornering load · long_g + accel / − braking.")
        appendLine("Each row covers the window to the next row: pedals/steer are window MEANS")
        appendLine("(sequential-shift throttle cuts show as briefly reduced throttle, not 0),")
        appendLine("lat_g/long_g are the window's signed peaks; speed and gear are at row start.")
    }

    fun fmtLap(s: Double): String = if (s <= 0) "—" else {
        val m = (s / 60).toInt()
        String.format(Locale.US, "%d:%06.3f", m, s - m * 60)
    }

    private fun String.fmt(vararg args: Any?): String = String.format(Locale.US, this, *args)
}
