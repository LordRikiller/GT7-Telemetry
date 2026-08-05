package com.gt7telemetry.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How the active dashboard layout is chosen. */
enum class DashMode { AUTO, MANUAL }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gt7_settings")

/** DataStore-backed user preferences: PS5 address, dashboard selection + display units. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val PS5_IP = stringPreferencesKey("ps5_ip")
        val MODE = stringPreferencesKey("dash_mode")
        val LAYOUT = stringPreferencesKey("manual_layout")
        val MPH = booleanPreferencesKey("use_mph")
        val FAHRENHEIT = booleanPreferencesKey("use_fahrenheit")
    }

    /** The console's LAN IP — where heartbeats go. Blank until the user sets it. */
    val ps5Ip: Flow<String> = context.dataStore.data.map { it[Keys.PS5_IP] ?: "" }

    val dashMode: Flow<DashMode> = context.dataStore.data.map { p ->
        runCatching { DashMode.valueOf(p[Keys.MODE] ?: DashMode.AUTO.name) }.getOrDefault(DashMode.AUTO)
    }

    /** Stored as the [com.gt7telemetry.dash.DashLayout] enum name; default DEFAULT. */
    val manualLayout: Flow<String> = context.dataStore.data.map { it[Keys.LAYOUT] ?: "DEFAULT" }

    val useMph: Flow<Boolean> = context.dataStore.data.map { it[Keys.MPH] ?: false }
    val useFahrenheit: Flow<Boolean> = context.dataStore.data.map { it[Keys.FAHRENHEIT] ?: false }

    suspend fun setPs5Ip(ip: String) = context.dataStore.edit { it[Keys.PS5_IP] = ip.trim() }
    suspend fun setDashMode(mode: DashMode) = context.dataStore.edit { it[Keys.MODE] = mode.name }
    suspend fun setManualLayout(layout: String) = context.dataStore.edit { it[Keys.LAYOUT] = layout }
    suspend fun setUseMph(v: Boolean) = context.dataStore.edit { it[Keys.MPH] = v }
    suspend fun setUseFahrenheit(v: Boolean) = context.dataStore.edit { it[Keys.FAHRENHEIT] = v }
}
