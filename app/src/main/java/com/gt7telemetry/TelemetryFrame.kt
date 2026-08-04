package com.gt7telemetry

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Gran Turismo 7 "Simulator Interface" packet decoder.
 *
 * GT7 streams packet A: exactly 296 bytes, little-endian, encrypted with
 * Salsa20. The key is the first 32 bytes of "Simulator Interface Packet GT7
 * ver 0.0"; the 8-byte nonce is derived from the seed the console writes in
 * plaintext at offset 0x40 of every datagram (iv2 = seed ^ 0xDEADBEAF, nonce
 * = LE(iv2) ‖ LE(seed)). A successful decrypt is proven by the "G7S0" magic
 * (0x47375330) at offset 0.
 *
 * Fields are read at their documented offsets (RPM @0x3C, speed m/s @0x4C,
 * tyre temps @0x60..0x6C, gears @0x90, car code @0x124, …) as mapped by the
 * GT7 community (Nenkai's SimulatorInterface docs / gt7dashboard).
 *
 * Wire units: speed m/s · tyre temps °C · lap times ms (−1 = no time) ·
 * turbo boost as (x−1) bar · oil pressure bar. Conversions to display units
 * live in [Frame] so they exist in exactly one place.
 */
object Packet {

    const val SIZE = 296
    const val MAGIC = 0x47375330 // "G7S0" little-endian

    private val KEY: ByteArray =
        "Simulator Interface Packet GT7 ver 0.0".toByteArray(Charsets.US_ASCII).copyOf(32)

    // ---- Flags at 0x8E --------------------------------------------------
    private const val FLAG_ON_TRACK = 1 shl 0
    private const val FLAG_PAUSED = 1 shl 1
    private const val FLAG_LOADING = 1 shl 2
    private const val FLAG_HAS_TURBO = 1 shl 4
    private const val FLAG_REV_LIMITER = 1 shl 5
    private const val FLAG_HANDBRAKE = 1 shl 6
    private const val FLAG_TCS = 1 shl 11

    private const val BAR_TO_PSI = 14.503773773

    /**
     * Decrypt + parse a raw datagram. Returns null if the length is not
     * exactly 296 bytes or the decrypted magic doesn't check out (stray
     * packets on the port are simply ignored rather than crashing the
     * receiver).
     */
    fun parse(data: ByteArray, length: Int): Frame? {
        if (length != SIZE) return null

        // The IV seed is readable pre-decryption at 0x40.
        val seedBuf = ByteBuffer.wrap(data, 0x40, 4).order(ByteOrder.LITTLE_ENDIAN)
        val iv1 = seedBuf.int
        val iv2 = iv1 xor 0xDEADBEAF.toInt()
        val nonce = ByteArray(8)
        ByteBuffer.wrap(nonce).order(ByteOrder.LITTLE_ENDIAN).putInt(iv2).putInt(iv1)

        val plain = Salsa20.xor(data, length, KEY, nonce)
        val b = ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN)
        if (b.getInt(0x00) != MAGIC) return null

        val posX = b.getFloat(0x04).toDouble()
        val posZ = b.getFloat(0x0C).toDouble()
        val rpm = b.getFloat(0x3C).toDouble()
        val fuelLevel = b.getFloat(0x44).toDouble()
        val fuelCapacity = b.getFloat(0x48).toDouble()
        val speedMs = b.getFloat(0x4C).toDouble()
        val turbo = b.getFloat(0x50).toDouble()
        val oilPressure = b.getFloat(0x54).toDouble()
        val waterTemp = b.getFloat(0x58).toDouble()
        val oilTemp = b.getFloat(0x5C).toDouble()
        val tFL = b.getFloat(0x60).toDouble()
        val tFR = b.getFloat(0x64).toDouble()
        val tRL = b.getFloat(0x68).toDouble()
        val tRR = b.getFloat(0x6C).toDouble()
        val packetId = b.getInt(0x70).toLong() and 0xFFFFFFFFL
        val lapCount = b.getShort(0x74).toInt()
        val totalLaps = b.getShort(0x76).toInt()
        val bestLapMs = b.getInt(0x78)
        val lastLapMs = b.getInt(0x7C)
        val preRacePosition = b.getShort(0x84).toInt()
        val rpmAlertMin = (b.getShort(0x88).toInt() and 0xFFFF).toDouble()
        val rpmAlertMax = (b.getShort(0x8A).toInt() and 0xFFFF).toDouble()
        val flags = b.getShort(0x8E).toInt() and 0xFFFF
        val gearByte = b.get(0x90).toInt() and 0xFF
        val throttle = b.get(0x91).toInt() and 0xFF
        val brake = b.get(0x92).toInt() and 0xFF
        val carCode = b.getInt(0x124)

        // Wheel speed vs car speed -> slip ratio per corner (>1 = wheelspin).
        val slip = DoubleArray(4) { i ->
            val revPerS = b.getFloat(0xA4 + i * 4).toDouble()
            val radius = b.getFloat(0xB4 + i * 4).toDouble()
            val wheelMs = kotlin.math.abs(revPerS * radius)
            if (speedMs > 1.0) wheelMs / speedMs else 0.0
        }

        val gear = gearByte and 0x0F
        val suggested = (gearByte shr 4) and 0x0F
        val onTrack = flags and FLAG_ON_TRACK != 0
        val paused = flags and FLAG_PAUSED != 0
        val loading = flags and FLAG_LOADING != 0

        // GT7 reports the rev-limiter window; when the car isn't loaded yet
        // both are 0, so fall back to a sane tach range.
        val rpmMax = if (rpmAlertMax > 100) rpmAlertMax else 8000.0
        val redlineFrac = if (rpmAlertMax > 100 && rpmAlertMin in 1.0..rpmAlertMax)
            (rpmAlertMin / rpmAlertMax) else 0.855

        return Frame(
            raceOn = onTrack && !paused && !loading,
            onTrack = onTrack,
            paused = paused,
            packetId = packetId,
            speedKmh = speedMs * 3.6,
            speedMph = speedMs * 2.2369362920544,
            rpm = rpm,
            rpmMax = rpmMax,
            rpmPct = (rpm / rpmMax).coerceIn(0.0, 1.0) * 100.0,
            redlineFrac = redlineFrac,
            revLimiterActive = flags and FLAG_REV_LIMITER != 0,
            gear = gear,
            gearLabel = when {
                gear == 0 -> "R"
                gear == 15 -> "N"
                else -> gear.toString()
            },
            suggestedGear = suggested.takeIf { it != 15 },
            throttlePct = throttle / 255.0 * 100.0,
            brakePct = brake / 255.0 * 100.0,
            handbrake = flags and FLAG_HANDBRAKE != 0,
            tcsActive = flags and FLAG_TCS != 0,
            fuelPct = if (fuelCapacity > 0) (fuelLevel / fuelCapacity * 100.0).coerceIn(0.0, 100.0) else 0.0,
            fuelLiters = fuelLevel,
            oilTempC = oilTemp,
            waterTempC = waterTemp,
            oilPressureBar = oilPressure,
            // Turbo boost is broadcast as manifold pressure ratio; (x-1) bar of
            // boost, negative = vacuum. Cars without a turbo sit at ~0.
            boostPsi = if (flags and FLAG_HAS_TURBO != 0) (turbo - 1.0) * BAR_TO_PSI else 0.0,
            tyreTempC = doubleArrayOf(tFL, tFR, tRL, tRR),
            slipRatio = slip,
            curLap = 0.0, // GT7 doesn't broadcast a running lap clock; LapTimer estimates it.
            lastLap = lapMs(lastLapMs),
            bestLap = lapMs(bestLapMs),
            lapNumber = lapCount,
            totalLaps = totalLaps,
            racePosition = preRacePosition,
            posX = posX,
            posZ = posZ,
            carOrdinal = carCode,
        )
    }

    /** GT7 lap times are int milliseconds with -1 (or 0) meaning "no time". */
    private fun lapMs(ms: Int): Double = if (ms > 0) ms / 1000.0 else 0.0
}

/** A parsed frame in display-friendly units. Arrays are [FL, FR, RL, RR]. */
data class Frame(
    val raceOn: Boolean,
    val onTrack: Boolean,
    val paused: Boolean,
    val packetId: Long,
    val speedKmh: Double,
    val speedMph: Double,
    val rpm: Double,
    val rpmMax: Double,
    val rpmPct: Double,
    val redlineFrac: Double,
    val revLimiterActive: Boolean,
    val gear: Int,
    val gearLabel: String,
    val suggestedGear: Int?,
    val throttlePct: Double,
    val brakePct: Double,
    val handbrake: Boolean,
    val tcsActive: Boolean,
    val fuelPct: Double,
    val fuelLiters: Double,
    val oilTempC: Double,
    val waterTempC: Double,
    val oilPressureBar: Double,
    val boostPsi: Double,
    val tyreTempC: DoubleArray,
    val slipRatio: DoubleArray,
    val curLap: Double,
    val lastLap: Double,
    val bestLap: Double,
    val lapNumber: Int,
    val totalLaps: Int,
    val racePosition: Int,
    val posX: Double,
    val posZ: Double,
    val carOrdinal: Int,
) {
    // Arrays in a data class break equals()/hashCode(); we never compare
    // Frames for equality, so the generated ones are fine to leave.
}

/**
 * GT7 broadcasts best/last lap but no running lap clock, so we keep one:
 * the clock (re)starts whenever the lap counter changes and freezes while
 * the game is paused or the car leaves the track.
 */
class LapTimer {
    private var lapCount = Int.MIN_VALUE
    private var startedAt = 0L
    private var pausedAt = 0L

    /** Returns the estimated current-lap seconds for [frame], updating state. */
    fun update(frame: Frame, nowMs: Long = System.currentTimeMillis()): Double {
        if (!frame.onTrack || frame.lapNumber <= 0) {
            lapCount = Int.MIN_VALUE
            return 0.0
        }
        if (frame.lapNumber != lapCount) {
            lapCount = frame.lapNumber
            startedAt = nowMs
            pausedAt = 0L
        }
        if (frame.paused) {
            if (pausedAt == 0L) pausedAt = nowMs
            return (pausedAt - startedAt) / 1000.0
        }
        if (pausedAt != 0L) {
            startedAt += nowMs - pausedAt // shift start by the paused span
            pausedAt = 0L
        }
        return (nowMs - startedAt) / 1000.0
    }
}
