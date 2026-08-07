package com.gt7telemetry.logger

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.Executors

/**
 * On-disk lap history. Every completed lap is written to the app's private
 * storage as a compact binary file (~350 KB for a two-minute lap), so the
 * log survives app restarts and builds up across sessions. The newest
 * [MAX_STORED] laps are kept; older files are pruned automatically.
 *
 * Writes happen on a single background thread (the recorder calls [save]
 * from the UDP receive thread and must never block on I/O). [entries]
 * publishes lightweight metadata for the history list — full traces load
 * on demand with [load].
 */
object LapStore {

    private const val MAGIC = 0x47375350 // "G7SP"
    private const val VERSION = 1
    private const val MAX_STORED = 200

    data class StoredLapMeta(
        val file: File,
        val recordedAtMs: Long,
        val carOrdinal: Int,
        val lapNumber: Int,
        val lapTimeS: Double,
        val tyres: String,
        val maxSpeedKmh: Double,
    )

    private val _entries = MutableStateFlow<List<StoredLapMeta>>(emptyList())
    /** Stored laps, newest first. */
    val entries: StateFlow<List<StoredLapMeta>> = _entries.asStateFlow()

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "gt7-lapstore").apply { isDaemon = true } }
    @Volatile private var dir: File? = null

    /** Idempotent. Called from both MainActivity and TelemetryService. */
    fun init(context: Context) = init(File(context.filesDir, "laps"))

    fun init(directory: File) {
        if (dir != null) return
        dir = directory
        io.execute {
            directory.mkdirs()
            refreshIndex()
        }
    }

    /** Queue a lap for persistence. Safe to call from the receive thread. */
    fun save(lap: RecordedLap) {
        val d = dir ?: return
        io.execute {
            runCatching {
                val name = "lap_%013d_%d.gt7lap".format(lap.recordedAtMs, lap.lapNumber)
                writeLap(File(d, name), lap)
                prune(d)
                refreshIndex()
            }
        }
    }

    /** Blocking full-trace read — call from Dispatchers.IO. Null if unreadable. */
    fun load(meta: StoredLapMeta): RecordedLap? = runCatching { readLap(meta.file) }.getOrNull()

    fun delete(meta: StoredLapMeta) {
        io.execute {
            meta.file.delete()
            refreshIndex()
        }
    }

    private fun prune(d: File) {
        val files = d.listFiles { f -> f.name.endsWith(".gt7lap") }?.sortedBy { it.name } ?: return
        for (f in files.dropLast(MAX_STORED)) f.delete()
    }

    private fun refreshIndex() {
        val d = dir ?: return
        val metas = d.listFiles { f -> f.name.endsWith(".gt7lap") }
            ?.mapNotNull { f -> runCatching { readMeta(f) }.getOrNull() }
            ?.sortedByDescending { it.recordedAtMs }
            ?: emptyList()
        _entries.value = metas
    }

    // ---- Wire format ------------------------------------------------------
    // header: magic, version, lapNumber, carOrdinal, lapTimeS, recordedAtMs,
    //         tyres (UTF), maxSpeed, sampleCount
    // body:   16 channels × sampleCount floats, in a fixed order.

    internal fun writeLap(file: File, lap: RecordedLap) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { o ->
            o.writeInt(MAGIC)
            o.writeInt(VERSION)
            o.writeInt(lap.lapNumber)
            o.writeInt(lap.carOrdinal)
            o.writeDouble(lap.lapTimeS)
            o.writeLong(lap.recordedAtMs)
            o.writeUTF(lap.tyres)
            o.writeDouble(lap.maxSpeedKmh)
            o.writeInt(lap.sampleCount)
            for (arr in channels(lap)) {
                // Pre-v0.7 laps can have empty tyre channels; pad with NaN.
                if (arr.size == lap.sampleCount) arr.forEach { o.writeFloat(it) }
                else repeat(lap.sampleCount) { o.writeFloat(Float.NaN) }
            }
        }
        tmp.renameTo(file)
    }

    internal fun readMeta(file: File): StoredLapMeta =
        DataInputStream(file.inputStream().buffered()).use { i ->
            require(i.readInt() == MAGIC) { "bad magic" }
            require(i.readInt() == VERSION) { "bad version" }
            val lapNumber = i.readInt()
            val carOrdinal = i.readInt()
            val lapTimeS = i.readDouble()
            val recordedAt = i.readLong()
            val tyres = i.readUTF()
            val maxSpeed = i.readDouble()
            StoredLapMeta(file, recordedAt, carOrdinal, lapNumber, lapTimeS, tyres, maxSpeed)
        }

    internal fun readLap(file: File): RecordedLap =
        DataInputStream(file.inputStream().buffered()).use { i ->
            require(i.readInt() == MAGIC) { "bad magic" }
            require(i.readInt() == VERSION) { "bad version" }
            val lapNumber = i.readInt()
            val carOrdinal = i.readInt()
            val lapTimeS = i.readDouble()
            val recordedAt = i.readLong()
            val tyres = i.readUTF()
            i.readDouble() // maxSpeed — recomputed lazily
            val n = i.readInt()
            require(n in 0..2_000_000) { "implausible sample count" }
            fun arr(): FloatArray = FloatArray(n) { i.readFloat() }
            RecordedLap(
                lapNumber = lapNumber, carOrdinal = carOrdinal, lapTimeS = lapTimeS,
                recordedAtMs = recordedAt, tyres = tyres, sampleCount = n,
                t = arr(), speedKmh = arr(), throttlePct = arr(), brakePct = arr(),
                steerDeg = arr(), rpm = arr(), gear = arr(), posX = arr(), posZ = arr(),
                latG = arr(), longG = arr(), clutchPct = arr(),
                tyreFL = arr(), tyreFR = arr(), tyreRL = arr(), tyreRR = arr(),
            )
        }

    private fun channels(lap: RecordedLap): Array<FloatArray> = arrayOf(
        lap.t, lap.speedKmh, lap.throttlePct, lap.brakePct, lap.steerDeg, lap.rpm,
        lap.gear, lap.posX, lap.posZ, lap.latG, lap.longG, lap.clutchPct,
        lap.tyreFL, lap.tyreFR, lap.tyreRL, lap.tyreRR,
    )
}
