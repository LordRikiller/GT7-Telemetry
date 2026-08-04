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
    private val packetHex =
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
}
