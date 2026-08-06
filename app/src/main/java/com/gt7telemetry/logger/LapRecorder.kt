package com.gt7telemetry.logger

import com.gt7telemetry.Frame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max

/**
 * A completed lap's full trace, stored column-wise (one FloatArray per
 * channel, all `sampleCount` long). Column storage keeps a 60 Hz × 2-minute
 * lap around 350 KB and lets the UI draw a channel with zero per-sample
 * allocation.
 *
 * `steerDeg` is NaN when the console didn't send the extended packet
 * (GT7 < 1.42); everything else is always present.
 */
class RecordedLap(
    val lapNumber: Int,
    val carOrdinal: Int,
    /** Official lap time in seconds (from GT7's last-lap field); 0 = invalid lap. */
    val lapTimeS: Double,
    val sampleCount: Int,
    /** Seconds since lap start. */
    val t: FloatArray,
    val speedKmh: FloatArray,
    val throttlePct: FloatArray,
    val brakePct: FloatArray,
    /** Steering wheel angle, degrees, negative = left. NaN if unavailable. */
    val steerDeg: FloatArray,
    val rpm: FloatArray,
    val gear: FloatArray,
    val posX: FloatArray,
    val posZ: FloatArray,
    /** Lateral acceleration in g (yaw rate × speed), signed. */
    val latG: FloatArray,
    /** Longitudinal acceleration in g (Δspeed), + = accel, − = braking. */
    val longG: FloatArray,
    val clutchPct: FloatArray,
) {
    val maxSpeedKmh: Double by lazy { (0 until sampleCount).maxOfOrNull { speedKmh[it].toDouble() } ?: 0.0 }
    val minSpeedKmh: Double by lazy { (0 until sampleCount).minOfOrNull { speedKmh[it].toDouble() } ?: 0.0 }
    val avgSpeedKmh: Double by lazy {
        if (sampleCount == 0) 0.0 else (0 until sampleCount).sumOf { speedKmh[it].toDouble() } / sampleCount
    }
    /** Share of the lap at (nearly) full throttle, 0–100. */
    val fullThrottlePct: Double by lazy { pctWhere { throttlePct[it] > 95f } }
    /** Share of the lap on the brakes, 0–100. */
    val brakingPct: Double by lazy { pctWhere { brakePct[it] > 5f } }
    /** Share of the lap coasting (neither pedal), 0–100. */
    val coastingPct: Double by lazy { pctWhere { throttlePct[it] <= 5f && brakePct[it] <= 5f } }
    val maxLatG: Double by lazy { (0 until sampleCount).maxOfOrNull { abs(latG[it]).toDouble() } ?: 0.0 }
    val maxBrakingG: Double by lazy { (0 until sampleCount).maxOfOrNull { (-longG[it]).toDouble() } ?: 0.0 }
    val hasSteering: Boolean by lazy { (0 until sampleCount).any { !steerDeg[it].isNaN() } }

    private inline fun pctWhere(pred: (Int) -> Boolean): Double {
        if (sampleCount == 0) return 0.0
        var n = 0
        for (i in 0 until sampleCount) if (pred(i)) n++
        return n * 100.0 / sampleCount
    }
}

/**
 * Process-wide lap logger fed from the telemetry service's receive thread.
 *
 * Samples every frame (60 Hz) while the car is on track, splits laps on
 * GT7's lap counter, stamps each completed lap with the official time the
 * game reports, and publishes an immutable lap list for the UI. Laps that
 * don't follow the previous one (restarts, teleport to pits) are discarded
 * rather than recorded short. Only [feed] mutates state and it's called
 * from a single thread; readers see immutable snapshots via StateFlow.
 */
object LapRecorder {

    /** Retain at most this many laps; the session-best lap is never evicted. */
    private const val MAX_LAPS = 16

    /** Stop appending past this many samples (~12 min at 60 Hz) as a runaway guard. */
    private const val MAX_SAMPLES = 45_000

    /**
     * Minimum time base for the speed derivative. UDP packets arrive in
     * bursts, so consecutive-frame dt can be a millisecond — differencing
     * over that produces physically impossible ±30 G spikes. Differencing
     * against a sample at least this far back keeps long-G honest.
     */
    private const val LONG_G_WINDOW_S = 0.08

    /** Hard cap — nothing on wheels brakes at more than this. */
    private const val LONG_G_MAX = 6.0

    /** Smoothing factor for lateral G (yaw rate × speed is spiky over kerbs). */
    private const val LAT_G_EMA = 0.30

    private val _laps = MutableStateFlow<List<RecordedLap>>(emptyList())
    val laps: StateFlow<List<RecordedLap>> = _laps.asStateFlow()

    /** Lap number currently being recorded (0 = idle); drives the REC pill. */
    private val _recordingLap = MutableStateFlow(0)
    val recordingLap: StateFlow<Int> = _recordingLap.asStateFlow()

    private var builder: Builder? = null
    private var prevT = -1.0

    // Short (t, speed) history for the long-G derivative window.
    private const val HIST_CAP = 32
    private val histT = DoubleArray(HIST_CAP)
    private val histV = DoubleArray(HIST_CAP)
    private var histHead = 0
    private var histSize = 0
    private var latGSmooth = Double.NaN

    fun feed(frame: Frame) {
        if (!frame.onTrack || frame.lapNumber <= 0) {
            discardCurrent()
            return
        }
        if (frame.paused) return

        val cur = builder
        if (cur == null || frame.lapNumber != cur.lapNumber) {
            // Lap boundary: the frame after the line carries the finished
            // lap's official time in lastLap. Only a directly consecutive
            // lap is a genuinely completed one.
            if (cur != null && frame.lapNumber == cur.lapNumber + 1 && cur.size > 300) {
                publish(cur.build(lapTimeS = frame.lastLap))
            }
            builder = Builder(frame.lapNumber, frame.carOrdinal)
            _recordingLap.value = frame.lapNumber
            prevT = -1.0
            histSize = 0
            histHead = 0
            latGSmooth = Double.NaN
        }

        val b = builder ?: return
        val t = frame.curLap
        if (t <= prevT || b.size >= MAX_SAMPLES) return // duplicate/stalled clock
        val speedMs = frame.speedKmh / 3.6

        // Long G: difference against the most recent sample ≥ the window back.
        var longG = 0.0
        for (k in 1 until histSize + 1) {
            val idx = (histHead - k + HIST_CAP * 2) % HIST_CAP
            val dt = t - histT[idx]
            if (dt >= LONG_G_WINDOW_S) {
                longG = ((speedMs - histV[idx]) / dt / 9.81).coerceIn(-LONG_G_MAX, LONG_G_MAX)
                break
            }
        }
        histT[histHead] = t
        histV[histHead] = speedMs
        histHead = (histHead + 1) % HIST_CAP
        if (histSize < HIST_CAP) histSize++

        val latRaw = frame.yawRateRadS * speedMs / 9.81
        latGSmooth = if (latGSmooth.isNaN()) latRaw
        else latGSmooth + (latRaw - latGSmooth) * LAT_G_EMA

        b.add(frame, t, latGSmooth, longG)
        prevT = t
    }

    /** Drop everything (UI "clear session"). */
    fun clear() {
        discardCurrent()
        _laps.value = emptyList()
    }

    private fun discardCurrent() {
        builder = null
        if (_recordingLap.value != 0) _recordingLap.value = 0
    }

    private fun publish(lap: RecordedLap) {
        val all = _laps.value + lap
        _laps.value = if (all.size <= MAX_LAPS) all else {
            val best = all.filter { it.lapTimeS > 0 }.minByOrNull { it.lapTimeS }
            val oldestDroppable = all.first { it !== best }
            all - oldestDroppable
        }
    }

    /** Growable column store for the in-progress lap. */
    private class Builder(val lapNumber: Int, val carOrdinal: Int) {
        var size = 0; private set
        private var cap = 8192
        private var t = FloatArray(cap)
        private var speed = FloatArray(cap)
        private var thr = FloatArray(cap)
        private var brk = FloatArray(cap)
        private var steer = FloatArray(cap)
        private var rpm = FloatArray(cap)
        private var gear = FloatArray(cap)
        private var px = FloatArray(cap)
        private var pz = FloatArray(cap)
        private var lat = FloatArray(cap)
        private var lon = FloatArray(cap)
        private var clu = FloatArray(cap)

        fun add(f: Frame, ts: Double, latG: Double, longG: Double) {
            if (size == cap) grow()
            t[size] = ts.toFloat()
            speed[size] = f.speedKmh.toFloat()
            thr[size] = f.throttlePct.toFloat()
            brk[size] = f.brakePct.toFloat()
            steer[size] = f.steeringRad?.let { Math.toDegrees(it).toFloat() } ?: Float.NaN
            rpm[size] = f.rpm.toFloat()
            gear[size] = f.gear.toFloat()
            px[size] = f.posX.toFloat()
            pz[size] = f.posZ.toFloat()
            lat[size] = latG.toFloat()
            lon[size] = longG.toFloat()
            clu[size] = f.clutchPct.toFloat()
            size++
        }

        private fun grow() {
            cap = max(cap * 2, 8192)
            t = t.copyOf(cap); speed = speed.copyOf(cap); thr = thr.copyOf(cap)
            brk = brk.copyOf(cap); steer = steer.copyOf(cap); rpm = rpm.copyOf(cap)
            gear = gear.copyOf(cap); px = px.copyOf(cap); pz = pz.copyOf(cap)
            lat = lat.copyOf(cap); lon = lon.copyOf(cap); clu = clu.copyOf(cap)
        }

        fun build(lapTimeS: Double) = RecordedLap(
            lapNumber = lapNumber,
            carOrdinal = carOrdinal,
            lapTimeS = lapTimeS,
            sampleCount = size,
            t = t.copyOf(size),
            speedKmh = speed.copyOf(size),
            throttlePct = thr.copyOf(size),
            brakePct = brk.copyOf(size),
            steerDeg = steer.copyOf(size),
            rpm = rpm.copyOf(size),
            gear = gear.copyOf(size),
            posX = px.copyOf(size),
            posZ = pz.copyOf(size),
            latG = lat.copyOf(size),
            longG = lon.copyOf(size),
            clutchPct = clu.copyOf(size),
        )
    }
}
