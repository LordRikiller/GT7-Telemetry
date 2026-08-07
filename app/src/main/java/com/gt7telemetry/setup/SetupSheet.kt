package com.gt7telemetry.setup

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * A per-car replica of GT7's settings sheet.
 *
 * GT7 never broadcasts the sheet, so the driver declares it here once —
 * but the STRUCTURE is smart: which settings exist is decided by which
 * parts are fitted, using the same rules as the game's tuning shop (a
 * fully customisable suspension unlocks ride height/frequency/dampers/
 * ARB/camber/toe; a customisable transmission unlocks per-gear ratios and
 * final drive; a customisable LSD unlocks initial/accel/braking, and so
 * on). Values are free-entry because the legal RANGE of each setting is
 * per-car data that lives only inside the game.
 *
 * All value fields are nullable — null means "not set/left stock", and
 * only set values are sent to the race engineer.
 */
@Serializable
data class SetupSheet(
    val carOrdinal: Int,
    val carName: String = "",
    val updatedAtMs: Long = 0,
    val parts: Parts = Parts(),
    val suspension: Suspension = Suspension(),
    val transmission: Transmission = Transmission(),
    val differential: Differential = Differential(),
    val aero: Aero = Aero(),
    val power: Power = Power(),
    val brakes: Brakes = Brakes(),
    val notes: String = "",
) {
    @Serializable
    data class Parts(
        val suspension: SuspensionKind = SuspensionKind.STOCK,
        val transmission: TransmissionKind = TransmissionKind.STOCK,
        val differential: DiffKind = DiffKind.STOCK,
        val frontAero: Boolean = false,
        val rearWing: Boolean = false,
        val brakeBalanceController: Boolean = false,
        val ecuTuned: Boolean = false,
        val ballastFitted: Boolean = false,
        val forcedInduction: ForcedInduction = ForcedInduction.NONE,
    )

    enum class SuspensionKind(val label: String) {
        STOCK("Stock"),
        SPORTS("Sports"),
        HEIGHT_ADJUSTABLE("Height-Adjustable Sports"),
        FULLY_CUSTOM("Fully Customisable"),
    }

    enum class TransmissionKind(val label: String) {
        STOCK("Stock"),
        SPORTS("Sports (close-ratio)"),
        FULLY_CUSTOM("Fully Customisable"),
        FULLY_CUSTOM_RACING("Fully Customised Racing"),
    }

    enum class DiffKind(val label: String) {
        STOCK("Stock"),
        ONE_WAY("One-Way LSD"),
        TWO_WAY("Two-Way LSD"),
        FULLY_CUSTOM("Fully Customisable LSD"),
    }

    enum class ForcedInduction(val label: String) {
        NONE("None / stock"),
        TURBO_LOW("Turbo — Low RPM"),
        TURBO_MED("Turbo — Medium RPM"),
        TURBO_HIGH("Turbo — High RPM"),
        SUPERCHARGER("Supercharger"),
    }

    @Serializable
    data class Suspension(
        val rideHeightF: Double? = null, val rideHeightR: Double? = null,   // mm
        val freqF: Double? = null, val freqR: Double? = null,               // Hz
        val arbF: Int? = null, val arbR: Int? = null,                       // level 1–10
        val compF: Int? = null, val compR: Int? = null,                     // damping compression %
        val extF: Int? = null, val extR: Int? = null,                       // damping expansion %
        val camberF: Double? = null, val camberR: Double? = null,           // deg (negative)
        val toeF: Double? = null, val toeR: Double? = null,                 // deg (- out / + in)
    )

    @Serializable
    data class Transmission(
        val finalDrive: Double? = null,
        val topSpeedKmh: Int? = null, // GT7's "top speed" auto-spread adjuster
        val gears: List<Double?> = List(8) { null },
    )

    @Serializable
    data class Differential(
        val initialF: Int? = null, val initialR: Int? = null,   // initial torque 5–60
        val accelF: Int? = null, val accelR: Int? = null,       // accel sensitivity 5–60
        val brakeF: Int? = null, val brakeR: Int? = null,       // braking sensitivity 5–60
        val frontRearSplit: String? = null,                     // AWD torque split e.g. "30:70"
    )

    @Serializable
    data class Aero(
        val front: Int? = null,  // downforce level
        val rear: Int? = null,
    )

    @Serializable
    data class Power(
        val ecuOutputPct: Int? = null,       // 70–100
        val powerRestrictorPct: Int? = null, // 70–100
        val ballastKg: Int? = null,          // 0–200
        val ballastPosition: Int? = null,    // −50 (front) … +50 (rear)
    )

    @Serializable
    data class Brakes(
        val balance: Int? = null, // −5 (front) … +5 (rear)
    )

    /** True when any value on the sheet has been filled in. */
    val hasAnyValues: Boolean
        get() = suspension != Suspension() || transmission != Transmission() ||
            differential != Differential() || aero != Aero() || power != Power() ||
            brakes != Brakes() || notes.isNotBlank() || parts != Parts()

    // ---- Formatting for the race-engineer briefing --------------------------

    fun toBriefingText(): String = buildString {
        fun f(v: Double?) = v?.let { String.format(Locale.US, if (it % 1.0 == 0.0) "%.0f" else "%.2f", it) }
        fun pair(label: String, a: Any?, b: Any?, unit: String = "") {
            if (a != null || b != null) appendLine("  - $label: ${a ?: "—"} / ${b ?: "—"}$unit (F/R)")
        }
        appendLine("Parts fitted: suspension ${parts.suspension.label} · transmission ${parts.transmission.label} · " +
            "diff ${parts.differential.label} · forced induction ${parts.forcedInduction.label}" +
            (if (parts.frontAero) " · front aero" else "") +
            (if (parts.rearWing) " · rear wing" else "") +
            (if (parts.brakeBalanceController) " · brake balance controller" else "") +
            (if (parts.ecuTuned) " · ECU" else "") +
            (if (parts.ballastFitted) " · ballast" else ""))
        if (parts.suspension == SuspensionKind.FULLY_CUSTOM || parts.suspension == SuspensionKind.HEIGHT_ADJUSTABLE) {
            appendLine("- Suspension:")
            pair("Ride height", f(suspension.rideHeightF), f(suspension.rideHeightR), " mm")
            pair("Natural frequency", f(suspension.freqF), f(suspension.freqR), " Hz")
            pair("Anti-roll bar", suspension.arbF, suspension.arbR)
            pair("Damping compression", suspension.compF, suspension.compR, " %")
            pair("Damping expansion", suspension.extF, suspension.extR, " %")
            pair("Camber", f(suspension.camberF), f(suspension.camberR), "°")
            pair("Toe", f(suspension.toeF), f(suspension.toeR), "°")
        }
        if (parts.transmission == TransmissionKind.FULLY_CUSTOM || parts.transmission == TransmissionKind.FULLY_CUSTOM_RACING) {
            appendLine("- Transmission:")
            transmission.topSpeedKmh?.let { appendLine("  - Top speed (auto-spread): $it km/h") }
            transmission.finalDrive?.let { appendLine("  - Final drive: ${f(it)}") }
            val setGears = transmission.gears.withIndex().filter { it.value != null }
            if (setGears.isNotEmpty())
                appendLine("  - Gears: " + setGears.joinToString(" · ") { (i, g) -> "${i + 1}: ${f(g)}" })
        }
        if (parts.differential != DiffKind.STOCK) {
            appendLine("- Differential (${parts.differential.label}):")
            pair("Initial torque", differential.initialF, differential.initialR)
            pair("Acceleration sensitivity", differential.accelF, differential.accelR)
            pair("Braking sensitivity", differential.brakeF, differential.brakeR)
            differential.frontRearSplit?.let { appendLine("  - F/R torque split: $it") }
        }
        if (parts.frontAero || parts.rearWing) {
            appendLine("- Downforce: front ${aero.front ?: "—"} · rear ${aero.rear ?: "—"}")
        }
        val p = power
        if (p != Power()) {
            appendLine("- Power/weight:" +
                (p.ecuOutputPct?.let { " ECU $it%" } ?: "") +
                (p.powerRestrictorPct?.let { " · restrictor $it%" } ?: "") +
                (p.ballastKg?.let { " · ballast ${it} kg @ ${p.ballastPosition ?: 0}" } ?: ""))
        }
        brakes.balance?.let { appendLine("- Brake balance: $it (${if (it < 0) "front" else if (it > 0) "rear" else "neutral"})") }
        if (notes.isNotBlank()) appendLine("- Notes: ${notes.trim()}")
    }
}
