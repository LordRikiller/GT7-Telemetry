package com.gt7telemetry

import com.gt7telemetry.logger.LapRecorder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the lap-splitting rules with synthetic frames: a lap records at
 * 60 Hz, completes when the counter increments (stamped with GT7's official
 * last-lap time), and anything non-consecutive is discarded, not recorded
 * short. Uses the parsed reference packet as a template frame.
 */
class LapRecorderTest {

    // The reference 296-byte packet from PacketTest, decoded (lap 3, on track).
    private val base: Frame = PacketTest().packetHex.let { s ->
        Packet.parse(
            ByteArray(s.length / 2) {
                ((s[it * 2].digitToInt(16) shl 4) or s[it * 2 + 1].digitToInt(16)).toByte()
            },
            Packet.SIZE,
        )!!
    }

    @Before fun reset() = LapRecorder.clear()
    @After fun cleanup() = LapRecorder.clear()

    /** One frame of lap [lap], [t] seconds in. */
    private fun at(lap: Int, t: Double, throttle: Double = 100.0, lastLap: Double = 0.0) =
        base.copy(lapNumber = lap, curLap = t, throttlePct = throttle, lastLap = lastLap)

    private fun driveLap(lap: Int, samples: Int, throttle: Double = 100.0) {
        for (i in 0 until samples) LapRecorder.feed(at(lap, i / 60.0, throttle))
    }

    @Test
    fun `a consecutive lap is published with the official time`() {
        driveLap(3, 400)
        assertEquals(0, LapRecorder.laps.value.size) // still recording
        assertEquals(3, LapRecorder.recordingLap.value)

        // First frame across the line: counter increments, lastLap = lap 3's time.
        LapRecorder.feed(at(4, 0.0, lastLap = 84.123))
        val laps = LapRecorder.laps.value
        assertEquals(1, laps.size)
        assertEquals(3, laps[0].lapNumber)
        assertEquals(84.123, laps[0].lapTimeS, 1e-6)
        assertEquals(400, laps[0].sampleCount)
        assertEquals(100.0, laps[0].fullThrottlePct, 1e-6)
        assertEquals(4, LapRecorder.recordingLap.value)
    }

    @Test
    fun `a lap jump discards the partial lap`() {
        driveLap(3, 400)
        LapRecorder.feed(at(1, 0.0, lastLap = 84.123)) // restart: 3 -> 1
        assertEquals(0, LapRecorder.laps.value.size)
        assertEquals(1, LapRecorder.recordingLap.value) // but lap 1 now records
    }

    @Test
    fun `too-short fragments are not recorded`() {
        driveLap(3, 50) // < 5 s of samples (e.g. joined mid-lap glitch)
        LapRecorder.feed(at(4, 0.0, lastLap = 30.0))
        assertEquals(0, LapRecorder.laps.value.size)
    }

    @Test
    fun `going off track stops recording`() {
        driveLap(3, 400)
        LapRecorder.feed(base.copy(onTrack = false, lapNumber = 0))
        assertEquals(0, LapRecorder.recordingLap.value)
        assertEquals(0, LapRecorder.laps.value.size)
    }

    @Test
    fun `bursty packet timing does not fabricate G spikes`() {
        // UDP frames often land in bursts: six samples 1 ms apart, then a
        // ~95 ms gap. With ±0.2 km/h of speed jitter on top of a steady
        // 1 g acceleration, a naive per-frame derivative reads ±5 g spikes;
        // the windowed derivative must stay near the true value.
        for (i in 0 until 400) {
            val t = (i / 6) * 0.1 + (i % 6) * 0.001
            val jitter = if (i % 2 == 0) 0.2 else -0.2
            val speed = 100.0 + 9.81 * 3.6 * t + jitter
            LapRecorder.feed(base.copy(lapNumber = 3, curLap = t, speedKmh = speed))
        }
        LapRecorder.feed(at(4, 0.0, lastLap = 60.0))
        val lap = LapRecorder.laps.value.single()
        var maxAbs = 0.0
        for (i in 0 until lap.sampleCount) maxAbs = maxOf(maxAbs, Math.abs(lap.longG[i].toDouble()))
        assertTrue("windowed long-G should stay near 1 g, was $maxAbs", maxAbs < 2.0)
        assertTrue("no fabricated braking (was ${lap.maxBrakingG})", lap.maxBrakingG < 1.0)
    }

    @Test
    fun `summaries derive from the traces`() {
        // Half the lap flat out, half coasting.
        for (i in 0 until 300) LapRecorder.feed(at(3, i / 60.0, throttle = 100.0))
        for (i in 300 until 600) LapRecorder.feed(at(3, i / 60.0, throttle = 0.0))
        LapRecorder.feed(at(4, 0.0, lastLap = 60.0))
        val lap = LapRecorder.laps.value.single()
        assertEquals(50.0, lap.fullThrottlePct, 1.0)
        assertTrue(lap.maxSpeedKmh > 100.0) // template frame carries 144 km/h
    }
}
