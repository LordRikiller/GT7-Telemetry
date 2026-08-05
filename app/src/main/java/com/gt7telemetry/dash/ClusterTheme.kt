package com.gt7telemetry.dash

import androidx.compose.ui.graphics.Color

/** Colour + type identity for one cluster layout. */
data class ClusterTheme(
    val bg: Color,
    val panel: Color,
    val line: Color,
    val ink: Color,
    val ink2: Color,
    val mute: Color,
    val accent: Color,
    val needle: Color,
    val redline: Color,
    val good: Color,
    val dialFace: Color,
    val dialText: Color,
    val dialGear: Color,
    val ring: Color? = null,          // chrome/bezel ring colour, when the layout uses one
    val warn: Color = Color(0xFFFFBF00),
    val italic: Boolean = false,
)

/** Structural family — the actual arrangement of the instruments. */
enum class LayoutFamily { DEFAULT, CENTRAL, TWIN, FIVE_DIAL, BAR, MINIMAL, TILES, DIGITAL_RING, OFFSET }

private fun c(v: Long): Color = Color(v)

/**
 * The catalogue of dashboard layouts. Each pairs a structural [family] with a
 * per-marque [theme]. [manufacturer] is the catalog key the AUTO picker matches
 * on (null = not auto-selected, e.g. the neutral default and the generic ones).
 */
enum class DashLayout(
    val label: String,
    val manufacturer: String?,
    val family: LayoutFamily,
    val theme: ClusterTheme,
    val heroSpeed: Boolean = false,
    val spdMax: Int = 240,
) {
    DEFAULT("Default", null, LayoutFamily.DEFAULT, ClusterTheme(
        c(0xFF16130F), c(0xFF211C16), c(0xFF372F25), c(0xFFF2EDE3), c(0xFFC9BEAD), c(0xFF6E6557),
        c(0xFFFFAE00), c(0xFFFFAE00), c(0xFFFF4D42), c(0xFF3BD98A), c(0xFF211C16), c(0xFFF2EDE3), c(0xFFF2EDE3))),

    FERRARI("Ferrari", "Ferrari", LayoutFamily.CENTRAL, ClusterTheme(
        c(0xFF0B0B0C), c(0xFF151517), c(0xFF26262B), c(0xFFF5F5F6), c(0xFFB8B8BD), c(0xFF76767D),
        c(0xFFFFEF00), c(0xFF111111), c(0xFFE2231A), c(0xFF3BD98A), c(0xFFF4C500), c(0xFF111111), c(0xFF111111))),

    LAMBORGHINI("Lamborghini", "Lamborghini", LayoutFamily.DIGITAL_RING, ClusterTheme(
        c(0xFF0A0D0A), c(0xFF0F150C), c(0xFF1D3113), c(0xFFEAFCE9), c(0xFF8FB87E), c(0xFF5F8A4F),
        c(0xFFA6FF3D), c(0xFFA6FF3D), c(0xFFFF5A3C), c(0xFFA6FF3D), c(0xFF0F150C), c(0xFFEAFCE9), c(0xFFA6FF3D),
        warn = c(0xFFFFD23D), italic = true)),

    PORSCHE("Porsche", "Porsche", LayoutFamily.FIVE_DIAL, ClusterTheme(
        c(0xFF0F1114), c(0xFF15181C), c(0xFF22262C), c(0xFFEEF1F4), c(0xFFAAB0B8), c(0xFF7F8792),
        c(0xFFD8261C), c(0xFFD8261C), c(0xFFD8261C), c(0xFF4AD07A), c(0xFF15181C), c(0xFFCFD4DA), c(0xFFEEF1F4))),

    MUSTANG("Ford Mustang", "Ford", LayoutFamily.TWIN, ClusterTheme(
        c(0xFF0A0E18), c(0xFF0D1526), c(0xFF1C2A48), c(0xFFEEF2F8), c(0xFF9FB0CC), c(0xFF8093B3),
        c(0xFFFF5A5F), c(0xFFFF3B30), c(0xFFFF3B30), c(0xFF4AD07A), c(0xFF0D1730), c(0xFFE8ECF4), c(0xFFFF5A5F),
        ring = c(0xFF6A7793))),

    FORMULA1("Formula 1", null, LayoutFamily.BAR, ClusterTheme(
        c(0xFF050506), c(0xFF101014), c(0xFF1A1A1F), c(0xFFFFFFFF), c(0xFFB7B7BD), c(0xFF7A7A82),
        c(0xFF00E078), c(0xFFFFFFFF), c(0xFFFF2B2B), c(0xFF00E078), c(0xFF101014), c(0xFFFFFFFF), c(0xFFFFFFFF))),

    TESLA("Tesla", "Tesla", LayoutFamily.MINIMAL, ClusterTheme(
        c(0xFF0E0F11), c(0xFF17191C), c(0xFF24272B), c(0xFFF4F6F8), c(0xFFAAB0B6), c(0xFF767C83),
        c(0xFF3AA0FF), c(0xFF3AA0FF), c(0xFFFF453A), c(0xFF32D74B), c(0xFF17191C), c(0xFFF4F6F8), c(0xFFF4F6F8))),

    BMW_M("BMW M", "BMW", LayoutFamily.OFFSET, ClusterTheme(
        c(0xFF0C0E10), c(0xFF15181B), c(0xFF242830), c(0xFFF2F5F8), c(0xFFAAB2BD), c(0xFF79818C),
        c(0xFF2E6CF6), c(0xFFE10600), c(0xFFE10600), c(0xFF00B7FF), c(0xFF101316), c(0xFFE6EAEF), c(0xFFF2F5F8),
        ring = c(0xFF3A4A63))),

    AMG("Mercedes-AMG", "Mercedes-Benz", LayoutFamily.TWIN, ClusterTheme(
        c(0xFF0A0B0D), c(0xFF131519), c(0xFF232730), c(0xFFEEF1F5), c(0xFFA7ADB8), c(0xFF787F8B),
        c(0xFF00A3E0), c(0xFFE2231A), c(0xFFE2231A), c(0xFF00A3E0), c(0xFF0D0F12), c(0xFFEEF1F5), c(0xFFEEF1F5),
        ring = c(0xFF2B3140))),

    AUDI_RS("Audi RS", "Audi", LayoutFamily.DIGITAL_RING, ClusterTheme(
        c(0xFF0B0D0E), c(0xFF14171A), c(0xFF242A2C), c(0xFFEEF2F3), c(0xFFA6AFB2), c(0xFF7A8488),
        c(0xFF43D675), c(0xFF43D675), c(0xFFFF433A), c(0xFF43D675), c(0xFF14171A), c(0xFFEEF2F3), c(0xFF43D675),
        warn = c(0xFFFFD23D))),

    GTR("Nissan GT-R", "Nissan", LayoutFamily.TILES, ClusterTheme(
        c(0xFF0A0B0D), c(0xFF141619), c(0xFF242832), c(0xFFEEF1F5), c(0xFFA7ADB8), c(0xFF7A808C),
        c(0xFFE2231A), c(0xFFE2231A), c(0xFFE2231A), c(0xFF38D0C0), c(0xFF101318), c(0xFFE6EAF0), c(0xFFE2231A))),

    CORVETTE("Corvette", "Chevrolet", LayoutFamily.CENTRAL, ClusterTheme(
        c(0xFF0C0A0A), c(0xFF171313), c(0xFF2A2020), c(0xFFF5F2F2), c(0xFFBCB2B2), c(0xFF7D7070),
        c(0xFFE4322B), c(0xFFE4322B), c(0xFFE4322B), c(0xFF3BD98A), c(0xFF141010), c(0xFFF0E8E8), c(0xFFE4322B))),

    LFA("Lexus LFA", "Lexus", LayoutFamily.DIGITAL_RING, ClusterTheme(
        c(0xFF0A0A0B), c(0xFF151417), c(0xFF282329), c(0xFFF4F2F5), c(0xFFB6B0BA), c(0xFF7C757F),
        c(0xFFFF7A1A), c(0xFFFF7A1A), c(0xFFFF2B2B), c(0xFF3BD98A), c(0xFF151417), c(0xFFF4F2F5), c(0xFFFF7A1A),
        warn = c(0xFFFFD23D))),

    ASTON("Aston Martin", "Aston Martin", LayoutFamily.OFFSET, ClusterTheme(
        c(0xFF0C0E0C), c(0xFF151815), c(0xFF242A24), c(0xFFEEF2EC), c(0xFFA8B0A6), c(0xFF7A8378),
        c(0xFFC7E600), c(0xFFC7E600), c(0xFFD8B400), c(0xFFC7E600), c(0xFF111411), c(0xFFE6EAE2), c(0xFFC7E600),
        ring = c(0xFF39422F))),

    PAGANI("Pagani", "Pagani", LayoutFamily.TWIN, ClusterTheme(
        c(0xFF12100C), c(0xFF1B1712), c(0xFF332C20), c(0xFFF3ECDC), c(0xFFC3B79C), c(0xFF94886A),
        c(0xFF7A5CFF), c(0xFF3A4FB0), c(0xFFB3271E), c(0xFF4A7A3A), c(0xFFEFE7D3), c(0xFF2A2418), c(0xFF2A2418),
        ring = c(0xFFB8A986))),

    BUGATTI("Bugatti", "Bugatti", LayoutFamily.CENTRAL, ClusterTheme(
        c(0xFF0A0C0F), c(0xFF131720), c(0xFF232A38), c(0xFFEEF2F8), c(0xFFA7B1C2), c(0xFF7A8497),
        c(0xFF00B4D8), c(0xFFC0D0E0), c(0xFFE2231A), c(0xFF00B4D8), c(0xFF0F1420), c(0xFFDFE6F0), c(0xFF00B4D8)),
        heroSpeed = true, spdMax = 420),

    HELLCAT("Dodge Hellcat", "Dodge", LayoutFamily.TWIN, ClusterTheme(
        c(0xFF0B0A0A), c(0xFF151212), c(0xFF2A1F1F), c(0xFFF6F3F3), c(0xFFBDB2B2), c(0xFF7D7070),
        c(0xFFC8102E), c(0xFFC8102E), c(0xFFC8102E), c(0xFF3BD98A), c(0xFF120E0E), c(0xFFF2EAEA), c(0xFFC8102E),
        ring = c(0xFF4A3232))),

    // GT7 has no Koenigsegg — kept as a manual-only pick.
    KOENIGSEGG("Koenigsegg", null, LayoutFamily.CENTRAL, ClusterTheme(
        c(0xFF0B0C0D), c(0xFF141618), c(0xFF242629), c(0xFFF4F6F8), c(0xFFADB2B8), c(0xFF787D83),
        c(0xFFE8ECEF), c(0xFFE8ECEF), c(0xFFFF4D42), c(0xFF3BD98A), c(0xFF101214), c(0xFFE8ECEF), c(0xFFE8ECEF))),

    MCLAREN("McLaren", "McLaren", LayoutFamily.MINIMAL, ClusterTheme(
        c(0xFF0A0B0C), c(0xFF141618), c(0xFF23262A), c(0xFFF2F5F7), c(0xFFA8AEB5), c(0xFF787E85),
        c(0xFFFF6A00), c(0xFFFF6A00), c(0xFFFF2B2B), c(0xFF00D68F), c(0xFF141618), c(0xFFF2F5F7), c(0xFFF2F5F7))),

    TYPE_R("Honda Civic Type R", "Honda", LayoutFamily.BAR, ClusterTheme(
        c(0xFF080809), c(0xFF121214), c(0xFF1E1E22), c(0xFFFFFFFF), c(0xFFB4B4BA), c(0xFF78787F),
        c(0xFFE2231A), c(0xFFFFFFFF), c(0xFFE2231A), c(0xFF00E078), c(0xFF121214), c(0xFFFFFFFF), c(0xFFFFFFFF))),

    CLASSIC("Classic (1960s)", null, LayoutFamily.TWIN, ClusterTheme(
        c(0xFFE7E0CC), c(0xFFF2ECD9), c(0xFFCCC0A0), c(0xFF1C1810), c(0xFF5A5140), c(0xFF8B7D5F),
        c(0xFF0F5132), c(0xFFB3271E), c(0xFFB3271E), c(0xFF0F5132), c(0xFFEFE8D4), c(0xFF241D12), c(0xFF241D12),
        ring = c(0xFFB7A880)));

    companion object {
        /** Everything the manual picker lists, default first. */
        val selectable: List<DashLayout> get() = entries

        private val byManufacturer: Map<String, DashLayout> =
            entries.filter { it.manufacturer != null }.associateBy { it.manufacturer!!.lowercase() }

        /**
         * GT7 lists some tuner/sub-brands as their own manufacturer; fold them
         * into the marque whose cluster fits.
         */
        private val aliases = mapOf(
            "amg" to "mercedes-benz",
            "nismo" to "nissan",
            "mine's" to "nissan",
            "shelby" to "ford",
            "srt" to "dodge",
            "ruf" to "porsche",
        )

        /** AUTO mode: pick the layout for a catalog manufacturer, else null. */
        fun forManufacturer(manufacturer: String?): DashLayout? {
            val key = manufacturer?.lowercase() ?: return null
            return byManufacturer[aliases[key] ?: key]
        }

        fun byNameOrDefault(name: String): DashLayout =
            runCatching { valueOf(name) }.getOrDefault(DEFAULT)
    }
}
