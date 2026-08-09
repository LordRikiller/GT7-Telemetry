package com.gt7telemetry

import com.gt7telemetry.logger.RecordedLap
import com.gt7telemetry.track.TrackStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class TrackStoreTest {

    /** A synthetic lap: a circle of radius [r] centred on ([cx], [cz]). */
    private fun circleLap(cx: Float, cz: Float, r: Double, samples: Int = 600): RecordedLap {
        val px = FloatArray(samples); val pz = FloatArray(samples); val py = FloatArray(samples)
        for (i in 0 until samples) {
            val a = 2 * PI * i / samples
            px[i] = (cx + r * cos(a)).toFloat()
            pz[i] = (cz + r * sin(a)).toFloat()
            py[i] = (10 + 5 * sin(a)).toFloat()
        }
        val zeros = FloatArray(samples)
        return RecordedLap(
            lapNumber = 1, carOrdinal = 1, lapTimeS = 90.0, sampleCount = samples,
            t = FloatArray(samples) { it / 60f }, speedKmh = zeros, throttlePct = zeros,
            brakePct = zeros, steerDeg = zeros, rpm = zeros, gear = zeros,
            posX = px, posZ = pz, posY = py, latG = zeros, longG = zeros, clutchPct = zeros,
        )
    }

    @Test
    fun `measures length and elevation from the driven line`() {
        val lap = circleLap(0f, 0f, r = 500.0)
        assertEquals(2 * PI * 500.0, lap.trackLengthM, 15.0)
        assertEquals(10.0, lap.elevationRangeM!!, 0.2)
    }

    @Test
    fun `named track is recognised on a later lap, elsewhere is not`() {
        TrackStore.init(File.createTempFile("tracks", ".json").apply { delete() })
        val lap = circleLap(1000f, -2000f, r = 700.0)
        assertNull(TrackStore.identify(lap))

        TrackStore.learn("Suzuka Circuit", lap)
        // Same track, slightly different line (start offset, small length delta).
        val later = circleLap(1050f, -2010f, r = 705.0)
        assertEquals("Suzuka Circuit", TrackStore.identify(later))
        // Same length elsewhere in the world — different track.
        assertNull(TrackStore.identify(circleLap(50_000f, 50_000f, r = 700.0)))
        // Same start-line point but twice the length — a different layout.
        assertNull(TrackStore.identify(circleLap(300f, -2000f, r = 1400.0)))
    }
}
