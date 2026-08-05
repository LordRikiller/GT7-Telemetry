package com.gt7telemetry.dash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gt7telemetry.Frame
import kotlin.math.ceil

/**
 * Resolves a [DashLayout] to its themed instrument body. The landscape
 * families are wide, side-by-side arrangements; in portrait every family
 * swaps to a stacked variant that keeps the marque's theme and hero
 * instrument (dial, ring or big numerals) but flows vertically.
 */
@Composable
fun ClusterHost(frame: Frame, layout: DashLayout, useMph: Boolean, useFahrenheit: Boolean, modifier: Modifier = Modifier) {
    val theme = layout.theme
    val data = clusterData(frame, useMph, useFahrenheit)
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxHeight > maxWidth) {
            PortraitCluster(data, theme, layout)
        } else when (layout.family) {
            LayoutFamily.DEFAULT -> DefaultCluster(data, theme)
            LayoutFamily.CENTRAL -> CentralCluster(data, theme, layout)
            LayoutFamily.TWIN -> TwinCluster(data, theme, layout)
            LayoutFamily.FIVE_DIAL -> FiveDialCluster(data, theme)
            LayoutFamily.BAR -> BarCluster(data, theme)
            LayoutFamily.MINIMAL -> MinimalCluster(data, theme)
            LayoutFamily.TILES -> TilesCluster(data, theme)
            LayoutFamily.DIGITAL_RING -> DigitalRingCluster(data, theme)
            LayoutFamily.OFFSET -> OffsetCluster(data, theme, layout)
        }
    }
}

// --- PORTRAIT (all families) ------------------------------------------------
@Composable
private fun PortraitCluster(data: ClusterData, theme: ClusterTheme, layout: DashLayout) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Hero instrument, themed per family.
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when (layout.family) {
                LayoutFamily.DIGITAL_RING -> Dial(
                    Modifier.fillMaxSize(),
                    gauge = { SegmentedRing(data.rpm / 1000f, tachMax(data), theme, Modifier.fillMaxSize(), segments = 36) },
                    center = {
                        Num(data.gear, theme, 56, color = theme.accent, weight = FontWeight.Black)
                        GearHint(data, theme)
                        Num("${data.speed}", theme, 22)
                        Lab("${data.speedUnit} · ${data.rpm} RPM", theme)
                    })
                LayoutFamily.DEFAULT, LayoutFamily.BAR, LayoutFamily.MINIMAL -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Num("${data.speed}", theme, 96, color = theme.accent, weight = FontWeight.Black)
                    Lab(data.speedUnit, theme)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Lab("Gear ", theme)
                        Num(data.gear, theme, 40, weight = FontWeight.Black)
                        Spacer(Modifier.width(8.dp))
                        GearHint(data, theme)
                    }
                    Lab("${data.rpm} / ${data.rpmMax} RPM", theme)
                }
                // Analog families keep their marque's hero dial — the speedo
                // for speed-hero layouts (Mini, Bugatti), the tach otherwise —
                // and everything else drops to the digital cards below.
                else -> Dial(
                    Modifier.fillMaxSize(),
                    gauge = {
                        if (layout.heroSpeed) RadialGauge(data.speed.toFloat(), layout.spdMax.toFloat(), theme, Modifier.fillMaxSize(), majorStep = layout.spdMax / 6f, bezel = true)
                        else RadialGauge(data.rpm / 1000f, tachMax(data), theme, Modifier.fillMaxSize(), redline = tachRedline(data), bezel = true)
                    },
                    center = {
                        Num(data.gear, theme, 52, color = theme.dialGear, weight = FontWeight.Black)
                        GearHint(data, theme)
                        Num(if (layout.heroSpeed) "${data.rpm}" else "${data.speed}", theme, 22, color = theme.dialText)
                        Lab(if (layout.heroSpeed) "rpm" else data.speedUnit, theme)
                    })
            }
        }
        ShiftLights(data.rpmFrac, theme, Modifier.fillMaxWidth())
        RevBar(data, theme, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimingBlock(data, theme, Modifier.weight(1f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Tile(theme, Modifier.fillMaxWidth()) {
                    Column { Lab("Tyres ${data.tempUnit}", theme); Spacer(Modifier.height(6.dp)); TyrePods(data, theme) }
                }
            }
        }
        RaceStrip(data, theme, Modifier.fillMaxWidth())
        VitalsStrip(data, theme, Modifier.fillMaxWidth())
        IndicatorRow(data, theme, Modifier.fillMaxWidth())
    }
}

// GT7 broadcasts each car's rev-limiter, so the tach is scaled per car
// (an F1 tach reads to 12, a vintage racer to 6) instead of a fixed 8.
private fun tachMax(data: ClusterData): Float =
    ceil(data.rpmMax / 1000.0).toFloat().coerceIn(4f, 14f)

private fun tachRedline(data: ClusterData): Float =
    (data.rpmMax / 1000f) * data.redlineFrac

@Composable
private fun Dial(modifier: Modifier, gauge: @Composable () -> Unit, center: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) { gauge(); Column(horizontalAlignment = Alignment.CenterHorizontally) { center() } }
}

// --- DEFAULT ---------------------------------------------------------------
@Composable
private fun DefaultCluster(data: ClusterData, theme: ClusterTheme) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Num("${data.speed}", theme, 72, color = theme.accent, weight = FontWeight.Black)
                Lab(data.speedUnit, theme)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Num(data.gear, theme, 62, color = theme.ink, weight = FontWeight.Black)
                Lab("Gear", theme)
            }
        }
        ShiftLights(data.rpmFrac, theme, Modifier.fillMaxWidth())
        RevBar(data, theme, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimingBlock(data, theme, Modifier.weight(1f))
            Tile(theme, Modifier.weight(1f)) { Column { Lab("Tyres ${data.tempUnit}", theme); Spacer(Modifier.height(6.dp)); TyrePods(data, theme) } }
        }
        VitalsStrip(data, theme, Modifier.fillMaxWidth())
    }
}

// --- CENTRAL ---------------------------------------------------------------
@Composable
private fun CentralCluster(data: ClusterData, theme: ClusterTheme, layout: DashLayout) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TimingBlock(data, theme)
            Stat("Fuel", "${data.fuel}", "%", theme, Modifier.fillMaxWidth())
        }
        Dial(
            Modifier.weight(1.5f).fillMaxSize(),
            gauge = {
                if (layout.heroSpeed) RadialGauge(data.speed.toFloat(), layout.spdMax.toFloat(), theme, Modifier.fillMaxSize(), majorStep = layout.spdMax / 6f, bezel = true)
                else RadialGauge(data.rpm / 1000f, tachMax(data), theme, Modifier.fillMaxSize(), redline = tachRedline(data), bezel = true)
            },
            center = {
                Num(data.gear, theme, 52, color = theme.dialGear, weight = FontWeight.Black)
                GearHint(data, theme)
                Num(if (layout.heroSpeed) "${data.rpm}" else "${data.speed}", theme, 15, color = theme.dialText)
                Lab(if (layout.heroSpeed) "rpm" else data.speedUnit, theme)
            },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(theme) { Column { Lab("Tyres ${data.tempUnit}", theme); Spacer(Modifier.height(6.dp)); TyrePods(data, theme) } }
            Stat("Boost", data.boost, "psi", theme, Modifier.fillMaxWidth())
        }
    }
}

// --- TWIN ------------------------------------------------------------------
@Composable
private fun TwinCluster(data: ClusterData, theme: ClusterTheme, layout: DashLayout) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Dial(Modifier.weight(1f).fillMaxSize(),
                gauge = { RadialGauge(data.rpm / 1000f, tachMax(data), theme, Modifier.fillMaxSize(), redline = tachRedline(data), bezel = true) },
                center = { Lab("Revs ×1000", theme); Num("${data.rpm}", theme, 15) })
            Dial(Modifier.weight(1f).fillMaxSize(),
                gauge = { RadialGauge(data.speed.toFloat(), layout.spdMax.toFloat(), theme, Modifier.fillMaxSize(), majorStep = layout.spdMax / 6f, bezel = true) },
                center = { Lab(data.speedUnit, theme); Num("${data.speed}", theme, 17); Row(verticalAlignment = Alignment.CenterVertically) { Lab("Gear ", theme); Num(data.gear, theme, 13, color = theme.accent) } })
        }
        Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ConsoleChip("Lap ${data.lapNo}", data.lap, theme, Modifier.weight(1f))
            ConsoleChip("Best", data.best, theme, Modifier.weight(1f), color = theme.good)
            ConsoleChip("Δ", data.delta, theme, Modifier.weight(1f), color = if (data.deltaBad) theme.redline else theme.good)
            ConsoleChip("Tyres", data.tyres.joinToString(" "), theme, Modifier.weight(1.4f))
        }
    }
}

@Composable
private fun ConsoleChip(label: String, value: String, theme: ClusterTheme, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = theme.ink) {
    Tile(theme, modifier) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { Lab(label, theme); Num(value, theme, 14, color = color) } }
}

// --- FIVE DIAL -------------------------------------------------------------
@Composable
private fun FiveDialCluster(data: ClusterData, theme: ClusterTheme) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Dial(Modifier.weight(0.85f).fillMaxSize(),
            gauge = { RadialGauge(data.speed.toFloat(), 240f, theme, Modifier.fillMaxSize(), majorStep = 40f) },
            center = { Num("${data.speed}", theme, 15); Lab(data.speedUnit, theme) })
        Dial(Modifier.weight(1.25f).fillMaxSize(),
            gauge = { RadialGauge(data.rpm / 1000f, tachMax(data), theme, Modifier.fillMaxSize(), redline = tachRedline(data)) },
            center = { Num(data.gear, theme, 40, color = theme.dialGear, weight = FontWeight.Black); GearHint(data, theme); Lab("${data.rpm} rpm", theme) })
        Dial(Modifier.weight(0.85f).fillMaxSize(),
            gauge = { RadialGauge(data.boostVal.toFloat().coerceIn(0f, 20f), 20f, theme, Modifier.fillMaxSize(), majorStep = 5f) },
            center = { Num(data.boost, theme, 15); Lab("boost", theme) })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            TimingBlock(data, theme)
            Tile(theme) { Column { Lab("Tyres ${data.tempUnit}", theme); Spacer(Modifier.height(6.dp)); TyrePods(data, theme) } }
        }
    }
}

// --- BAR -------------------------------------------------------------------
@Composable
private fun BarCluster(data: ClusterData, theme: ClusterTheme) {
    Column(Modifier.fillMaxSize()) {
        ShiftLights(data.rpmFrac, theme, Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Num("${data.speed}", theme, 52, weight = FontWeight.Black); Lab(data.speedUnit, theme) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Num(data.gear, theme, 96, color = theme.ink, weight = FontWeight.Black); Lab("Gear", theme) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) { Num("${data.rpm}", theme, 30); Lab("rpm / ${data.rpmMax}", theme) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.clip(RoundedCornerShape(5.dp)).background(theme.redline).padding(horizontal = 9.dp, vertical = 3.dp)) {
                Text(data.delta, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            Text("${data.lapNo} ${data.lap} · best ${data.best}", color = theme.ink2, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            Text(data.tyres.mapIndexed { i, t -> "${listOf("FL","FR","RL","RR")[i]} $t" }.joinToString("  "), color = theme.ink2, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }
}

// --- MINIMAL ---------------------------------------------------------------
@Composable
private fun MinimalCluster(data: ClusterData, theme: ClusterTheme) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Num("${data.speed}", theme, 100, weight = FontWeight.Black)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Lab("${data.speedUnit}   ·   GEAR ", theme); Num(data.gear, theme, 14, color = theme.accent)
                Lab("   ·   ${data.rpm} / ${data.rpmMax} RPM", theme)
            }
        }
        RevBar(data, theme, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ConsoleChip("Lap", data.lap, theme, Modifier.weight(1f))
            ConsoleChip("Best", data.best, theme, Modifier.weight(1f), color = theme.good)
            ConsoleChip("Δ", data.delta, theme, Modifier.weight(1f), color = if (data.deltaBad) theme.redline else theme.good)
            ConsoleChip("Tyres", data.tyres.joinToString(" "), theme, Modifier.weight(1.4f))
        }
    }
}

// --- TILES -----------------------------------------------------------------
@Composable
private fun TilesCluster(data: ClusterData, theme: ClusterTheme) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Dial(Modifier.weight(0.9f).fillMaxSize(),
            gauge = { RadialGauge(data.rpm / 1000f, tachMax(data), theme, Modifier.fillMaxSize(), redline = tachRedline(data)) },
            center = { Num(data.gear, theme, 36, color = theme.dialGear, weight = FontWeight.Black); GearHint(data, theme); Lab("${data.rpm}", theme) })
        Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val cells = listOf(
                "Speed" to "${data.speed}", "Boost" to data.boost, "Fuel" to "${data.fuel}%",
                "Lap ${data.lapNo}" to data.lap, "Best" to data.best, "Δ" to data.delta,
            )
            for (r in 0..1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col2 in 0..2) {
                        val (k, v) = cells[r * 3 + col2]
                        Tile(theme, Modifier.weight(1f)) { Column { Lab(k, theme); Num(v, theme, 15) } }
                    }
                }
            }
            TyrePods(data, theme)
        }
    }
}

// --- DIGITAL RING ----------------------------------------------------------
@Composable
private fun DigitalRingCluster(data: ClusterData, theme: ClusterTheme) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Dial(Modifier.weight(1.3f).fillMaxSize(),
            gauge = { SegmentedRing(data.rpm / 1000f, tachMax(data), theme, Modifier.fillMaxSize(), segments = 36) },
            center = {
                Num(data.gear, theme, 50, color = theme.accent, weight = FontWeight.Black)
                GearHint(data, theme)
                Num("${data.speed}", theme, 18)
                Lab("${data.rpm} / ${data.rpmMax}", theme)
            })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TimingBlock(data, theme)
            Tile(theme) { Column { Lab("Tyres ${data.tempUnit}", theme); Spacer(Modifier.height(6.dp)); TyrePods(data, theme) } }
        }
    }
}

// --- OFFSET ----------------------------------------------------------------
@Composable
private fun OffsetCluster(data: ClusterData, theme: ClusterTheme, layout: DashLayout) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Dial(Modifier.weight(1.25f).fillMaxSize(),
            gauge = { RadialGauge(data.rpm / 1000f, tachMax(data), theme, Modifier.fillMaxSize(), redline = tachRedline(data), bezel = true) },
            center = { Num(data.gear, theme, 44, color = theme.dialGear, weight = FontWeight.Black); GearHint(data, theme); Lab("${data.rpm} rpm", theme) })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Column { Num("${data.speed}", theme, 54, weight = FontWeight.Black); Lab(data.speedUnit, theme) }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Stat("Lap ${data.lapNo}", data.lap, "", theme, Modifier.weight(1f))
                Stat("Δ best", data.delta, "", theme, Modifier.weight(1f))
            }
            TyrePods(data, theme)
        }
    }
}
