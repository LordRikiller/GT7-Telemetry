package com.gt7telemetry.update

import kotlinx.serialization.Serializable
import java.io.File

/**
 * The update manifest GT7 Telemetry fetches from [UpdateChecker.MANIFEST_URL].
 *
 * Expected JSON (host this at the manifest URL — e.g. served by your worker,
 * which can proxy the latest GitHub release so no token ships in the APK):
 * {
 *   "versionCode": 2,
 *   "versionName": "0.2.0",
 *   "apkUrl": "https://.../gt7-telemetry.apk",
 *   "notes": "What's new in this version"
 * }
 *
 * An update is offered when [versionCode] is greater than the installed one.
 */
@Serializable
data class UpdateManifest(
    val versionCode: Long,
    val versionName: String = "",
    val apkUrl: String,
    val notes: String = "",
)

/** State of the in-app update flow, surfaced to the UI. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val manifest: UpdateManifest) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class ReadyToInstall(val file: File, val manifest: UpdateManifest) : UpdateState
    data class Failed(val message: String) : UpdateState
}
