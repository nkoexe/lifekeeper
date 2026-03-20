package com.lifekeeper.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifekeeper.app.LifekeeperApp
import com.lifekeeper.app.data.preferences.ThemePreference
import com.lifekeeper.app.data.preferences.UserPreferences
import com.lifekeeper.app.data.preferences.UserPreferencesRepository
import com.lifekeeper.app.data.repository.ModeRepository
import com.lifekeeper.app.data.repository.TimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val app: LifekeeperApp,
    private val modeRepo: ModeRepository,
    private val timeRepo: TimeRepository,
    private val prefsRepo: UserPreferencesRepository,
) : ViewModel() {

    // ── Preferences ───────────────────────────────────────────────────────────

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { prefsRepo.setTheme(theme) }
    }

    fun setMinSessionDuration(seconds: Int) {
        viewModelScope.launch { prefsRepo.setMinSessionDuration(seconds) }
    }

    // ── Developer: fill random data ───────────────────────────────────────────

    private val _isFilling = MutableStateFlow(false)
    val isFilling: StateFlow<Boolean> = _isFilling.asStateFlow()

    private val _fillDone = MutableStateFlow(false)
    val fillDone: StateFlow<Boolean> = _fillDone.asStateFlow()

    fun fillWithRandomData() {
        if (_isFilling.value) return
        viewModelScope.launch {
            _isFilling.value = true
            _fillDone.value  = false
            val modeIds = modeRepo.modes.first().map { it.id }
            timeRepo.fillWithRandomData(modeIds)
            app.scheduleWidgetUpdate()
            _isFilling.value = false
            _fillDone.value  = true
        }
    }

    fun clearFillDone() { _fillDone.value = false }

    // ── Data management ───────────────────────────────────────────────────────

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    private val _workMessage = MutableStateFlow<String?>(null)
    val workMessage: StateFlow<String?> = _workMessage.asStateFlow()

    fun clearWorkMessage() { _workMessage.value = null }

    /** Deletes all time entries; modes and settings are preserved. */
    fun deleteTrackedData() {
        if (_isWorking.value) return
        viewModelScope.launch {
            _isWorking.value = true
            timeRepo.deleteAllEntries()
            app.scheduleWidgetUpdate()
            _isWorking.value = false
            _workMessage.value = "Tracking history deleted"
        }
    }

    /** Deletes all modes (cascades to entries) and resets preferences to defaults. */
    fun deleteEverything() {
        if (_isWorking.value) return
        viewModelScope.launch {
            _isWorking.value = true
            modeRepo.deleteAllAndReseed()   // cascade deletes all time_entries too
            prefsRepo.resetAll()
            app.scheduleWidgetUpdate()
            _isWorking.value = false
            _workMessage.value = "All data reset to defaults"
        }
    }

    companion object {
        fun factory(app: LifekeeperApp): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    app,
                    app.modeRepository,
                    app.timeRepository,
                    app.userPreferencesRepository,
                )
            }
        }
    }
}
