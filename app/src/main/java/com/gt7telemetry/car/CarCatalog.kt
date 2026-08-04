package com.gt7telemetry.car

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Car-code → car identity lookup.
 *
 * GT7 broadcasts only a numeric `CarCode`; names come from the bundled
 * `gt7_car_catalog.json`, generated from the community-maintained
 * ddm999/gt7info database (~575 cars, updated with each game patch).
 *
 * Unmatched codes resolve to null and are shown as "Car #N".
 * The manufacturer is also what the auto dashboard picker keys off.
 */
object CarCatalog {

    @Serializable
    private data class CatalogFile(val ordinals: Map<String, Entry> = emptyMap())

    @Serializable
    private data class Entry(
        val manufacturer: String? = null,
        val model: String? = null,
        val display_name: String? = null,
    )

    data class CarInfo(val manufacturer: String?, val name: String)

    @Volatile
    private var byOrdinal: Map<Int, Entry> = emptyMap()

    @Volatile
    var loaded: Boolean = false
        private set

    private val json = Json { ignoreUnknownKeys = true }

    /** Parse the bundled catalog once. Cheap (~90 KB) but do it off the main thread. */
    fun load(context: Context) {
        if (loaded) return
        val merged = HashMap<Int, Entry>()
        val parsed = runCatching {
            val text = context.assets.open("gt7_car_catalog.json").bufferedReader().use { it.readText() }
            json.decodeFromString<CatalogFile>(text)
        }.getOrNull()
        if (parsed != null) {
            for ((k, v) in parsed.ordinals) {
                val ord = k.toIntOrNull() ?: continue
                merged[ord] = v
            }
        }
        byOrdinal = merged
        loaded = true
    }

    fun lookup(ordinal: Int?): CarInfo? {
        if (ordinal == null || ordinal == 0) return null
        val e = byOrdinal[ordinal] ?: return null
        val name = e.display_name?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(e.manufacturer, e.model).joinToString(" ").ifBlank { return null }
        return CarInfo(e.manufacturer, name)
    }

    fun manufacturer(ordinal: Int?): String? = lookup(ordinal)?.manufacturer
}
