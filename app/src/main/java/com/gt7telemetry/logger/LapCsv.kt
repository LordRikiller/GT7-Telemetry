package com.gt7telemetry.logger

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/**
 * Full-rate CSV export of a recorded lap — every 60 Hz sample, every
 * channel — for spreadsheets or external analysis tools. The file lands in
 * the app's cache and is handed out through the existing FileProvider.
 */
object LapCsv {

    fun build(lap: RecordedLap, carName: String?): String = buildString {
        appendLine("# GT7 Telemetry lap export")
        appendLine("# car: ${carName ?: "car #${lap.carOrdinal}"}")
        appendLine("# lap: ${lap.lapNumber} · time_s: ${"%.3f".fmt(lap.lapTimeS)}")
        if (lap.tyres.isNotBlank()) appendLine("# tyres (driver-declared): ${lap.tyres}")
        appendLine("# tyre wear is not broadcast by GT7; tyre_*_c are surface temperatures in Celsius")
        appendLine("# track length (measured from the driven line): ${"%.1f".fmt(lap.trackLengthM)} m")
        append("t_s,speed_kmh,throttle_pct,brake_pct,steer_deg,gear,rpm,")
        appendLine("pos_x,pos_y,pos_z,lat_g,long_g,clutch_pct,tyre_fl_c,tyre_fr_c,tyre_rl_c,tyre_rr_c")
        val temps = lap.hasTyreTemps
        val elev = lap.hasElevation
        for (i in 0 until lap.sampleCount) {
            val steer = lap.steerDeg[i]
            append("%.3f,%.1f,%.0f,%.0f,%s,%.0f,%.0f,%.2f,%s,%.2f,%.3f,%.3f,%.0f".fmt(
                lap.t[i].toDouble(), lap.speedKmh[i].toDouble(),
                lap.throttlePct[i].toDouble(), lap.brakePct[i].toDouble(),
                if (steer.isNaN()) "" else "%.1f".fmt(steer.toDouble()),
                lap.gear[i].toDouble(), lap.rpm[i].toDouble(),
                lap.posX[i].toDouble(),
                if (elev) "%.2f".fmt(lap.posY[i].toDouble()) else "",
                lap.posZ[i].toDouble(),
                lap.latG[i].toDouble(), lap.longG[i].toDouble(), lap.clutchPct[i].toDouble(),
            ))
            if (temps) appendLine(",%.1f,%.1f,%.1f,%.1f".fmt(
                lap.tyreFL[i].toDouble(), lap.tyreFR[i].toDouble(),
                lap.tyreRL[i].toDouble(), lap.tyreRR[i].toDouble(),
            )) else appendLine(",,,,")
        }
    }

    /** Write the CSV to cache and open the system share sheet. Call off the main thread. */
    fun share(context: Context, lap: RecordedLap, carName: String?) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "gt7_lap%d_%013d.csv".format(lap.lapNumber, lap.recordedAtMs))
        file.writeText(build(lap, carName))
        val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Export lap CSV…").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // Matches the manifest's FileProvider (${applicationId}.updates).
    private const val AUTHORITY = "com.gt7telemetry.updates"

    private fun String.fmt(vararg args: Any?): String = String.format(Locale.US, this, *args)
}
