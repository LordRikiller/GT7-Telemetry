package com.gt7telemetry

/**
 * Minimal Salsa20 stream cipher (20 rounds, 32-byte key, 8-byte nonce) — just
 * enough to decrypt GT7's Simulator Interface packets, with no crypto
 * dependency. Pure JVM so the packet parser is unit-testable off-device.
 *
 * Validated in Salsa20Test against keystream produced by PyCryptodome.
 */
object Salsa20 {

    // "expand 32-byte k" — the sigma constants for a 256-bit key.
    private const val C0 = 0x61707865
    private const val C1 = 0x3320646e
    private const val C2 = 0x79622d32
    private const val C3 = 0x6b206574

    private fun le32(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or
            ((b[i + 1].toInt() and 0xFF) shl 8) or
            ((b[i + 2].toInt() and 0xFF) shl 16) or
            ((b[i + 3].toInt() and 0xFF) shl 24)

    /**
     * XOR [data] (first [length] bytes) with the Salsa20 keystream for
     * [key] (32 bytes) and [nonce] (8 bytes), block counter starting at 0.
     * Returns a new array; the input is left untouched.
     */
    fun xor(data: ByteArray, length: Int, key: ByteArray, nonce: ByteArray): ByteArray {
        require(key.size == 32) { "Salsa20 key must be 32 bytes" }
        require(nonce.size == 8) { "Salsa20 nonce must be 8 bytes" }

        val out = data.copyOf(length)
        val state = IntArray(16)
        val working = IntArray(16)
        val block = ByteArray(64)

        state[0] = C0
        state[1] = le32(key, 0)
        state[2] = le32(key, 4)
        state[3] = le32(key, 8)
        state[4] = le32(key, 12)
        state[5] = C1
        state[6] = le32(nonce, 0)
        state[7] = le32(nonce, 4)
        state[8] = 0 // counter low
        state[9] = 0 // counter high
        state[10] = C2
        state[11] = le32(key, 16)
        state[12] = le32(key, 20)
        state[13] = le32(key, 24)
        state[14] = le32(key, 28)
        state[15] = C3

        var offset = 0
        var counter = 0L
        while (offset < length) {
            state[8] = counter.toInt()
            state[9] = (counter ushr 32).toInt()
            core(state, working, block)
            val n = minOf(64, length - offset)
            for (i in 0 until n) {
                out[offset + i] = (out[offset + i].toInt() xor block[i].toInt()).toByte()
            }
            offset += n
            counter++
        }
        return out
    }

    /** One Salsa20/20 block: doubleround x10, add input, serialize LE. */
    private fun core(input: IntArray, x: IntArray, out: ByteArray) {
        System.arraycopy(input, 0, x, 0, 16)
        repeat(10) {
            // column round
            x[4] = x[4] xor (x[0] + x[12]).rotateLeft(7)
            x[8] = x[8] xor (x[4] + x[0]).rotateLeft(9)
            x[12] = x[12] xor (x[8] + x[4]).rotateLeft(13)
            x[0] = x[0] xor (x[12] + x[8]).rotateLeft(18)
            x[9] = x[9] xor (x[5] + x[1]).rotateLeft(7)
            x[13] = x[13] xor (x[9] + x[5]).rotateLeft(9)
            x[1] = x[1] xor (x[13] + x[9]).rotateLeft(13)
            x[5] = x[5] xor (x[1] + x[13]).rotateLeft(18)
            x[14] = x[14] xor (x[10] + x[6]).rotateLeft(7)
            x[2] = x[2] xor (x[14] + x[10]).rotateLeft(9)
            x[6] = x[6] xor (x[2] + x[14]).rotateLeft(13)
            x[10] = x[10] xor (x[6] + x[2]).rotateLeft(18)
            x[3] = x[3] xor (x[15] + x[11]).rotateLeft(7)
            x[7] = x[7] xor (x[3] + x[15]).rotateLeft(9)
            x[11] = x[11] xor (x[7] + x[3]).rotateLeft(13)
            x[15] = x[15] xor (x[11] + x[7]).rotateLeft(18)
            // row round
            x[1] = x[1] xor (x[0] + x[3]).rotateLeft(7)
            x[2] = x[2] xor (x[1] + x[0]).rotateLeft(9)
            x[3] = x[3] xor (x[2] + x[1]).rotateLeft(13)
            x[0] = x[0] xor (x[3] + x[2]).rotateLeft(18)
            x[6] = x[6] xor (x[5] + x[4]).rotateLeft(7)
            x[7] = x[7] xor (x[6] + x[5]).rotateLeft(9)
            x[4] = x[4] xor (x[7] + x[6]).rotateLeft(13)
            x[5] = x[5] xor (x[4] + x[7]).rotateLeft(18)
            x[11] = x[11] xor (x[10] + x[9]).rotateLeft(7)
            x[8] = x[8] xor (x[11] + x[10]).rotateLeft(9)
            x[9] = x[9] xor (x[8] + x[11]).rotateLeft(13)
            x[10] = x[10] xor (x[9] + x[8]).rotateLeft(18)
            x[12] = x[12] xor (x[15] + x[14]).rotateLeft(7)
            x[13] = x[13] xor (x[12] + x[15]).rotateLeft(9)
            x[14] = x[14] xor (x[13] + x[12]).rotateLeft(13)
            x[15] = x[15] xor (x[14] + x[13]).rotateLeft(18)
        }
        for (i in 0 until 16) {
            val v = x[i] + input[i]
            out[i * 4] = v.toByte()
            out[i * 4 + 1] = (v ushr 8).toByte()
            out[i * 4 + 2] = (v ushr 16).toByte()
            out[i * 4 + 3] = (v ushr 24).toByte()
        }
    }
}
