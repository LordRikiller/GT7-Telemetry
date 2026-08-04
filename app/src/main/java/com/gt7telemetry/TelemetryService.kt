package com.gt7telemetry

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.gt7telemetry.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.concurrent.thread
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Long-running foreground service that owns the UDP socket GT7 streams to.
 *
 * Unlike Forza's "Data Out" (game pushes to an address you type in-game), a
 * PS5 sends telemetry only while it keeps receiving heartbeats: we bind UDP
 * 33740 and send a 1-byte 'A' to the console's port 33739 — on start, every
 * 100 received packets (~1.7 s at 60 Hz) and whenever the stream goes quiet.
 * The console then streams encrypted packets back at 60 Hz.
 *
 * Runs the blocking receive loop on its own thread and pushes parsed frames
 * into [TelemetryRepository]. Survives the screen turning off so a mounted
 * phone keeps reading. The PS5's IP comes from settings and is picked up
 * live when the user changes it.
 */
class TelemetryService : Service() {

    @Volatile private var running = false
    @Volatile private var ps5Ip: String = ""
    private var socket: DatagramSocket? = null
    private var worker: Thread? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Packet-rate accounting.
    @Volatile private var packetCount = 0
    @Volatile private var lastPacketAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification())
        if (!running) startReceiver()
        return START_STICKY
    }

    private fun startReceiver() {
        running = true
        TelemetryRepository.setStatus(Status(listening = true))

        // Track the PS5 address setting live; the receive loop reads [ps5Ip].
        scope.launch {
            SettingsRepository(this@TelemetryService).ps5Ip.collect { ps5Ip = it.trim() }
        }

        worker = thread(name = "gt7-udp", isDaemon = true) {
            val buf = ByteArray(2048)
            try {
                val s = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(RECEIVE_PORT))
                    soTimeout = 1000
                }
                socket = s
                var windowStart = System.currentTimeMillis()
                var windowCount = 0
                var sinceHeartbeat = HEARTBEAT_EVERY // fire one immediately
                var lastHeartbeatAt = 0L

                while (running) {
                    // Heartbeat: keep the console streaming. Cheap enough to
                    // evaluate every loop pass; sends at most every ~1.6 s.
                    val now0 = System.currentTimeMillis()
                    if (sinceHeartbeat >= HEARTBEAT_EVERY || now0 - lastHeartbeatAt >= HEARTBEAT_MS) {
                        if (sendHeartbeat(s)) {
                            sinceHeartbeat = 0
                            lastHeartbeatAt = now0
                        } else {
                            lastHeartbeatAt = now0 // no/bad IP — retry after the interval, not every pass
                        }
                    }

                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        s.receive(packet)
                    } catch (_: java.net.SocketTimeoutException) {
                        // No data this second — refresh rate/age and loop.
                        publishRate(windowCount)
                        windowStart = System.currentTimeMillis()
                        windowCount = 0
                        continue
                    }

                    val frame = Packet.parse(packet.data, packet.length)
                    if (frame != null) {
                        packetCount++
                        windowCount++
                        sinceHeartbeat++
                        lastPacketAt = System.currentTimeMillis()
                        TelemetryRepository.publish(frame.copy(curLap = lapTimer.update(frame)))
                    }

                    val now = System.currentTimeMillis()
                    if (now - windowStart >= 1000) {
                        publishRate(windowCount)
                        windowStart = now
                        windowCount = 0
                    }
                }
            } catch (e: Exception) {
                TelemetryRepository.setStatus(Status(listening = false))
            } finally {
                socket?.close()
                socket = null
            }
        }
    }

    private val lapTimer = LapTimer()

    /** Send the 'A' heartbeat to the PS5. False when no valid IP is set yet. */
    private fun sendHeartbeat(s: DatagramSocket): Boolean {
        val ip = ps5Ip
        if (ip.isBlank()) return false
        return try {
            val payload = byteArrayOf('A'.code.toByte())
            s.send(DatagramPacket(payload, payload.size, InetAddress.getByName(ip), SEND_PORT))
            true
        } catch (_: Exception) {
            false // unresolvable/unreachable — keep listening, retry later
        }
    }

    private fun ageNow(): Long =
        if (lastPacketAt == 0L) Long.MAX_VALUE else System.currentTimeMillis() - lastPacketAt

    private fun publishRate(rate: Int) {
        TelemetryRepository.updateStatus { st ->
            st.copy(
                listening = true,
                packetsPerSec = rate,
                everReceived = st.everReceived || lastPacketAt != 0L,
                lastPacketAgeMs = ageNow(),
            )
        }
    }

    private fun stopEverything() {
        running = false
        socket?.close()
        socket = null
        worker = null
        TelemetryRepository.setStatus(Status(listening = false))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        socket?.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Telemetry receiver", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Listening for GT7 telemetry" }
            nm.createNotificationChannel(ch)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder
            .setContentTitle("GT7 Telemetry")
            .setContentText("Listening on UDP $RECEIVE_PORT")
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
    }

    companion object {
        /** GT7 streams telemetry to this port on the device that asked. */
        const val RECEIVE_PORT = 33740
        /** The console listens for heartbeats here. */
        const val SEND_PORT = 33739
        /** Re-heartbeat after this many packets (~1.7 s at 60 Hz)… */
        private const val HEARTBEAT_EVERY = 100
        /** …or after this much wall time, whichever comes first. */
        private const val HEARTBEAT_MS = 1600L

        private const val CHANNEL_ID = "gt7_telemetry"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "com.gt7telemetry.STOP"

        fun start(ctx: Context) {
            val i = Intent(ctx, TelemetryService::class.java)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            val i = Intent(ctx, TelemetryService::class.java).apply { action = ACTION_STOP }
            ctx.startService(i)
        }
    }
}
