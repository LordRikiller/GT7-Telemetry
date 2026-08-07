package com.gt7telemetry.car

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Car-code → car identity lookup.
 *
 * GT7 broadcasts only a numeric `CarCode`. Names come from three layers,
 * later layers winning:
 *   1. `assets/gt7_car_catalog.json` — bundled snapshot of the community
 *      ddm999/gt7info database (~575 cars).
 *   2. A cached copy of the live database from the last successful refresh.
 *   3. [refresh] — re-fetches the live database on launch, so cars added in
 *      a game patch get names (and the auto dashboard picker starts working
 *      for them) as soon as the community DB updates — no app release needed.
 *
 * Unmatched codes resolve to null and are shown as "Car #N".
 * The manufacturer is also what the auto dashboard picker keys off.
 */
object CarCatalog {

    private const val CARS_URL = "https://raw.githubusercontent.com/ddm999/gt7info/master/_data/db/cars.csv"
    private const val MAKERS_URL = "https://raw.githubusercontent.com/ddm999/gt7info/master/_data/db/maker.csv"
    private const val CACHE_FILE = "car_catalog_overlay.json"

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

    /** Bumped whenever the table changes, so Compose can key off it. */
    private val _revision = AtomicInteger(0)
    val revision: Int get() = _revision.get()

    private val json = Json { ignoreUnknownKeys = true }

    /** Parse the bundled catalog + any cached overlay. Cheap (~90 KB) but do it off the main thread. */
    fun load(context: Context) {
        if (loaded) return
        val merged = HashMap<Int, Entry>()
        mergeJson(merged, runCatching {
            context.assets.open("gt7_car_catalog.json").bufferedReader().use { it.readText() }
        }.getOrNull())
        // Overlay from the last successful refresh survives offline launches.
        mergeJson(merged, runCatching {
            File(context.filesDir, CACHE_FILE).takeIf { it.exists() }?.readText()
        }.getOrNull())
        byOrdinal = merged
        loaded = true
        _revision.incrementAndGet()
    }

    /**
     * Fetch the live community database and merge it in (call from a
     * background thread). Quietly a no-op on any network/parse failure —
     * the bundled + cached data keeps working.
     */
    fun refresh(context: Context) {
        val overlay = runCatching { fetchLiveDb() }.getOrNull() ?: return
        if (overlay.isEmpty()) return
        val merged = HashMap(byOrdinal)
        merged.putAll(overlay)
        byOrdinal = merged
        _revision.incrementAndGet()
        // Cache as the same JSON shape the asset uses.
        runCatching {
            val body = overlay.entries.joinToString(",\n") { (id, e) ->
                "\"$id\": ${json.encodeToString(Entry.serializer(), e)}"
            }
            File(context.filesDir, CACHE_FILE).writeText("{\"ordinals\": {\n$body\n}}")
        }
    }

    private fun fetchLiveDb(): Map<Int, Entry> {
        // maker.csv: ID,Name,Country — no quoting/embedded commas in either file.
        val makers = HashMap<String, String>()
        for (line in httpGet(MAKERS_URL).lineSequence().drop(1)) {
            val parts = line.split(',')
            if (parts.size >= 2) makers[parts[0].trim()] = parts[1].trim()
        }
        // cars.csv: ID,ShortName,Maker
        val out = HashMap<Int, Entry>()
        for (line in httpGet(CARS_URL).lineSequence().drop(1)) {
            if (line.isBlank()) continue
            val first = line.indexOf(',')
            val last = line.lastIndexOf(',')
            if (first <= 0 || last <= first) continue
            val id = line.substring(0, first).trim().toIntOrNull() ?: continue
            val model = line.substring(first + 1, last).trim()
            val maker = makers[line.substring(last + 1).trim()]
            out[id] = Entry(
                manufacturer = maker,
                model = model,
                display_name = if (maker != null) "$maker $model" else model,
            )
        }
        return out
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }

    private fun mergeJson(into: MutableMap<Int, Entry>, text: String?) {
        val parsed = text?.let { runCatching { json.decodeFromString<CatalogFile>(it) }.getOrNull() } ?: return
        for ((k, v) in parsed.ordinals) {
            val ord = k.toIntOrNull() ?: continue
            into[ord] = v
        }
    }

    fun lookup(ordinal: Int?): CarInfo? {
        if (ordinal == null || ordinal == 0) return null
        val e = byOrdinal[ordinal] ?: return null
        val name = e.display_name?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(e.manufacturer, e.model).joinToString(" ").ifBlank { return null }
        return CarInfo(e.manufacturer, name)
    }

    fun manufacturer(ordinal: Int?): String? = lookup(ordinal)?.manufacturer

    /** Case-insensitive substring search over the whole catalog (for the car picker). */
    fun search(query: String, limit: Int = 40): List<Pair<Int, CarInfo>> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return byOrdinal.entries.asSequence()
            .mapNotNull { (ord, _) -> lookup(ord)?.let { ord to it } }
            .filter { (_, info) -> info.name.lowercase().contains(q) }
            .sortedBy { (_, info) -> info.name }
            .take(limit)
            .toList()
    }
}
