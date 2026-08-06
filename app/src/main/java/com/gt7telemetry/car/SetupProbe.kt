package com.gt7telemetry.car

import com.gt7telemetry.Frame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Everything the telemetry stream reveals about the car's current setup —
 * read directly where GT7 broadcasts it, measured where it only broadcasts
 * the effect. GT7 never transmits the settings sheet itself (ARB levels,
 * damper %, camber, diff numbers, downforce clicks stay inside the game),
 * so this is deliberately split into "known" and "measured" so the Setup
 * screen and the AI briefing can be honest about which is which.
 */
data class MeasuredSetup(
    val carOrdinal: Int,
    // ---- Broadcast directly (settings-sheet values GT7 does send) --------
    /** Fitted gear ratios 1..8 (0 = not fitted) — actual transmission setting. */
    val gearRatios: List<Double>,
    /** Rev limiter, rpm. */
    val revLimiterRpm: Int,
    /** Shift-light onset, rpm. */
    val shiftLightRpm: Int,
    /** The game's own calculated top speed for this tune, km/h. */
    val calcMaxSpeedKmh: Int,
    /** Tyre radius per corner, metres [FL, FR, RL, RR]. */
    val tyreRadiusM: List<Double>,
    /** Turbo fitted (from the has-turbo flag). */
    val hasTurbo: Boolean,
    /** Electric drivetrain ('~' packet only; null = unknown). */
    val electric: Boolean?,
    /** Fuel tank capacity, litres. */
    val fuelCapacityL: Double,
    // ---- Measured (derived from how the car behaves) ----------------------
    /** Estimated final-drive ratio (median of rpm/wheel-speed ÷ gear ratio). */
    val finalDriveEst: Double?,
    /** Estimated speed at the rev limiter per fitted gear, km/h. */
    val speedAtRedlineKmh: List<Double>,
    /** Static ride height (body over road at standstill), mm. */
    val rideHeightStaticMm: Double?,
    /** Lowest body height seen under aero/braking load, mm. */
    val rideHeightMinMm: Double?,
    /** Suspension travel range used per corner, mm [FL, FR, RL, RR]. */
    val suspTravelMm: List<Double>,
    /** Peak boost seen, psi. */
    val maxBoostPsi: Double,
    /** Peak oil pressure seen, bar. */
    val maxOilPressureBar: Double,
    /** Top speed actually reached this session, km/h. */
    val maxSpeedSeenKmh: Double,
    /** Driver aids observed active at least once. */
    val tcsSeen: Boolean,
    val asmSeen: Boolean,
)

/**
 * Accumulates [MeasuredSetup] from the live stream. Fed every frame from
 * the telemetry service thread; resets itself when the car changes.
 *
 * The final-drive estimate works because engine rpm and wheel angular
 * velocity are both broadcast: total ratio = engine ω ÷ wheel ω in a
 * steady, engaged gear; dividing by the (broadcast) gear ratio leaves the
 * final drive. Medians over many samples make it robust to wheelspin and
 * shifts.
 */
object SetupProbe {

    private val _setup = MutableStateFlow<MeasuredSetup?>(null)
    val setup: StateFlow<MeasuredSetup?> = _setup.asStateFlow()

    private const val MAX_RATIO_SAMPLES = 400
    private const val PUBLISH_EVERY = 90 // frames (~1.5 s)

    private var carOrdinal = Int.MIN_VALUE
    private var frames = 0
    private var gearRatios = DoubleArray(0)
    private var revLimiter = 0
    private var shiftLight = 0
    private var calcMaxSpeed = 0
    private var tyreRadius = DoubleArray(4)
    private var hasTurbo = false
    private var electric: Boolean? = null
    private var fuelCapacity = 0.0
    private val totalRatioSamples = Array(8) { ArrayList<Double>() }
    private var rideStatic = ArrayList<Double>()
    private var bodyMin = Double.MAX_VALUE
    private var suspMin = DoubleArray(4) { Double.MAX_VALUE }
    private var suspMax = DoubleArray(4) { -Double.MAX_VALUE }
    private var maxBoost = 0.0
    private var maxOilPressure = 0.0
    private var maxSpeed = 0.0
    private var tcsSeen = false
    private var asmSeen = false

    fun feed(f: Frame) {
        if (f.carOrdinal != carOrdinal) reset(f.carOrdinal)
        if (!f.onTrack || f.paused) return
        frames++

        // Broadcast values — take the latest (they're static per tune).
        if (f.gearRatios.isNotEmpty()) gearRatios = f.gearRatios.copyOf()
        if (f.rpmMax > 100) revLimiter = f.rpmMax.toInt()
        if (f.redlineFrac in 0.1..1.0 && f.rpmMax > 100) shiftLight = (f.redlineFrac * f.rpmMax).toInt()
        if (f.calcMaxSpeedKmh in 1..2000) calcMaxSpeed = f.calcMaxSpeedKmh
        if (f.tyreRadiusM[0] > 0.01) tyreRadius = f.tyreRadiusM.copyOf()
        if (f.boostPsi > 0.5) hasTurbo = true
        f.carType?.let { electric = it == 4 }
        if (f.fuelLiters >= 0 && fuelCapacity == 0.0 && f.fuelPct > 0)
            fuelCapacity = f.fuelLiters / (f.fuelPct / 100.0)

        val speedMs = f.speedKmh / 3.6

        // Total-ratio sampling: engaged forward gear, rolling, tyres not
        // obviously spinning (slip ratio near 1 on all corners). The wire's
        // wheel-speed channel is angular velocity in rad/s (v = ω·r — see
        // the slip-ratio math in Packet), so the engine side converts to
        // rad/s too: total ratio = engine ω / wheel ω.
        val g = f.gear
        if (g in 1..8 && f.clutchEngagement > 0.99 && speedMs > 8 && f.rpm > 1500 &&
            f.slipRatio.all { it in 0.9..1.1 }
        ) {
            val wheelRadS = (abs(f.wheelRps[2]) + abs(f.wheelRps[3])) / 2.0
            if (wheelRadS > 1.0) {
                val samples = totalRatioSamples[g - 1]
                if (samples.size < MAX_RATIO_SAMPLES)
                    samples.add(f.rpm * 2.0 * Math.PI / 60.0 / wheelRadS)
            }
        }

        // Ride height: static at standstill; minimum under load when moving.
        val bodyMm = f.bodyHeightM * 1000.0
        if (bodyMm > 1.0) {
            if (speedMs < 0.3 && rideStatic.size < 300) rideStatic.add(bodyMm)
            if (speedMs > 14) bodyMin = minOf(bodyMin, bodyMm)
        }

        for (i in 0 until 4) {
            val s = f.suspensionM[i]
            suspMin[i] = minOf(suspMin[i], s)
            suspMax[i] = maxOf(suspMax[i], s)
        }
        maxBoost = maxOf(maxBoost, f.boostPsi)
        maxOilPressure = maxOf(maxOilPressure, f.oilPressureBar)
        maxSpeed = maxOf(maxSpeed, f.speedKmh)
        tcsSeen = tcsSeen || f.tcsActive
        asmSeen = asmSeen || f.asmActive

        if (frames % PUBLISH_EVERY == 0) _setup.value = snapshot()
    }

    fun clear() = reset(Int.MIN_VALUE)

    private fun reset(newCar: Int) {
        carOrdinal = newCar
        frames = 0
        gearRatios = DoubleArray(0)
        revLimiter = 0; shiftLight = 0; calcMaxSpeed = 0
        tyreRadius = DoubleArray(4)
        hasTurbo = false; electric = null; fuelCapacity = 0.0
        totalRatioSamples.forEach { it.clear() }
        rideStatic = ArrayList()
        bodyMin = Double.MAX_VALUE
        suspMin = DoubleArray(4) { Double.MAX_VALUE }
        suspMax = DoubleArray(4) { -Double.MAX_VALUE }
        maxBoost = 0.0; maxOilPressure = 0.0; maxSpeed = 0.0
        tcsSeen = false; asmSeen = false
        _setup.value = null
    }

    private fun median(xs: List<Double>): Double? =
        if (xs.isEmpty()) null else xs.sorted()[xs.size / 2]

    private fun snapshot(): MeasuredSetup {
        val fitted = gearRatios.toList()
        // Per-gear total ratio (rpm/wheel-rev) medians.
        val totals = (0 until 8).map { median(totalRatioSamples[it]) }
        // Final drive: total ÷ broadcast gear ratio, median across gears seen.
        val finals = (0 until 8).mapNotNull { i ->
            val t = totals[i] ?: return@mapNotNull null
            val r = fitted.getOrNull(i) ?: return@mapNotNull null
            if (r > 0.05) t / r else null
        }
        val finalDrive = median(finals)
        val rRear = tyreRadius.getOrNull(2)?.takeIf { it > 0.01 }
        val redlineSpeeds = (0 until 8).map { i ->
            val ratio = fitted.getOrNull(i) ?: 0.0
            // wheel ω at the limiter = engine ω / total ratio; v = ω·r.
            if (ratio <= 0.05 || revLimiter <= 0 || finalDrive == null || rRear == null) 0.0
            else revLimiter * 2 * Math.PI / 60.0 / (ratio * finalDrive) * rRear * 3.6
        }
        return MeasuredSetup(
            carOrdinal = carOrdinal,
            gearRatios = fitted,
            revLimiterRpm = revLimiter,
            shiftLightRpm = shiftLight,
            calcMaxSpeedKmh = calcMaxSpeed,
            tyreRadiusM = tyreRadius.toList(),
            hasTurbo = hasTurbo,
            electric = electric,
            fuelCapacityL = fuelCapacity,
            finalDriveEst = finalDrive,
            speedAtRedlineKmh = redlineSpeeds,
            rideHeightStaticMm = median(rideStatic),
            rideHeightMinMm = bodyMin.takeIf { it != Double.MAX_VALUE },
            suspTravelMm = (0 until 4).map {
                if (suspMax[it] < suspMin[it]) 0.0 else (suspMax[it] - suspMin[it]) * 1000.0
            },
            maxBoostPsi = maxBoost,
            maxOilPressureBar = maxOilPressure,
            maxSpeedSeenKmh = maxSpeed,
            tcsSeen = tcsSeen,
            asmSeen = asmSeen,
        )
    }
}
