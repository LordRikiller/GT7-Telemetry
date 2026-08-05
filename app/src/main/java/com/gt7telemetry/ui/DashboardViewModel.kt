package com.gt7telemetry.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gt7telemetry.car.CarCatalog
import com.gt7telemetry.dash.DashLayout
import com.gt7telemetry.settings.DashMode
import com.gt7telemetry.settings.SettingsRepository
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

    /** Bumped whenever the car catalog gains data (bundled load, live refresh). */
    private val _catalogRevision = MutableStateFlow(0)
    val catalogRevision: StateFlow<Int> = _catalogRevision.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    init {
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

    fun setPs5Ip(ip: String) = viewModelScope.launch { settings.setPs5Ip(ip) }
    fun setDashMode(mode: DashMode) = viewModelScope.launch { settings.setDashMode(mode) }
    fun setManualLayout(layout: DashLayout) = viewModelScope.launch { settings.setManualLayout(layout.name) }
    fun setUseMph(v: Boolean) = viewModelScope.launch { settings.setUseMph(v) }
    fun setUseFahrenheit(v: Boolean) = viewModelScope.launch { settings.setUseFahrenheit(v) }

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
