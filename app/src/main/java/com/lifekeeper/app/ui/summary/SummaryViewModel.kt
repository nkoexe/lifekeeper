package com.lifekeeper.app.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifekeeper.app.LifekeeperApp
import com.lifekeeper.app.data.model.ModeSummary
import com.lifekeeper.app.data.repository.ModeRepository
import com.lifekeeper.app.data.repository.TimeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SummaryViewModel(
    private val modeRepo: ModeRepository,
    private val timeRepo: TimeRepository,
) : ViewModel() {

    private val _summaries = MutableStateFlow<List<ModeSummary>>(emptyList())
    val summaries: StateFlow<List<ModeSummary>> = _summaries.asStateFlow()

    private val _totalMs = MutableStateFlow(0L)
    val totalMs: StateFlow<Long> = _totalMs.asStateFlow()

    // Incremented every second to keep the active-entry duration live.
    private val _tick = MutableStateFlow(0L)

    init {
        // Tick every 30 s while there is an open (active) entry to keep durations
        // live. 30 s is sufficient since nothing in the UI shows seconds anymore.
        // collectLatest cancels the inner while-loop as soon as the active entry
        // changes, so we never waste CPU ticking when everything is idle.
        viewModelScope.launch {
            timeRepo.getActiveEntryFlow().collectLatest { activeEntry ->
                if (activeEntry == null) return@collectLatest
                while (true) {
                    delay(30_000)
                    _tick.update { it + 1 }
                }
            }
        }

        // Recompute summaries whenever entries, modes, or the tick changes.
        viewModelScope.launch {
            combine(
                timeRepo.getTodayEntries(),
                modeRepo.modes,
                _tick,
            ) { entries, modes, _ ->
                val modeMap = modes.associateBy { it.id }
                val now = System.currentTimeMillis()
                val grouped = entries.groupBy { it.modeId }
                grouped.mapNotNull { (modeId, modeEntries) ->
                    val mode = modeMap[modeId] ?: return@mapNotNull null
                    val total = modeEntries.sumOf { entry ->
                        ((entry.endEpochMs ?: now) - entry.startEpochMs).coerceAtLeast(0)
                    }
                    ModeSummary(
                        modeId   = modeId,
                        modeName = mode.name,
                        colorHex = mode.colorHex,
                        totalMs  = total,
                    )
                }.sortedByDescending { it.totalMs }
            }.collect { computed ->
                _summaries.update { computed }
                _totalMs.update { computed.sumOf { it.totalMs } }
            }
        }
    }

    companion object {
        fun factory(app: LifekeeperApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { SummaryViewModel(app.modeRepository, app.timeRepository) }
        }
    }
}
