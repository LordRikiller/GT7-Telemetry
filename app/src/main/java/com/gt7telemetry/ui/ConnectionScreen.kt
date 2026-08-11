package com.gt7telemetry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gt7telemetry.TelemetryRepository
import com.gt7telemetry.TelemetryService

/**
 * True while any VPN is up on this phone. A VPN is the classic silent killer
 * of GT7 telemetry: the heartbeat leaves through the tunnel (so the console
 * never sees it) or the 60 Hz UDP return stream gets dropped — so we call it
 * out instead of showing an unexplained "no packets".
 */
@Composable
internal fun rememberVpnActive(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    var active by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        val vpns = java.util.Collections.synchronizedSet(mutableSetOf<android.net.Network>())
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                vpns.add(network); active = vpns.isNotEmpty()
            }
            override fun onLost(network: android.net.Network) {
                vpns.remove(network); active = vpns.isNotEmpty()
            }
        }
        // Default requests exclude VPNs — ask for them explicitly.
        val req = android.net.NetworkRequest.Builder()
            .addTransportType(android.net.NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        cm.registerNetworkCallback(req, cb)
        onDispose { cm.unregisterNetworkCallback(cb) }
    }
    return active
}

/**
 * The PlayStation connection page: live link status, the console's IP, the
 * how-to steps, and the legacy-packet escape hatch. Reachable from Settings
 * and from the home screen's connection card.
 */
@Composable
fun ConnectionScreen(viewModel: DashboardViewModel, onBack: () -> Unit) {
    val ps5Ip by viewModel.ps5Ip.collectAsStateWithLifecycle()
    val legacyPacket by viewModel.legacyPacket.collectAsStateWithLifecycle()
    val status by TelemetryRepository.status.collectAsStateWithLifecycle()
    val vpnActive = rememberVpnActive()

    Column(
        Modifier.fillMaxSize().background(Palette.Asphalt).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill("‹ BACK", onClick = onBack)
            Spacer(Modifier.width(10.dp))
            Text("CONNECTION", color = Palette.Paint, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(10.dp))

        // ---- Live status ---------------------------------------------------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Label("STATUS")
                Spacer(Modifier.height(8.dp))
                val (txt, col) = when {
                    status.live -> "CONNECTED — RECEIVING TELEMETRY" to Palette.Good
                    status.everReceived -> "NO PACKETS — GT7 CLOSED OR CONSOLE OFF?" to Palette.Hot
                    ps5Ip.isBlank() -> "NOT SET UP YET" to Palette.InkMute
                    else -> "WAITING FOR FIRST PACKET…" to Palette.InkDim
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(col.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(txt, color = col, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp)
                    }
                }
                if (status.live) {
                    Spacer(Modifier.height(6.dp))
                    Text("${status.packetsPerSec} packets/s · " +
                        (if (status.extendedPacket) "extended '~' format (steering available)"
                        else "legacy format — no steering channel"),
                        color = Palette.InkDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                } else if (status.everReceived && status.lastPacketAgeMs != Long.MAX_VALUE) {
                    Spacer(Modifier.height(6.dp))
                    Text("Last packet received ${Fmt.age(status.lastPacketAgeMs)} ago",
                        color = Palette.InkDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                if (vpnActive) {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.clip(RoundedCornerShape(8.dp))
                        .background(Palette.Over.copy(alpha = 0.14f)).padding(10.dp)) {
                        Text(
                            "VPN ACTIVE ON THIS PHONE — VPNs usually block GT7's local UDP " +
                                "stream. Pause the VPN (or exempt this app from it) to receive telemetry.",
                            color = Palette.Over, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // ---- Console address -----------------------------------------------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Label("PLAYSTATION 5")
                Spacer(Modifier.height(8.dp))
                StepRow("1.", "On the PS5: Settings → Network → Connection Status")
                StepRow("2.", "Note the console's IPv4 address and enter it below")
                StepRow("3.", "Start Gran Turismo 7 and drive — GT7 streams automatically, nothing to enable in-game")
                Spacer(Modifier.height(12.dp))
                var draft by remember(ps5Ip) { mutableStateOf(ps5Ip) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("PS5 IP address") },
                        placeholder = { Text("192.168.1.20") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = { viewModel.setPs5Ip(draft) }, enabled = draft.isNotBlank() && draft.trim() != ps5Ip) { Text("Save") }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "The PS5 and this phone must be on the same Wi-Fi network. The app asks the " +
                        "console for GT7's telemetry stream on UDP ${TelemetryService.SEND_PORT}.",
                    color = Palette.InkDim, fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // ---- Legacy packet -------------------------------------------------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Label("TELEMETRY FORMAT")
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Legacy telemetry packet", color = Palette.Ink, fontSize = 14.sp)
                        Text(
                            if (legacyPacket) "296-byte 'A' — no steering channel"
                            else "Extended '~' — steering + chassis G (GT7 ≥ 1.42)",
                            color = Palette.InkDim, fontSize = 12.sp,
                        )
                    }
                    Switch(checked = legacyPacket, onCheckedChange = { viewModel.setLegacyPacket(it) })
                }
                Text(
                    "Leave this off. Only switch it on if a very old GT7 version refuses to stream at all.",
                    color = Palette.InkMute, fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StepRow(num: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(num, color = Palette.Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(22.dp))
        Text(text, color = Palette.Ink, fontSize = 13.sp)
    }
}
