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
import com.lifekeeper.app.data.model.TimeEntry
import com.lifekeeper.app.data.repository.ModeRepository
import com.lifekeeper.app.data.repository.TimeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    // Incremented every second to keep the active-entry duration live.
    private val _tick = MutableStateFlow(0L)

    /**
     * Current wall-clock time in epoch-ms, refreshed every second while an entry
     * is open. Used by [MiniDayStrip] to keep the now-indicator visually current.
     */
    val nowMs: StateFlow<Long> = _tick
        .map { System.currentTimeMillis() }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = System.currentTimeMillis(),
        )

    /** Maps modeId → total milliseconds tracked today, live-updated while an entry is active. */
    val todayDurationsMs: StateFlow<Map<Long, Long>> = combine(
        timeRepo.getTodayEntries(),
        _tick,
    ) { entries, _ ->
        val now = System.currentTimeMillis()
        entries
            .groupBy { it.modeId }
            .mapValues { (_, modeEntries) ->
                modeEntries.sumOf { entry ->
                    ((entry.endEpochMs ?: now) - entry.startEpochMs).coerceAtLeast(0L)
                }
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap(),
    )

    /**
     * Raw time entries for today — drives the [MiniDayStrip] on the mode screen.
     * Re-uses the same Room subscription path as [todayDurationsMs].
     */
    val todayEntries: StateFlow<List<TimeEntry>> = timeRepo.getTodayEntries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    // Becomes true on the first emission from getActiveEntryFlow(). Until then,
    // activeModeId == null means "DB hasn't emitted yet", not "no active mode".
    // ModeScreen uses this flag to hold the loading spinner until the real value
    // is known, so the list can be positioned correctly on first render.
    var activeModeSeen by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch { modeRepo.seedDefaultsIfEmpty() }

        // Tick every second while an entry is active to keep todayDurationsMs live.
        viewModelScope.launch {
            timeRepo.getActiveEntryFlow().collectLatest { activeEntry ->
                if (activeEntry == null) return@collectLatest
                while (true) {
                    delay(1_000)
                    _tick.update { it + 1 }
                }
            }
        }

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
