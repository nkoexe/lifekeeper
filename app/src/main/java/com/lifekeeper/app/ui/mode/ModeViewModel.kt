package com.lifekeeper.app.ui.mode

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifekeeper.app.LifekeeperApp
import com.lifekeeper.app.data.model.Mode
import com.lifekeeper.app.data.repository.ModeRepository
import com.lifekeeper.app.data.repository.TimeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ModeViewModel(
    private val app: LifekeeperApp,
    private val modeRepo: ModeRepository,
    private val timeRepo: TimeRepository,
) : ViewModel() {

    val modes: StateFlow<List<Mode>> = modeRepo.modes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    var activeModeId by mutableStateOf<Long?>(null)
        private set

    // Becomes true on the first emission from getActiveEntryFlow(). Until then,
    // activeModeId == null means "DB hasn't emitted yet", not "no active mode".
    // ModeScreen uses this flag to hold the loading spinner until the real value
    // is known, so the list can be positioned correctly on first render.
    var activeModeSeen by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch { modeRepo.seedDefaultsIfEmpty() }

        // This Flow also picks up widget-tap changes made while the app is open.
        viewModelScope.launch {
            timeRepo.getActiveEntryFlow()
                .map { it?.modeId }
                .collectLatest {
                    activeModeId = it
                    activeModeSeen = true
                }
        }
    }

    fun switchMode(modeId: Long) {
        if (modeId == activeModeId) return
        viewModelScope.launch {
            timeRepo.switchMode(modeId)
            app.scheduleWidgetUpdate()
        }
    }

    companion object {
        fun factory(app: LifekeeperApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { ModeViewModel(app, app.modeRepository, app.timeRepository) }
        }
    }
}
