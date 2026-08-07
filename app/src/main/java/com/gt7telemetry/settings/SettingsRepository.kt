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

/** Which API the built-in race engineer talks to (with the user's own key). */
enum class EngineerProvider(val label: String, val defaultModel: String, val keyHint: String) {
    ANTHROPIC("Claude (Anthropic)", "claude-sonnet-5", "sk-ant-…  (console.anthropic.com)"),
    GEMINI("Gemini (Google)", "gemini-2.5-flash", "AIza…  (aistudio.google.com — free tier)"),
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gt7_settings")

/** DataStore-backed user preferences: PS5 address, dashboard selection + display units. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val PS5_IP = stringPreferencesKey("ps5_ip")
        val MODE = stringPreferencesKey("dash_mode")
        val LAYOUT = stringPreferencesKey("manual_layout")
        val MPH = booleanPreferencesKey("use_mph")
        val FAHRENHEIT = booleanPreferencesKey("use_fahrenheit")
        val LEGACY_PACKET = booleanPreferencesKey("legacy_packet")
        val ENGINEER_PROVIDER = stringPreferencesKey("engineer_provider")
        val ENGINEER_KEY = stringPreferencesKey("engineer_api_key")
        val ENGINEER_MODEL = stringPreferencesKey("engineer_model")
        val SETUP_NOTES = stringPreferencesKey("setup_notes")
        val TYRES = stringPreferencesKey("tyres_fitted")
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

    /**
     * Heartbeat the legacy 'A' (296-byte packet, no steering) instead of '~'.
     * Only needed for a GT7 older than 1.42 that won't answer '~' at all.
     */
    val legacyPacket: Flow<Boolean> = context.dataStore.data.map { it[Keys.LEGACY_PACKET] ?: false }

    val engineerProvider: Flow<EngineerProvider> = context.dataStore.data.map { p ->
        runCatching { EngineerProvider.valueOf(p[Keys.ENGINEER_PROVIDER] ?: "") }
            .getOrDefault(EngineerProvider.ANTHROPIC)
    }

    /** The user's own API key, stored only on-device. Blank = built-in engineer off. */
    val engineerApiKey: Flow<String> = context.dataStore.data.map { it[Keys.ENGINEER_KEY] ?: "" }

    /** Model override; blank = the provider's default. */
    val engineerModel: Flow<String> = context.dataStore.data.map { it[Keys.ENGINEER_MODEL] ?: "" }

    /** Free-text description of the car's current tune (GT7 doesn't broadcast it). */
    val setupNotes: Flow<String> = context.dataStore.data.map { it[Keys.SETUP_NOTES] ?: "" }

    /** Tyre compound fitted, driver-declared (GT7 doesn't broadcast it); stamped onto laps. */
    val tyres: Flow<String> = context.dataStore.data.map { it[Keys.TYRES] ?: "" }

    suspend fun setPs5Ip(ip: String) = context.dataStore.edit { it[Keys.PS5_IP] = ip.trim() }
    suspend fun setDashMode(mode: DashMode) = context.dataStore.edit { it[Keys.MODE] = mode.name }
    suspend fun setManualLayout(layout: String) = context.dataStore.edit { it[Keys.LAYOUT] = layout }
    suspend fun setUseMph(v: Boolean) = context.dataStore.edit { it[Keys.MPH] = v }
    suspend fun setUseFahrenheit(v: Boolean) = context.dataStore.edit { it[Keys.FAHRENHEIT] = v }
    suspend fun setLegacyPacket(v: Boolean) = context.dataStore.edit { it[Keys.LEGACY_PACKET] = v }
    suspend fun setEngineerProvider(p: EngineerProvider) =
        context.dataStore.edit { it[Keys.ENGINEER_PROVIDER] = p.name }
    suspend fun setEngineerApiKey(k: String) = context.dataStore.edit { it[Keys.ENGINEER_KEY] = k.trim() }
    suspend fun setEngineerModel(m: String) = context.dataStore.edit { it[Keys.ENGINEER_MODEL] = m.trim() }
    suspend fun setSetupNotes(n: String) = context.dataStore.edit { it[Keys.SETUP_NOTES] = n }
    suspend fun setTyres(t: String) = context.dataStore.edit { it[Keys.TYRES] = t }
}
