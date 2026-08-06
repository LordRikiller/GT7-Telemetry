package com.gt7telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the non-negotiable wire layout of the GT7 Simulator Interface
 * packet. The reference datagram below was built field-by-field at the
 * documented offsets (RPM @0x3C, speed @0x4C, tyre temps @0x60, flags @0x8E,
 * gears @0x90, car code @0x124, …) and encrypted with PyCryptodome's Salsa20
 * using GT7's key and nonce scheme — so a passing parse proves decryption,
 * magic check, offsets and unit conversions all at once. If a field is
 * reordered or an offset drifts, one of these breaks.
 */
class PacketTest {

    /**
     * 296-byte encrypted packet (iv seed 0x0BADF00D at 0x40). Plaintext:
     * magic G7S0 · posX 12.5 · posZ −34.25 · rpm 4000 · fuel 30/60 L ·
     * speed 40 m/s · turbo 1.827 · oil 6 bar/110 °C · water 85 °C ·
     * tyres 65.5/66/70.25/71 °C · packetId 123456 · lap 3/5 ·
     * best 83456 ms · last 84123 ms · alerts 6800..7500 rpm ·
     * flags 0x0011 (on-track, turbo) · gear 4 (suggested 5) ·
     * throttle 255 · brake 0 · wheelFL 100 rad/s × r 0.35 m · car 1234.
     */
    val packetHex = // shared with LapRecorderTest as a realistic template frame
        "dc26cf81303fa80ed53e4ce328a291ae00d71a47ad5c841cdaa98bb7ce61a02c" +
            "cda0b1ca871ced25d0b9b57faca4203a49f5cbbc284204036de7a63884adfed7" +
            "0df0ad0b3d38aaf83a0ae1d3fe660276ad2c95cb9b61a3a7f234faa25dd28fa3" +
            "20f4a75a47d62cdd3fb6168d785d0c908ee4ae7dd295b005010429800748885d" +
            "f295130e419885970fe6d571b44454e3c6d69173729a2ce7e7a4a08ceac28657" +
            "87b537cdf5358e9455aefc1a8e96157dc70a1367714390509e64e88b30d8ab2b" +
            "df30ecc14697034f8cb1537c7977e3b78930991f8fab1f728db1850f24fb76d6" +
            "c51786f10f4415253a89d478b69c426e9cf6dd3cda63f0c9110aad2c9eae2d51" +
            "7d756d8e78c4df723f01236e05da64ef6337af17c865720696b601d070b001cf" +
            "293d675f6a2d6f44"

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { ((s[it * 2].digitToInt(16) shl 4) or s[it * 2 + 1].digitToInt(16)).toByte() }

    private fun frame(): Frame = Packet.parse(hex(packetHex), Packet.SIZE)!!

    @Test
    fun `wrong length is rejected`() {
        assertNull(Packet.parse(ByteArray(100), 100))
        assertNull(Packet.parse(ByteArray(297), 297))
    }

    @Test
    fun `garbage of the right length fails the magic check`() {
        assertNull(Packet.parse(ByteArray(Packet.SIZE) { 0x5A }, Packet.SIZE))
    }

    @Test
    fun `fields decrypt and decode at their documented offsets with unit conversion`() {
        val f = frame()

        assertTrue(f.raceOn)          // flags bit0 set, not paused/loading
        assertTrue(f.onTrack)
        assertFalse(f.paused)
        assertEquals(4000.0, f.rpm, 1e-3)
        assertEquals(7500.0, f.rpmMax, 1e-3)          // rev-limiter alert max
        assertEquals(6800.0 / 7500.0, f.redlineFrac, 1e-6)
        assertEquals(144.0, f.speedKmh, 1e-3)         // 40 m/s * 3.6
        assertEquals(40.0 * 2.2369362920544, f.speedMph, 1e-3)
        assertEquals(4, f.gear)
        assertEquals("4", f.gearLabel)
        assertEquals(5, f.suggestedGear ?: -1)
        assertEquals(100.0, f.throttlePct, 1e-6)      // 255/255 * 100
        assertEquals(0.0, f.brakePct, 1e-6)
        assertEquals(50.0, f.fuelPct, 1e-3)           // 30 of 60 L
        assertEquals(110.0, f.oilTempC, 1e-3)
        assertEquals(85.0, f.waterTempC, 1e-3)
        assertEquals(6.0, f.oilPressureBar, 1e-3)
        // (1.827 - 1) bar of boost -> psi.
        assertEquals(0.827 * 14.503773773, f.boostPsi, 1e-3)
        assertEquals(65.5, f.tyreTempC[0], 1e-3)
        assertEquals(66.0, f.tyreTempC[1], 1e-3)
        assertEquals(70.25, f.tyreTempC[2], 1e-3)
        assertEquals(71.0, f.tyreTempC[3], 1e-3)
        assertEquals(123456L, f.packetId)
        assertEquals(3, f.lapNumber)
        assertEquals(5, f.totalLaps)
        assertEquals(83.456, f.bestLap, 1e-6)         // ms -> s
        assertEquals(84.123, f.lastLap, 1e-6)
        assertEquals(9, f.racePosition)
        assertEquals(12.5, f.posX, 1e-3)
        assertEquals(-34.25, f.posZ, 1e-3)
        assertEquals(1234, f.carOrdinal)
        // Wheel FL: |100 rad/s * 0.35 m| / 40 m/s = 0.875 slip ratio.
        assertEquals(0.875, f.slipRatio[0], 1e-3)
        // A 296-byte packet carries no extended channels.
        assertNull(f.steeringRad)
        assertNull(f.sway)
        assertNull(f.energyRecovery)
    }

    /**
     * The '~' heartbeat's 344-byte extended packet: same layout up to 0x128,
     * then steering / sway / heave / surge / energy-recovery — and a
     * different seed-XOR constant (0x55FABB4F instead of 0xDEADBEAF). Built
     * in-test from plaintext and encrypted with the app's own (separately
     * PyCryptodome-validated) Salsa20, exactly as the console would.
     */
    @Test
    fun `extended tilde packet decodes steering and chassis channels`() {
        val plain = ByteArray(Packet.SIZE_TILDE)
        val b = java.nio.ByteBuffer.wrap(plain).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.putInt(0x00, Packet.MAGIC)
        b.putFloat(0x30, 0.25f)      // yaw rate rad/s
        b.putFloat(0x38, 0.12f)      // body height m
        b.putFloat(0x3C, 5500f)      // rpm
        b.putFloat(0x4C, 50f)        // speed m/s
        b.putShort(0x74, 2)          // lap
        b.putShort(0x8E, 0x0001)     // flags: on-track
        b.put(0x90, ((15 shl 4) or 3).toByte()) // gear 3, no suggested gear (15)
        // Suspension travel FL..RR.
        floatArrayOf(0.051f, 0.052f, 0.063f, 0.064f).forEachIndexed { i, v -> b.putFloat(0xC4 + i * 4, v) }
        b.putFloat(0xF4, 0.5f)       // clutch pedal
        b.putFloat(0xF8, 0.9f)       // clutch engagement
        floatArrayOf(3.2f, 2.1f, 1.6f, 1.3f, 1.1f, 0.9f, 0f, 0f).forEachIndexed { i, v ->
            b.putFloat(0x104 + i * 4, v)
        }
        b.putInt(0x124, 4321)        // car code
        b.putFloat(0x128, -0.5f)     // steering wheel angle, rad (left)
        b.putFloat(0x130, 1.5f)      // sway
        b.putFloat(0x134, -0.2f)     // heave
        b.putFloat(0x138, 0.8f)      // surge
        b.putFloat(0x150, 42f)       // energy recovery

        // Encrypt like the console: Salsa20 over the whole datagram, nonce
        // from (seed ^ 0x55FABB4F) ‖ seed, seed left readable at 0x40.
        val key = "Simulator Interface Packet GT7 ver 0.0".toByteArray(Charsets.US_ASCII).copyOf(32)
        val seed = 0x0BADF00D
        val nonce = ByteArray(8)
        java.nio.ByteBuffer.wrap(nonce).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putInt(seed xor 0x55FABB4F).putInt(seed)
        val enc = Salsa20.xor(plain, plain.size, key, nonce)
        java.nio.ByteBuffer.wrap(enc).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(0x40, seed)

        val f = Packet.parse(enc, Packet.SIZE_TILDE)!!
        assertEquals(5500.0, f.rpm, 1e-3)
        assertEquals(180.0, f.speedKmh, 1e-3)            // 50 m/s
        assertEquals(3, f.gear)
        assertEquals(4321, f.carOrdinal)
        assertEquals(0.25, f.yawRateRadS, 1e-6)
        assertEquals(0.12, f.bodyHeightM, 1e-6)
        assertEquals(0.051, f.suspensionM[0], 1e-6)
        assertEquals(0.064, f.suspensionM[3], 1e-6)
        assertEquals(50.0, f.clutchPct, 1e-3)            // 0.5 -> 50 %
        assertEquals(0.9, f.clutchEngagement, 1e-6)
        assertEquals(3.2, f.gearRatios[0], 1e-6)
        assertEquals(0.9, f.gearRatios[5], 1e-6)
        assertEquals(-0.5, f.steeringRad!!, 1e-6)
        assertEquals(1.5, f.sway!!, 1e-6)
        assertEquals(-0.2, f.heave!!, 1e-6)
        assertEquals(0.8, f.surge!!, 1e-6)
        assertEquals(42.0, f.energyRecovery!!, 1e-6)
    }

    @Test
    fun `tilde packet with the legacy xor constant fails the magic check`() {
        // Same plaintext but encrypted with packet-A's 0xDEADBEAF constant:
        // parse() must reject it (it decrypts 344-byte packets with 0x55FABB4F).
        val plain = ByteArray(Packet.SIZE_TILDE)
        java.nio.ByteBuffer.wrap(plain).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(0x00, Packet.MAGIC)
        val key = "Simulator Interface Packet GT7 ver 0.0".toByteArray(Charsets.US_ASCII).copyOf(32)
        val seed = 0x0BADF00D
        val nonce = ByteArray(8)
        java.nio.ByteBuffer.wrap(nonce).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putInt(seed xor 0xDEADBEAF.toInt()).putInt(seed)
        val enc = Salsa20.xor(plain, plain.size, key, nonce)
        java.nio.ByteBuffer.wrap(enc).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(0x40, seed)
        assertNull(Packet.parse(enc, Packet.SIZE_TILDE))
    }

    @Test
    fun `gear nibble maps reverse and neutral`() {
        // Re-encrypt the reference packet with patched gear bytes via the
        // Salsa20 helper (xor with the same keystream keeps all other fields).
        fun withGear(nibble: Int): Frame {
            val enc = hex(packetHex)
            // Decrypt, patch, re-encrypt with the same seed-derived nonce.
            val key = "Simulator Interface Packet GT7 ver 0.0".toByteArray(Charsets.US_ASCII).copyOf(32)
            val seed = java.nio.ByteBuffer.wrap(enc, 0x40, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            val nonce = ByteArray(8)
            java.nio.ByteBuffer.wrap(nonce).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putInt(seed xor 0xDEADBEAF.toInt()).putInt(seed)
            val plain = Salsa20.xor(enc, enc.size, key, nonce)
            plain[0x90] = ((5 shl 4) or nibble).toByte()
            val re = Salsa20.xor(plain, plain.size, key, nonce)
            // Restore the plaintext seed bytes the console leaves readable.
            java.nio.ByteBuffer.wrap(re).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(0x40, seed)
            return Packet.parse(re, Packet.SIZE)!!
        }
        assertEquals("R", withGear(0).gearLabel)
        assertEquals("N", withGear(15).gearLabel)
        assertEquals("3", withGear(3).gearLabel)
    }

    @Test
    fun `lap timer estimates the running lap and freezes on pause`() {
        val f = frame() // lap 3, on track, not paused
        val timer = LapTimer()
        assertEquals(0.0, timer.update(f, 1_000L), 1e-6)      // lap 3 starts
        assertEquals(2.5, timer.update(f, 3_500L), 1e-6)      // 2.5 s in
        val paused = f.copy(paused = true)
        assertEquals(4.0, timer.update(paused, 5_000L), 1e-6) // freezes at 4 s
        assertEquals(4.0, timer.update(paused, 9_000L), 1e-6) // still 4 s
        assertEquals(4.0, timer.update(f, 9_500L), 1e-6)      // resume instant: still 4 s
        assertEquals(5.0, timer.update(f, 10_500L), 1e-6)     // clock runs again
        val nextLap = f.copy(lapNumber = 4)
        assertEquals(0.0, timer.update(nextLap, 11_000L), 1e-6) // new lap resets
        val offTrack = f.copy(onTrack = false, lapNumber = 0)
        assertEquals(0.0, timer.update(offTrack, 12_000L), 1e-6)
    }

    @Test
    fun `fuel tracker measures per-lap burn over consecutive laps only`() {
        val base = frame() // lap 3, fuelPct 50
        val tracker = FuelTracker()
        assertEquals(0.0, tracker.update(base.copy(lapNumber = 1, fuelPct = 60.0)), 1e-6)
        // Lap 1 -> 2 burned 5%.
        assertEquals(5.0, tracker.update(base.copy(lapNumber = 2, fuelPct = 55.0)), 1e-6)
        // Mid-lap frames keep reporting the last measured burn.
        assertEquals(5.0, tracker.update(base.copy(lapNumber = 2, fuelPct = 53.0)), 1e-6)
        // Lap 2 -> 3 burned 6%.
        assertEquals(6.0, tracker.update(base.copy(lapNumber = 3, fuelPct = 49.0)), 1e-6)
        // Restart (lap jumps back to 1): estimate resets.
        assertEquals(0.0, tracker.update(base.copy(lapNumber = 1, fuelPct = 100.0)), 1e-6)
        // Refuelled/EV lap (no burn) leaves the estimate unknown.
        assertEquals(0.0, tracker.update(base.copy(lapNumber = 2, fuelPct = 100.0)), 1e-6)
    }
}
