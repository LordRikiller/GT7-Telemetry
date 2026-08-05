package com.gt7telemetry.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Warm-black instrument palette (shared with the FH6 app).
object Palette {
    val Asphalt = Color(0xFF16130F)   // page background
    val Carbon = Color(0xFF211C16)    // card surface
    val Carbon2 = Color(0xFF2B241C)   // nested surface / controls
    val Line = Color(0xFF372F25)      // hairlines
    val Paint = Color(0xFFF2EDE3)     // primary numerals
    val Ink = Color(0xFFC9BEAD)       // body text
    val InkDim = Color(0xFF9A8F7D)    // secondary
    val InkMute = Color(0xFF6E6557)   // labels
    val Amber = Color(0xFFFFAE00)     // identity accent
    val AmberInk = Color(0xFF1A1200)
    val Cold = Color(0xFF4FA8FF)
    val Optimal = Color(0xFF3BD98A)
    val Hot = Color(0xFFFFAE00)
    val Over = Color(0xFFFF4D42)
    val Good = Color(0xFF3BD98A)
    val Bad = Color(0xFFFF4D42)
}

private val Gt7Colors = darkColorScheme(
    primary = Palette.Amber,
    onPrimary = Palette.AmberInk,
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
