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

    // The classic five-dial 911 cluster stays as a manual pick; the 992 GT3 RS
    // flagship below owns the Porsche AUTO slot.
    PORSCHE("Porsche 911 Classic", null, LayoutFamily.FIVE_DIAL, ClusterTheme(
        c(0xFF0F1114), c(0xFF15181C), c(0xFF22262C), c(0xFFEEF1F4), c(0xFFAAB0B8), c(0xFF7F8792),
        c(0xFFD8261C), c(0xFFD8261C), c(0xFFD8261C), c(0xFF4AD07A), c(0xFF15181C), c(0xFFCFD4DA), c(0xFFEEF1F4))),

    // Kept as a manual pick; the Mustang GTD flagship owns the Ford AUTO slot.
    MUSTANG("Ford Mustang GT", null, LayoutFamily.TWIN, ClusterTheme(
        c(0xFF0A0E18), c(0xFF0D1526), c(0xFF1C2A48), c(0xFFEEF2F8), c(0xFF9FB0CC), c(0xFF8093B3),
        c(0xFFFF5A5F), c(0xFFFF3B30), c(0xFFFF3B30), c(0xFF4AD07A), c(0xFF0D1730), c(0xFFE8ECF4), c(0xFFFF5A5F),
        ring = c(0xFF6A7793))),

    FORMULA1("Formula 1", "Super Formula (Dallara)", LayoutFamily.BAR, ClusterTheme(
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
        ring = c(0xFFB7A880))),

    // --- v0.3.0 flagship additions -----------------------------------------
    // Twenty more marque clusters, each modelled on the manufacturer's
    // top-of-the-line dashboard. Colours and instrument arrangement follow
    // the real car; the structural family is the closest of the nine.

    // 992 GT3 RS track screen: one big central tach, white needle on a black
    // face, Guards Red accents, GT-silver bezel, shift lights across the top.
    PORSCHE_GT3_RS("Porsche 911 GT3 RS (992)", "Porsche", LayoutFamily.CENTRAL, ClusterTheme(
        c(0xFF0C0D0F), c(0xFF14161A), c(0xFF23272E), c(0xFFF1F3F6), c(0xFFA9B0BA), c(0xFF79818D),
        c(0xFFD5001C), c(0xFFF4F6F8), c(0xFFD5001C), c(0xFF34C759), c(0xFF101215), c(0xFFE9ECF1), c(0xFFF1F3F6),
        ring = c(0xFF4A515C))),

    // Mustang GTD's bespoke race display: full-width dark strip, big white
    // digits, red shift band — closer to a GT3 racer than a road Mustang.
    MUSTANG_GTD("Ford Mustang GTD", "Ford", LayoutFamily.BAR, ClusterTheme(
        c(0xFF0A0A0C), c(0xFF141417), c(0xFF212127), c(0xFFF5F6F8), c(0xFFAFB3BB), c(0xFF7B808A),
        c(0xFFFF2D2D), c(0xFFF5F6F8), c(0xFFFF2D2D), c(0xFF35D07F), c(0xFF141417), c(0xFFF5F6F8), c(0xFFF5F6F8))),

    // GR010 Hybrid steering-wheel display: black boxes of data around a gear
    // digit, GR red on white, green for OK states.
    TOYOTA_GR010("Toyota GR010 Hybrid", "Toyota", LayoutFamily.TILES, ClusterTheme(
        c(0xFF0B0B0D), c(0xFF141418), c(0xFF232330), c(0xFFF3F4F7), c(0xFFACB0BA), c(0xFF7B8089),
        c(0xFFEB0A1E), c(0xFFEB0A1E), c(0xFFEB0A1E), c(0xFF00B060), c(0xFF101014), c(0xFFE9EBF0), c(0xFFEB0A1E))),

    // 787B: charcoal cockpit, big central tach, Renown orange and green.
    MAZDA_787B("Mazda 787B", "Mazda", LayoutFamily.CENTRAL, ClusterTheme(
        c(0xFF121110), c(0xFF1A1917), c(0xFF2E2C28), c(0xFFF3EFE6), c(0xFFC2BBAC), c(0xFF8A8375),
        c(0xFFFF6A13), c(0xFFFF6A13), c(0xFFE8231A), c(0xFF00A551), c(0xFF161513), c(0xFFEFEBE0), c(0xFFFF6A13),
        ring = c(0xFF3A372F))),

    // WRX STI Final Edition: black twin dials, STI cherry-red needles.
    SUBARU_STI("Subaru WRX STI Final", "Subaru", LayoutFamily.TWIN, ClusterTheme(
        c(0xFF0B0B0D), c(0xFF131316), c(0xFF232329), c(0xFFF2F3F5), c(0xFFAAADB5), c(0xFF7A7E87),
        c(0xFFE4002B), c(0xFFE4002B), c(0xFFE4002B), c(0xFF3BD98A), c(0xFF101114), c(0xFFEDEFF2), c(0xFFE4002B),
        ring = c(0xFF3A3F4A))),

    // Lancer Evolution Final Edition: high-contrast black dials, Ralliart red.
    EVO_FINAL("Mitsubishi Lancer Evo Final", "Mitsubishi", LayoutFamily.OFFSET, ClusterTheme(
        c(0xFF0B0A0B), c(0xFF131214), c(0xFF232126), c(0xFFF4F3F5), c(0xFFAEABB3), c(0xFF7D7A84),
        c(0xFFD80C18), c(0xFFD80C18), c(0xFFD80C18), c(0xFF3BD98A), c(0xFF0F0F12), c(0xFFEFEDF1), c(0xFFD80C18),
        ring = c(0xFF474C55))),

    // ID.R record car: serene minimal EV readout, e-blue on blue-black.
    VW_IDR("Volkswagen ID.R", "Volkswagen", LayoutFamily.MINIMAL, ClusterTheme(
        c(0xFF0A0F16), c(0xFF121A26), c(0xFF223040), c(0xFFF0F5FA), c(0xFFA6B4C4), c(0xFF76859A),
        c(0xFF00B0F0), c(0xFF00B0F0), c(0xFFFF3B4A), c(0xFF35D07F), c(0xFF121A26), c(0xFFF0F5FA), c(0xFFF0F5FA))),

    // Giulia GTAm: the twin-binnacle "cannocchiale", cream numerals on black,
    // Alfa-red needles, Italian-green OK states.
    ALFA_GTAM("Alfa Romeo Giulia GTAm", "Alfa Romeo", LayoutFamily.TWIN, ClusterTheme(
        c(0xFF0E0C0A), c(0xFF171310), c(0xFF2E2820), c(0xFFF0E6C8), c(0xFFC0B394), c(0xFF8A7F63),
        c(0xFFC4212B), c(0xFFC4212B), c(0xFFC4212B), c(0xFF108A4A), c(0xFF13100C), c(0xFFEDE2C2), c(0xFFF0E6C8),
        ring = c(0xFF9C8A5A), italic = true)),

    // MC20: single wide digital cluster, trident-blue highlights.
    MASERATI_MC20("Maserati MC20", "Maserati", LayoutFamily.DIGITAL_RING, ClusterTheme(
        c(0xFF0A0C10), c(0xFF131720), c(0xFF232B3A), c(0xFFEFF2F7), c(0xFFA8B1C0), c(0xFF788296),
        c(0xFF00A9CE), c(0xFF00A9CE), c(0xFFFF3B30), c(0xFF00A9CE), c(0xFF10141C), c(0xFFE9EDF4), c(0xFF00A9CE),
        warn = c(0xFFFFD23D))),

    // Jaguar Vision GT SV: quiet EV racer, British racing green and gold.
    JAGUAR_VGT("Jaguar Vision GT SV", "Jaguar", LayoutFamily.MINIMAL, ClusterTheme(
        c(0xFF0B1210), c(0xFF121B17), c(0xFF223229), c(0xFFEFF4F1), c(0xFFA9BBB1), c(0xFF798B81),
        c(0xFFC8A85A), c(0xFFC8A85A), c(0xFFE23B2E), c(0xFF37C978), c(0xFF121B17), c(0xFFEFF4F1), c(0xFFEFF4F1))),

    // 9X8 Le Mans Hypercar: race strip in Peugeot's "Kryptonite" acid green.
    PEUGEOT_9X8("Peugeot 9X8", "Peugeot", LayoutFamily.BAR, ClusterTheme(
        c(0xFF060607), c(0xFF101012), c(0xFF1B1B1F), c(0xFFFDFDFE), c(0xFFB5B5BB), c(0xFF7B7B83),
        c(0xFF9BFF00), c(0xFFFDFDFE), c(0xFFFF2B2B), c(0xFF9BFF00), c(0xFF101012), c(0xFFFDFDFE), c(0xFFFDFDFE))),

    // A110 R: compact TFT, Alpine blue with an orange redline.
    ALPINE_A110R("Alpine A110 R", "Alpine", LayoutFamily.OFFSET, ClusterTheme(
        c(0xFF0A0E15), c(0xFF121927), c(0xFF22304A), c(0xFFEFF3F9), c(0xFFA7B3C6), c(0xFF77839A),
        c(0xFF2E8BFF), c(0xFF2E8BFF), c(0xFFFF5A3C), c(0xFF35D07F), c(0xFF101623), c(0xFFE9EEF6), c(0xFF2E8BFF),
        ring = c(0xFF33415C))),

    // Mégane R.S. Trophy-R and its R.S. Monitor: Liquid Yellow data tiles.
    RENAULT_RS("Renault Mégane R.S. Trophy-R", "Renault", LayoutFamily.TILES, ClusterTheme(
        c(0xFF0C0C0A), c(0xFF151512), c(0xFF26261F), c(0xFFF5F5F0), c(0xFFB3B3A6), c(0xFF7F7F72),
        c(0xFFFFD500), c(0xFFFFD500), c(0xFFFF3B30), c(0xFF3BD98A), c(0xFF121210), c(0xFFF0F0E8), c(0xFFFFD500),
        warn = c(0xFFFF8A00))),

    // N Vision 74: retro pixel readout, N performance blue with hot orange.
    HYUNDAI_N74("Hyundai N Vision 74", "Hyundai", LayoutFamily.TILES, ClusterTheme(
        c(0xFF0A0F18), c(0xFF121A28), c(0xFF223148), c(0xFFEFF4FB), c(0xFFA6B6CC), c(0xFF76889F),
        c(0xFF55B7E8), c(0xFF55B7E8), c(0xFFFF4D00), c(0xFF55B7E8), c(0xFF101724), c(0xFFE9F0F9), c(0xFF55B7E8),
        warn = c(0xFFFF8A00))),

    // Genesis X Gran Berlinetta VGT: dark cabin with signature copper.
    GENESIS_X("Genesis X Gran Berlinetta", "Genesis", LayoutFamily.CENTRAL, ClusterTheme(
        c(0xFF0D0C0B), c(0xFF161413), c(0xFF2A2622), c(0xFFF2EFEC), c(0xFFBCB3AA), c(0xFF857C73),
        c(0xFFC36F51), c(0xFFC36F51), c(0xFFE23B2E), c(0xFF37C978), c(0xFF121110), c(0xFFEDE9E5), c(0xFFC36F51),
        ring = c(0xFF5A4A40))),

    // Delta HF Integrale: a wall of small black Veglia dials, amber needles,
    // a nod of Martini blue for the good states.
    LANCIA_DELTA("Lancia Delta HF Integrale", "Lancia", LayoutFamily.FIVE_DIAL, ClusterTheme(
        c(0xFF0D0D0C), c(0xFF151514), c(0xFF272725), c(0xFFF2F0E8), c(0xFFBDBAAC), c(0xFF868375),
        c(0xFFFFB300), c(0xFFFFB300), c(0xFFD8281C), c(0xFF77C4D3), c(0xFF121210), c(0xFFEDEBE2), c(0xFFFFB300))),

    // Cooper S '65: one giant central Smiths speedometer, chrome ring, cream
    // numerals, British-racing-green surround. Speed is the hero.
    MINI_COOPER("Mini Cooper S '65", "MINI", LayoutFamily.CENTRAL, ClusterTheme(
        c(0xFF11291F), c(0xFF0D211A), c(0xFF1E3A2E), c(0xFFF0EAD8), c(0xFFC3BBA2), c(0xFF8A8570),
        c(0xFFD8B44A), c(0xFFDB4A2B), c(0xFFB3271E), c(0xFF4A9E6B), c(0xFF15130E), c(0xFFEFE8D4), c(0xFFF0EAD8),
        ring = c(0xFFB9C0BC)),
        heroSpeed = true, spdMax = 160),

    // Abarth 695: matte black binnacle, scorpion red, big central tach.
    ABARTH_695("Abarth 695", "Abarth", LayoutFamily.CENTRAL, ClusterTheme(
        c(0xFF0D0B0B), c(0xFF161212), c(0xFF2A2222), c(0xFFF5F2F2), c(0xFFBBB1B1), c(0xFF837878),
        c(0xFFD7182A), c(0xFFD7182A), c(0xFFD7182A), c(0xFF3BD98A), c(0xFF121010), c(0xFFF0EAEA), c(0xFFD7182A),
        ring = c(0xFF4A3A3A))),

    // Suzuki Vision GT: superbike-style twin dials, Suzuki racing blue.
    SUZUKI_VGT("Suzuki Vision GT", "Suzuki", LayoutFamily.TWIN, ClusterTheme(
        c(0xFF0A1322), c(0xFF101B2E), c(0xFF203450), c(0xFFEFF3F9), c(0xFFA7B4C8), c(0xFF77869D),
        c(0xFF3E8EF7), c(0xFFEFF3F9), c(0xFFFF3B4A), c(0xFF35D07F), c(0xFF0E1828), c(0xFFE9EEF6), c(0xFF3E8EF7),
        ring = c(0xFF6E7B8C))),

    // The Vision GT concepts themselves — segmented ring in the GT blue/red
    // of the launcher icon.
    GT_VGT("Gran Turismo VGT", "Gran Turismo", LayoutFamily.DIGITAL_RING, ClusterTheme(
        c(0xFF10141F), c(0xFF161B29), c(0xFF27304A), c(0xFFF1F4FA), c(0xFFA9B3CA), c(0xFF7985A1),
        c(0xFF2D6BFF), c(0xFFE62E32), c(0xFFE62E32), c(0xFF3BD98A), c(0xFF131828), c(0xFFEBEFF8), c(0xFF2D6BFF),
        warn = c(0xFFFFD23D))),

    // --- v0.4.0: full-catalog coverage --------------------------------------
    // Twelve more marques, closing out every manufacturer in the 575-car
    // catalog (the rest fold into these via the alias table below).

    // AFEELA 1: Sony's panoramic media bar — calm violet on cool graphite.
    AFEELA("AFEELA 1", "AFEELA", LayoutFamily.MINIMAL, ClusterTheme(
        c(0xFF0C0D12), c(0xFF15161E), c(0xFF262838), c(0xFFF1F2F8), c(0xFFACAFC2), c(0xFF7B7F95),
        c(0xFF8A7CFF), c(0xFF8A7CFF), c(0xFFFF4D5E), c(0xFF35D07F), c(0xFF15161E), c(0xFFF1F2F8), c(0xFFF1F2F8))),

    // BAC Mono: single-seater motorsport LCD, black and surgical white.
    BAC_MONO("BAC Mono", "BAC", LayoutFamily.BAR, ClusterTheme(
        c(0xFF070708), c(0xFF111113), c(0xFF1D1D21), c(0xFFFBFBFC), c(0xFFB3B3BA), c(0xFF7A7A82),
        c(0xFFE8E9EC), c(0xFFFBFBFC), c(0xFFFF2B2B), c(0xFF00D68F), c(0xFF111113), c(0xFFFBFBFC), c(0xFFFBFBFC))),

    // GT by Citroën: sci-fi cockpit, chevron red on gloss black.
    CITROEN_GT("GT by Citroën", "Citroen", LayoutFamily.DIGITAL_RING, ClusterTheme(
        c(0xFF0B0A0B), c(0xFF141215), c(0xFF262128), c(0xFFF4F1F5), c(0xFFB2ABB5), c(0xFF7D7681),
        c(0xFFDA291C), c(0xFFDA291C), c(0xFFDA291C), c(0xFF3BD98A), c(0xFF110F12), c(0xFFEFEBF1), c(0xFFDA291C),
        warn = c(0xFFFFD23D))),

    // DMC DeLorean: brushed stainless, black faces, the orange-tipped needles.
    DMC_DELOREAN("DMC DeLorean", "DMC", LayoutFamily.OFFSET, ClusterTheme(
        c(0xFF141517), c(0xFF1D1F22), c(0xFF33363B), c(0xFFEDEEF0), c(0xFFADB0B5), c(0xFF7C8087),
        c(0xFFFF8A00), c(0xFFFF8A00), c(0xFFE24A2E), c(0xFF3BD98A), c(0xFF101113), c(0xFFDADCE0), c(0xFFFF8A00),
        ring = c(0xFF8D939C))),

    // Jeep: olive drab, amber backlight, chunky utilitarian tiles.
    JEEP("Jeep", "Jeep", LayoutFamily.TILES, ClusterTheme(
        c(0xFF121510), c(0xFF1A1F16), c(0xFF2E3626), c(0xFFF0EFE4), c(0xFFB9BBA4), c(0xFF848873),
        c(0xFFFFB300), c(0xFFFFB300), c(0xFFE24A2E), c(0xFF7BAE5E), c(0xFF161A12), c(0xFFEBEADF), c(0xFFFFB300))),

    // KTM X-Bow: ready-to-race orange on carbon black.
    KTM_XBOW("KTM X-Bow", "KTM", LayoutFamily.MINIMAL, ClusterTheme(
        c(0xFF0A0A0B), c(0xFF131314), c(0xFF202023), c(0xFFF5F5F6), c(0xFFB0B0B5), c(0xFF7A7A80),
        c(0xFFFF6600), c(0xFFFF6600), c(0xFFFF3B30), c(0xFF35D07F), c(0xFF131314), c(0xFFF5F5F6), c(0xFFFF6600))),

    // Opel Motorsport: signal yellow on anthracite.
    OPEL("Opel", "Opel", LayoutFamily.DEFAULT, ClusterTheme(
        c(0xFF0E0F10), c(0xFF171819), c(0xFF28292B), c(0xFFF3F4F4), c(0xFFAEB0B1), c(0xFF7C7E80),
        c(0xFFF7E600), c(0xFFF7E600), c(0xFFFF3B30), c(0xFF3BD98A), c(0xFF141516), c(0xFFEFF0F0), c(0xFFF7E600))),

    // Radical SR3: bare track-day LCD, red on silver-grey.
    RADICAL("Radical SR3", "Radical", LayoutFamily.BAR, ClusterTheme(
        c(0xFF0B0B0C), c(0xFF141416), c(0xFF232326), c(0xFFF2F2F4), c(0xFFB4B5BA), c(0xFF7C7D84),
        c(0xFFD8261C), c(0xFFF2F2F4), c(0xFFD8261C), c(0xFF00D68F), c(0xFF141416), c(0xFFF2F2F4), c(0xFFF2F2F4))),

    // TVR Tuscan: machined-aluminium oddball dash, TVR purple.
    TVR_TUSCAN("TVR Tuscan", "TVR", LayoutFamily.FIVE_DIAL, ClusterTheme(
        c(0xFF0D0C10), c(0xFF161419), c(0xFF2A2730), c(0xFFF1EFF4), c(0xFFB2ADBB), c(0xFF7E7889),
        c(0xFF8E5AE8), c(0xFFCFCAD8), c(0xFFE24A2E), c(0xFF3BD98A), c(0xFF121014), c(0xFFE9E5EF), c(0xFF8E5AE8))),

    // Volvo: calm Swedish twins, amber needles, Polestar-blue OK states.
    VOLVO("Volvo", "Volvo", LayoutFamily.TWIN, ClusterTheme(
        c(0xFF0D0E0F), c(0xFF161718), c(0xFF28292B), c(0xFFF1F2F2), c(0xFFAFB1B2), c(0xFF7C7F81),
        c(0xFFFFA000), c(0xFFFFA000), c(0xFFE24A2E), c(0xFF54B0E3), c(0xFF121314), c(0xFFECEDEE), c(0xFFFFA000),
        ring = c(0xFF4A4E53))),

    // Xiaomi SU7 Ultra: HyperOS dark cockpit, Xiaomi orange with cyan.
    XIAOMI_SU7("Xiaomi SU7 Ultra", "Xiaomi", LayoutFamily.DIGITAL_RING, ClusterTheme(
        c(0xFF0B0C0F), c(0xFF141619), c(0xFF25282E), c(0xFFF2F3F5), c(0xFFACB0B7), c(0xFF7B7F87),
        c(0xFFFF6900), c(0xFFFF6900), c(0xFFFF3B30), c(0xFF00C8D7), c(0xFF111215), c(0xFFECEEF1), c(0xFFFF6900),
        warn = c(0xFFFFD23D))),

    // Yangwang U9: quad-motor hypercar EV, jade green on graphite.
    YANGWANG_U9("Yangwang U9", "Yangwang", LayoutFamily.CENTRAL, ClusterTheme(
        c(0xFF0B0E0D), c(0xFF141817), c(0xFF252C2A), c(0xFFF0F4F2), c(0xFFAAB6B1), c(0xFF7A8781),
        c(0xFF00A98F), c(0xFF00A98F), c(0xFFE24A2E), c(0xFF00A98F), c(0xFF101413), c(0xFFEAEFEC), c(0xFF00A98F),
        ring = c(0xFF3C4A45)));

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
            "infiniti" to "nissan",
            "amuse" to "nissan",
            "greddy" to "nissan",
            "shelby" to "ford",
            "greening auto company" to "ford",
            "srt" to "dodge",
            "plymouth" to "dodge",
            "ruf" to "porsche",
            "autobianchi" to "abarth",
            "fiat" to "abarth",
            "daihatsu" to "suzuki",
            // GM stable and the hot-rod shops building on GM metal.
            "pontiac" to "chevrolet",
            "chaparral" to "chevrolet",
            "chris holstrom concepts" to "chevrolet",
            "roadster shop" to "chevrolet",
            "wicked fabrication" to "chevrolet",
            "eckert's rod & custom" to "chevrolet",
            "re amemiya" to "mazda",
            // The Pantera's dash is a wall of black Veglia dials — the same
            // instrument language as the Delta Integrale cluster.
            "de tomaso" to "lancia",
            "garage rcr" to "honda",
            "skoda" to "volkswagen",
            "ds automobiles" to "citroen",
            "polestar" to "volvo",
            // Design-house Vision GT one-offs wear the Gran Turismo cluster.
            "bvlgari" to "gran turismo",
            "italdesign" to "gran turismo",
            "zagato" to "gran turismo",
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
