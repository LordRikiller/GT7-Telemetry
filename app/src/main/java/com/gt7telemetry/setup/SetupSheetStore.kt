package com.gt7telemetry.setup

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Per-car setup sheets, one JSON file per car ordinal in private storage.
 * [sheets] holds the full in-memory index so the active car's sheet can be
 * pulled up instantly when the game hands us its ordinal, and so the
 * race-engineer briefing can attach the sheet belonging to the recorded
 * laps' car.
 */
object SetupSheetStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    private val _sheets = MutableStateFlow<Map<Int, SetupSheet>>(emptyMap())
    /** All saved sheets, keyed by car ordinal. */
    val sheets: StateFlow<Map<Int, SetupSheet>> = _sheets.asStateFlow()

    @Volatile private var dir: File? = null

    /** Idempotent; cheap enough to run synchronously (a handful of small JSON files). */
    fun init(context: Context) = init(File(context.filesDir, "setups"))

    fun init(directory: File) {
        if (dir != null) return
        dir = directory
        directory.mkdirs()
        _sheets.value = directory.listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { f -> runCatching { json.decodeFromString<SetupSheet>(f.readText()) }.getOrNull() }
            ?.associateBy { it.carOrdinal }
            ?: emptyMap()
    }

    fun forCar(ordinal: Int?): SetupSheet? = ordinal?.let { _sheets.value[it] }

    fun save(sheet: SetupSheet) {
        val d = dir ?: return
        val stamped = sheet.copy(updatedAtMs = System.currentTimeMillis())
        runCatching { File(d, "car_${sheet.carOrdinal}.json").writeText(json.encodeToString(SetupSheet.serializer(), stamped)) }
        _sheets.value = _sheets.value + (sheet.carOrdinal to stamped)
    }

    fun delete(ordinal: Int) {
        dir?.let { File(it, "car_$ordinal.json").delete() }
        _sheets.value = _sheets.value - ordinal
    }
}
