package com.gt7telemetry.update

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks a remote manifest for a newer version and downloads its APK.
 *
 * [MANIFEST_URL] points at the update endpoint (a worker route that serves the
 * latest release from KV so no token ships in the APK). If it's ever blanked,
 * checks report "not configured" instead of erroring.
 */
object UpdateChecker {

    // Cloudflare Worker (KV-backed) that serves the update manifest + APK.
    const val MANIFEST_URL = "https://gt7-updates.fh6rik.workers.dev/latest.json"

    val isConfigured: Boolean get() = MANIFEST_URL.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true }

    fun currentVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(info)
    }

    suspend fun check(context: Context): UpdateState = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext UpdateState.Failed("Update source not configured")
        try {
            val body = httpGetText(MANIFEST_URL)
            val manifest = json.decodeFromString<UpdateManifest>(body)
            if (manifest.versionCode > currentVersionCode(context)) {
                UpdateState.Available(manifest)
            } else {
                UpdateState.UpToDate
            }
        } catch (e: Exception) {
            UpdateState.Failed(e.message ?: "Update check failed")
        }
    }

    suspend fun download(
        context: Context,
        manifest: UpdateManifest,
        onProgress: (Int) -> Unit,
    ): UpdateState = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            // Clear old downloads so we don't accumulate stale APKs.
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "gt7-telemetry-${manifest.versionCode}.apk")

            val conn = (URL(manifest.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                return@withContext UpdateState.Failed("Download failed (HTTP ${conn.responseCode})")
            }
            val total = conn.contentLength.toLong()
            var downloaded = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) onProgress(((downloaded * 100) / total).toInt())
                    }
                }
            }
            UpdateState.ReadyToInstall(file, manifest)
        } catch (e: Exception) {
            UpdateState.Failed(e.message ?: "Download failed")
        }
    }

    private fun httpGetText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            requestMethod = "GET"
        }
        conn.inputStream.use { return it.readBytes().toString(Charsets.UTF_8) }
    }
}
