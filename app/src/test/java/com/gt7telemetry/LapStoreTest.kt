package com.gt7telemetry

import com.gt7telemetry.logger.LapStore
import com.gt7telemetry.logger.RecordedLap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Round-trips a lap through the on-disk binary format. */
class LapStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun sampleLap(n: Int = 500): RecordedLap {
        fun ch(seed: Float) = FloatArray(n) { seed + it * 0.25f }
        return RecordedLap(
            lapNumber = 7, carOrdinal = 3241, lapTimeS = 111.489,
            recordedAtMs = 1_754_600_000_000L, tyres = "Racing Soft", sampleCount = n,
            t = FloatArray(n) { it / 60f }, speedKmh = ch(100f), throttlePct = ch(0f),
            brakePct = ch(1f), steerDeg = FloatArray(n) { if (it % 7 == 0) Float.NaN else it * 0.1f },
            rpm = ch(3000f), gear = FloatArray(n) { (it % 6 + 1).toFloat() },
            posX = ch(-500f), posZ = ch(250f), latG = ch(-1f), longG = ch(0.5f),
            clutchPct = ch(2f), tyreFL = ch(60f), tyreFR = ch(61f), tyreRL = ch(62f), tyreRR = ch(63f),
        )
    }

    @Test
    fun `write then read returns an identical lap`() {
        val file = tmp.newFile("lap.gt7lap")
        val lap = sampleLap()
        LapStore.writeLap(file, lap)

        val meta = LapStore.readMeta(file)
        assertEquals(7, meta.lapNumber)
        assertEquals(3241, meta.carOrdinal)
        assertEquals(111.489, meta.lapTimeS, 1e-9)
        assertEquals(1_754_600_000_000L, meta.recordedAtMs)
        assertEquals("Racing Soft", meta.tyres)
        assertEquals(lap.maxSpeedKmh, meta.maxSpeedKmh, 1e-3)

        val back = LapStore.readLap(file)
        assertEquals(lap.sampleCount, back.sampleCount)
        assertEquals(lap.tyres, back.tyres)
        assertArrayEquals(lap.t, back.t, 0f)
        assertArrayEquals(lap.speedKmh, back.speedKmh, 0f)
        assertArrayEquals(lap.throttlePct, back.throttlePct, 0f)
        assertArrayEquals(lap.steerDeg, back.steerDeg, 0f) // NaNs compare equal in assertArrayEquals
        assertArrayEquals(lap.rpm, back.rpm, 0f)
        assertArrayEquals(lap.gear, back.gear, 0f)
        assertArrayEquals(lap.latG, back.latG, 0f)
        assertArrayEquals(lap.longG, back.longG, 0f)
        assertArrayEquals(lap.tyreFL, back.tyreFL, 0f)
        assertArrayEquals(lap.tyreRR, back.tyreRR, 0f)
        assertEquals(lap.avgTyreTempF!!, back.avgTyreTempF!!, 1e-3)
    }

    @Test
    fun `laps without tyre channels round-trip as NaN padding`() {
        val n = 100
        val lap = sampleLap(n).let {
            RecordedLap(
                lapNumber = it.lapNumber, carOrdinal = it.carOrdinal, lapTimeS = it.lapTimeS,
                recordedAtMs = it.recordedAtMs, tyres = "", sampleCount = n,
                t = it.t, speedKmh = it.speedKmh, throttlePct = it.throttlePct,
                brakePct = it.brakePct, steerDeg = it.steerDeg, rpm = it.rpm, gear = it.gear,
                posX = it.posX, posZ = it.posZ, latG = it.latG, longG = it.longG,
                clutchPct = it.clutchPct, // tyre channels left empty
            )
        }
        val file = tmp.newFile("old.gt7lap")
        LapStore.writeLap(file, lap)
        val back = LapStore.readLap(file)
        assertEquals(n, back.tyreFL.size)
        assertEquals(true, back.tyreFL.all { it.isNaN() })
    }
}
