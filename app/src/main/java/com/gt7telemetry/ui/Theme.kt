package com.gt7telemetry.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Midnight-garage palette (v0.10). Cooler, bluer blacks with more contrast
 * than the old warm-brown set, amber kept as the identity accent, plus a
 * racing red and an electric blue for hero moments. The per-car cluster
 * themes are untouched — this styles the app around the instruments.
 */
object Palette {
    val Asphalt = Color(0xFF0B0F14)   // page background — blue-black
    val Carbon = Color(0xFF141B23)    // card surface
    val Carbon2 = Color(0xFF1E2833)   // nested surface / controls
    val Line = Color(0xFF2B3745)      // hairlines
    val Paint = Color(0xFFF3F6FA)     // primary numerals
    val Ink = Color(0xFFC5CFDA)       // body text
    val InkDim = Color(0xFF92A0B0)    // secondary
    val InkMute = Color(0xFF64717F)   // labels
    val Amber = Color(0xFFFFB300)     // identity accent
    val AmberInk = Color(0xFF1A1200)
    val Red = Color(0xFFFF3B4E)       // racing red — REC, hero highlights
    val Blue = Color(0xFF44A5FF)      // electric blue — secondary accent
    val Cold = Color(0xFF44A5FF)
    val Optimal = Color(0xFF2FE08D)
    val Hot = Color(0xFFFFB300)
    val Over = Color(0xFFFF4D55)
    val Good = Color(0xFF2FE08D)
    val Bad = Color(0xFFFF4D55)

    /** Hero-card backdrop: a blue racing glow fading into the asphalt. */
    val HeroBrush = Brush.linearGradient(
        colors = listOf(Color(0xFF16324F), Color(0xFF11202F), Carbon),
    )
}

private val Gt7Colors = darkColorScheme(
    primary = Palette.Amber,
    onPrimary = Palette.AmberInk,
    secondary = Palette.Blue,
    background = Palette.Asphalt,
    onBackground = Palette.Paint,
    surface = Palette.Carbon,
    onSurface = Palette.Paint,
    surfaceVariant = Palette.Carbon2,
    onSurfaceVariant = Palette.Ink,
    error = Palette.Over,
)

@Composable
fun Gt7Theme(content: @Composable () -> Unit) {
    // Always dark — this is a night-driving instrument.
    MaterialTheme(colorScheme = Gt7Colors, content = content)
}
