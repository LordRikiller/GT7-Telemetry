package com.gt7telemetry

import com.gt7telemetry.car.SetupProbe
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Feeds synthetic frames with a known drivetrain (gear ratios × final
 * drive, rad/s wheel channel) and checks the probe recovers the final
 * drive, per-gear redline speeds and static ride height.
 */
class SetupProbeTest {

    private val base: Frame = PacketTest().packetHex.let { s ->
        Packet.parse(
            ByteArray(s.length / 2) {
                ((s[it * 2].digitToInt(16) shl 4) or s[it * 2 + 1].digitToInt(16)).toByte()
            },
            Packet.SIZE,
        )!!
    }

    private val ratios = doubleArrayOf(3.0, 2.0, 1.5, 1.2, 1.0, 0.8, 0.0, 0.0)
    private val finalDrive = 4.1
    private val rTyre = 0.33

    @Before fun reset() = SetupProbe.clear()
    @After fun cleanup() = SetupProbe.clear()

    /** A steady frame in [gear] at [wheelRadS], consistent rpm/speed, no slip. */
    private fun steady(gear: Int, wheelRadS: Double, speedKmh0: Double? = null) = base.copy(
        gear = gear,
        gearRatios = ratios,
        wheelRps = DoubleArray(4) { wheelRadS },
        tyreRadiusM = DoubleArray(4) { rTyre },
        slipRatio = DoubleArray(4) { 1.0 },
        clutchEngagement = 1.0,
        rpm = wheelRadS * ratios[gear - 1] * finalDrive * 60.0 / (2 * Math.PI),
        speedKmh = speedKmh0 ?: (wheelRadS * rTyre * 3.6),
        bodyHeightM = 0.093,
    )

    @Test
    fun `final drive and redline speeds recover from steady driving`() {
        // ~200 steady frames across three gears.
        for (i in 0 until 100) SetupProbe.feed(steady(3, 60.0 + i * 0.1))
        for (i in 0 until 100) SetupProbe.feed(steady(4, 70.0 + i * 0.1))
        for (i in 0 until 100) SetupProbe.feed(steady(5, 80.0 + i * 0.1))

        val s = SetupProbe.setup.value
        assertNotNull(s)
        assertEquals(finalDrive, s!!.finalDriveEst!!, 0.02)
        assertEquals(3.0, s.gearRatios[0], 1e-6) // broadcast ratio passthrough

        // Speed at limiter in 5th: ω_wheel = limiter·2π/60 / (1.0 × 4.1); v = ω·r.
        val expected5 = s.revLimiterRpm * 2 * Math.PI / 60.0 / (1.0 * finalDrive) * rTyre * 3.6
        assertEquals(expected5, s.speedAtRedlineKmh[4], 1.0)
        // Unfitted gears report no speed.
        assertEquals(0.0, s.speedAtRedlineKmh[6], 1e-6)
    }

    @Test
    fun `static ride height comes from standstill frames`() {
        // Standstill on the grid (speed ~0), then driving.
        for (i in 0 until 100) SetupProbe.feed(steady(1, 0.1, speedKmh0 = 0.0).copy(bodyHeightM = 0.0912))
        for (i in 0 until 100) SetupProbe.feed(steady(3, 60.0).copy(bodyHeightM = 0.074))

        val s = SetupProbe.setup.value!!
        assertEquals(91.2, s.rideHeightStaticMm!!, 0.5)
        assertEquals(74.0, s.rideHeightMinMm!!, 0.5)
    }

    @Test
    fun `changing car resets the probe`() {
        for (i in 0 until 100) SetupProbe.feed(steady(3, 60.0))
        assertNotNull(SetupProbe.setup.value)
        SetupProbe.feed(steady(3, 60.0).copy(carOrdinal = base.carOrdinal + 1))
        // New car: previous measurements must not leak.
        val s = SetupProbe.setup.value
        assertTrue(s == null || s.finalDriveEst == null)
    }
}
