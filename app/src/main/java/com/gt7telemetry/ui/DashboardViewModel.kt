package com.gt7telemetry.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gt7telemetry.car.CarCatalog
import com.gt7telemetry.car.SetupProbe
import com.gt7telemetry.dash.DashLayout
import com.gt7telemetry.engineer.Briefing
import com.gt7telemetry.engineer.EngineerClient
import com.gt7telemetry.logger.LapRecorder
import com.gt7telemetry.logger.LapStore
import com.gt7telemetry.logger.RecordedLap
import com.gt7telemetry.settings.DashMode
import com.gt7telemetry.settings.EngineerProvider
import com.gt7telemetry.settings.SettingsRepository
import com.gt7telemetry.setup.SetupSheet
import com.gt7telemetry.setup.SetupSheetStore
import com.gt7telemetry.track.TrackStore
import com.gt7telemetry.update.UpdateChecker
import com.gt7telemetry.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns user settings (PS5 address, dashboard selection + units), the car-name
 * catalog, and the in-app update flow. Telemetry itself flows straight from
 * [com.gt7telemetry.TelemetryRepository] to the UI.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)

    val ps5Ip: StateFlow<String> =
        settings.ps5Ip.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val dashMode: StateFlow<DashMode> =
        settings.dashMode.stateIn(viewModelScope, SharingStarted.Eagerly, DashMode.AUTO)
    val manualLayout: StateFlow<DashLayout> =
        settings.manualLayout.map { DashLayout.byNameOrDefault(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, DashLayout.DEFAULT)
    val useMph: StateFlow<Boolean> =
        settings.useMph.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val useFahrenheit: StateFlow<Boolean> =
        settings.useFahrenheit.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val legacyPacket: StateFlow<Boolean> =
        settings.legacyPacket.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Bumped whenever the car catalog gains data (bundled load, live refresh). */
    private val _catalogRevision = MutableStateFlow(0)
    val catalogRevision: StateFlow<Int> = _catalogRevision.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    init {
        // Per-car setup sheets live on disk; a handful of small JSON files.
        SetupSheetStore.init(application)
        // Learned track fingerprints (name it once, recognised forever).
        TrackStore.init(application)
        // Load the bundled ~575-car catalog off the main thread, then refresh
        // it from the live community DB so cars added in a game patch resolve
        // (and the auto dashboard picker works for them) without an app update.
        viewModelScope.launch {
            withContext(Dispatchers.IO) { CarCatalog.load(getApplication()) }
            _catalogRevision.value = CarCatalog.revision
            withContext(Dispatchers.IO) { CarCatalog.refresh(getApplication()) }
            _catalogRevision.value = CarCatalog.revision
        }
        // Silently check for an app update on launch (if an endpoint is set).
        if (UpdateChecker.isConfigured) checkForUpdates()
    }

    // ---- Race engineer ---------------------------------------------------

    val engineerProvider: StateFlow<EngineerProvider> =
        settings.engineerProvider.stateIn(viewModelScope, SharingStarted.Eagerly, EngineerProvider.ANTHROPIC)
    val engineerApiKey: StateFlow<String> =
        settings.engineerApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val engineerModel: StateFlow<String> =
        settings.engineerModel.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val setupNotes: StateFlow<String> =
        settings.setupNotes.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val tyres: StateFlow<String> =
        settings.tyres.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    sealed interface EngineerState {
        data object Idle : EngineerState
        data object Working : EngineerState
        data class Done(val text: String) : EngineerState
        data class Error(val message: String) : EngineerState
    }

    private val _engineerState = MutableStateFlow<EngineerState>(EngineerState.Idle)
    val engineerState: StateFlow<EngineerState> = _engineerState.asStateFlow()

    /** Laps handed to the engineer from lap history. Null = live session. */
    data class EngineerSource(val laps: List<RecordedLap>, val label: String)

    private val _engineerSource = MutableStateFlow<EngineerSource?>(null)
    val engineerSource: StateFlow<EngineerSource?> = _engineerSource.asStateFlow()

    /**
     * Point the engineer at a stored lap's whole session: every stored lap of
     * the same car recorded within ±3 h of the picked one (capped at the 30
     * most recent so the briefing stays bounded). Calls [onReady] once loaded.
     */
    fun openEngineerWithStored(meta: LapStore.StoredLapMeta, onReady: () -> Unit) {
        viewModelScope.launch {
            val laps = withContext(Dispatchers.IO) {
                LapStore.entries.value
                    .filter {
                        it.carOrdinal == meta.carOrdinal &&
                            kotlin.math.abs(it.recordedAtMs - meta.recordedAtMs) < 3 * 3600_000L
                    }
                    .sortedBy { it.recordedAtMs }
                    .takeLast(30)
                    .mapNotNull { LapStore.load(it) }
            }
            if (laps.isNotEmpty()) {
                val car = CarCatalog.lookup(meta.carOrdinal)?.name
                    ?: meta.carOrdinal.takeIf { it != 0 }?.let { "Car #$it" } ?: "Unknown car"
                val date = java.text.SimpleDateFormat("d MMM · HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(meta.recordedAtMs))
                _engineerSource.value = EngineerSource(laps, "$car · $date")
            }
            onReady()
        }
    }

    /** Back to analysing the live session's laps. */
    fun useLiveSession() { _engineerSource.value = null }

    /** Build the shareable/analysable briefing text for the current session. */
    fun buildBriefing(laps: List<RecordedLap>): String {
        val ordinal = laps.lastOrNull()?.carOrdinal
        val car = CarCatalog.lookup(ordinal)?.name ?: ordinal?.takeIf { it != 0 }?.let { "GT7 car #$it" }
        // Only attach measured setup that belongs to the recorded car.
        val measured = SetupProbe.setup.value?.takeIf { ordinal == null || it.carOrdinal == ordinal }
        // The recorded car's saved setup sheet is pulled up automatically.
        val sheet = SetupSheetStore.forCar(ordinal)?.takeIf { it.hasAnyValues }
        val track = laps.lastOrNull()?.let { TrackStore.identify(it) }
        return Briefing.build(laps, car, setupNotes.value, measured, sheet, track)
    }

    /** Teach the app this lap's track name (recognised automatically after). */
    fun nameTrack(name: String, lap: RecordedLap) {
        TrackStore.learn(name, lap)
        trackRevision.value++
    }

    /** Bumped when a track is named so open screens re-run identification. */
    val trackRevision = MutableStateFlow(0)

    /**
     * The built-in engineer: exactly one bounded API request per press.
     * No loop, no retries — cost is capped by construction.
     */
    fun askEngineer(laps: List<RecordedLap>) {
        if (_engineerState.value is EngineerState.Working) return
        val briefing = buildBriefing(laps)
        val provider = engineerProvider.value
        val key = engineerApiKey.value
        val model = engineerModel.value
        if (key.isBlank()) {
            _engineerState.value = EngineerState.Error("Add your API key below first.")
            return
        }
        _engineerState.value = EngineerState.Working
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { EngineerClient.ask(provider, key, model, briefing) }
            _engineerState.value = result.fold(
                onSuccess = { EngineerState.Done(it) },
                onFailure = { EngineerState.Error(it.message ?: "Request failed") },
            )
        }
    }

    fun resetEngineer() { _engineerState.value = EngineerState.Idle }
    fun clearLaps() = LapRecorder.clear()
    fun setTyres(t: String) = viewModelScope.launch { settings.setTyres(t) }

    // ---- Setup sheets (per-car, GT7 settings-sheet replica) ----------------

    val setupSheets: StateFlow<Map<Int, SetupSheet>> = SetupSheetStore.sheets

    /** The car the sheet editor should open on (set before navigating). */
    @Volatile var editorCarOrdinal: Int? = null

    fun saveSetupSheet(sheet: SetupSheet) {
        val named = if (sheet.carName.isBlank())
            sheet.copy(carName = CarCatalog.lookup(sheet.carOrdinal)?.name ?: "") else sheet
        SetupSheetStore.save(named)
    }

    fun deleteSetupSheet(ordinal: Int) = SetupSheetStore.delete(ordinal)

    // ---- Lap history (on-disk) --------------------------------------------

    val lapHistory: StateFlow<List<LapStore.StoredLapMeta>> = LapStore.entries

    /** Load a stored lap's full trace off the main thread, then hand it to the UI. */
    fun loadStoredLap(meta: LapStore.StoredLapMeta, onLoaded: (RecordedLap?) -> Unit) {
        viewModelScope.launch {
            val lap = withContext(Dispatchers.IO) { LapStore.load(meta) }
            onLoaded(lap)
        }
    }

    fun deleteStoredLap(meta: LapStore.StoredLapMeta) = LapStore.delete(meta)

    /** Full-rate CSV export of a lap via the share sheet. */
    fun exportCsv(lap: RecordedLap) {
        viewModelScope.launch(Dispatchers.IO) {
            val car = CarCatalog.lookup(lap.carOrdinal)?.name
            runCatching { com.gt7telemetry.logger.LapCsv.share(getApplication(), lap, car) }
        }
    }

    fun setEngineerProvider(p: EngineerProvider) = viewModelScope.launch { settings.setEngineerProvider(p) }
    fun setEngineerApiKey(k: String) = viewModelScope.launch { settings.setEngineerApiKey(k) }
    fun setEngineerModel(m: String) = viewModelScope.launch { settings.setEngineerModel(m) }
    fun setSetupNotes(n: String) = viewModelScope.launch { settings.setSetupNotes(n) }

    fun setPs5Ip(ip: String) = viewModelScope.launch { settings.setPs5Ip(ip) }
    fun setDashMode(mode: DashMode) = viewModelScope.launch { settings.setDashMode(mode) }
    fun setManualLayout(layout: DashLayout) = viewModelScope.launch { settings.setManualLayout(layout.name) }
    fun setUseMph(v: Boolean) = viewModelScope.launch { settings.setUseMph(v) }
    fun setUseFahrenheit(v: Boolean) = viewModelScope.launch { settings.setUseFahrenheit(v) }
    fun setLegacyPacket(v: Boolean) = viewModelScope.launch { settings.setLegacyPacket(v) }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            _updateState.value = UpdateChecker.check(getApplication())
        }
    }

    fun downloadUpdate() {
        val available = _updateState.value as? UpdateState.Available ?: return
        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(0)
            _updateState.value = UpdateChecker.download(getApplication(), available.manifest) { pct ->
                _updateState.value = UpdateState.Downloading(pct)
            }
        }
    }

    /** Dismiss the launch-time "update available" prompt without downloading. */
    fun dismissUpdatePrompt() {
        if (_updateState.value is UpdateState.Available) _updateState.value = UpdateState.Idle
    }
}
