package com.gt7telemetry.track

import android.content.Context
import com.gt7telemetry.logger.RecordedLap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Learned track recognition. GT7's telemetry stream never names the track,
 * but every lap trace carries two stable fingerprints: where the start line
 * sits in world coordinates and how long the driven lap is. So the driver
 * names a track ONCE and every later session on it — live or from history —
 * is recognised automatically.
 */
object TrackStore {

    /** Start-line world positions on the same track land within this radius. */
    private const val MATCH_RADIUS_M = 200.0

    /** Lap-length agreement required on top of the start-line match. */
    private const val MATCH_LENGTH_TOLERANCE = 0.06

    @Serializable
    data class KnownTrack(
        val name: String,
        val startX: Float,
        val startZ: Float,
        val lengthM: Float,
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "gt7-tracks").apply { isDaemon = true } }

    private val _tracks = MutableStateFlow<List<KnownTrack>>(emptyList())
    val tracks: StateFlow<List<KnownTrack>> = _tracks.asStateFlow()

    @Volatile private var file: File? = null

    /** Idempotent. */
    fun init(context: Context) = init(File(context.filesDir, "tracks.json"))

    fun init(storage: File) {
        if (file != null) return
        file = storage
        io.execute {
            val loaded = runCatching {
                json.decodeFromString<List<KnownTrack>>(storage.readText())
            }.getOrDefault(emptyList())
            // Don't clobber a track learned between init and this load.
            if (_tracks.value.isEmpty()) _tracks.value = loaded
        }
    }

    /** The learned name for the track this lap was driven on, or null. */
    fun identify(lap: RecordedLap): String? = match(lap)?.name

    /** Whether this lap's track is worth offering a "name this track" prompt for. */
    fun isKnown(lap: RecordedLap): Boolean = match(lap) != null

    /**
     * Name (or rename) the track this lap was driven on. Replaces any
     * previously learned track that matches the same fingerprint.
     */
    fun learn(name: String, lap: RecordedLap) {
        if (lap.sampleCount == 0 || name.isBlank()) return
        val entry = KnownTrack(
            name = name.trim(),
            startX = lap.posX[0],
            startZ = lap.posZ[0],
            lengthM = lap.trackLengthM.toFloat(),
        )
        val kept = _tracks.value.filterNot { matches(it, lap) }
        val updated = kept + entry
        _tracks.value = updated
        val f = file ?: return
        io.execute {
            runCatching {
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.writeText(json.encodeToString(updated))
                tmp.renameTo(f)
            }
        }
    }

    private fun match(lap: RecordedLap): KnownTrack? {
        if (lap.sampleCount == 0) return null
        return _tracks.value.firstOrNull { matches(it, lap) }
    }

    private fun matches(track: KnownTrack, lap: RecordedLap): Boolean {
        val d = hypot((track.startX - lap.posX[0]).toDouble(), (track.startZ - lap.posZ[0]).toDouble())
        if (d > MATCH_RADIUS_M) return false
        val len = lap.trackLengthM
        if (track.lengthM < 1f || len < 1.0) return d <= MATCH_RADIUS_M
        return abs(len - track.lengthM) / track.lengthM <= MATCH_LENGTH_TOLERANCE
    }
}
