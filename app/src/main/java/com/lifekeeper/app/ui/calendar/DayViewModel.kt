package com.lifekeeper.app.ui.calendar

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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

// ── Public state ──────────────────────────────────────────────────────────────

/** How many days are shown simultaneously on the calendar grid. */
enum class ViewMode(val visibleDays: Int) {
    SINGLE(1), THREE_DAYS(3), WEEK(7)
}

data class DayUiState(
    /** Epoch-ms of the local midnight that starts the displayed day. */
    val dayStartMs: Long,
    /** All entries whose interval overlaps this day, clamped inside the ViewModel. */
    val entries: List<TimeEntry> = emptyList(),
    /** Sparse map of modeId → Mode for colour/name lookups, always complete for [entries]. */
    val modes: Map<Long, Mode> = emptyMap(),
    /** Current wall-clock time; refreshed every 30 s while an entry is open. */
    val nowMs: Long,
    /** True when today is within the visible range — controls the now-indicator and Today button. */
    val isToday: Boolean = true,
    /** Current view mode — determines how many days are shown side by side. */
    val viewMode: ViewMode = ViewMode.SINGLE,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class DayViewModel(
    private val modeRepo: ModeRepository,
    private val timeRepo: TimeRepository,
) : ViewModel() {

    private val todayStart: Long = todayMidnight()

    private val _dayStartMs = MutableStateFlow(todayStart)
    private val _nowMs      = MutableStateFlow(System.currentTimeMillis())
    private val _viewMode   = MutableStateFlow(ViewMode.SINGLE)

    private val _uiState = MutableStateFlow(
        DayUiState(dayStartMs = todayStart, nowMs = System.currentTimeMillis()),
    )
    val uiState: StateFlow<DayUiState> = _uiState.asStateFlow()

    init {
        // Refresh the now-indicator every 30 s while there is an open entry.
        // collectLatest cancels the inner loop the moment the entry changes.
        viewModelScope.launch {
            timeRepo.getActiveEntryFlow().collectLatest { active ->
                if (active == null) return@collectLatest
                while (true) {
                    delay(30_000)
                    _nowMs.update { System.currentTimeMillis() }
                }
            }
        }

        // Re-subscribe whenever the selected day OR the view mode changes.
        viewModelScope.launch {
            combine(_dayStartMs, _viewMode) { day, mode -> day to mode }
                .flatMapLatest { (dayStart, mode) ->
                    // For WEEK mode snap the anchor to the Monday of the selected day.
                    val anchor   = if (mode == ViewMode.WEEK) weekMondayMs(dayStart) else dayStart
                    val rangeEnd = anchor + mode.visibleDays * DAY_MS
                    combine(
                        timeRepo.getEntriesInRange(anchor, rangeEnd),
                        modeRepo.modes,
                        _nowMs,
                    ) { entries, modes, nowMs ->
                        DayUiState(
                            dayStartMs = anchor,
                            entries    = entries,
                            modes      = modes.associateBy { it.id },
                            nowMs      = nowMs,
                            isToday    = anchor <= todayStart &&
                                         todayStart < anchor + mode.visibleDays * DAY_MS,
                            viewMode   = mode,
                        )
                    }
                }
                .collect { state -> _uiState.update { state } }
        }
    }

    // ── Day navigation ────────────────────────────────────────────────────────

    /** Move backward by one period (1 / 3 / 7 days depending on view mode). */
    fun previousDay() { _dayStartMs.update { it - _viewMode.value.visibleDays * DAY_MS } }
    /** Move forward by one period. */
    fun nextDay()     { _dayStartMs.update { it + _viewMode.value.visibleDays * DAY_MS } }
    /** Jump to today (or the Monday of the current week in WEEK mode). */
    fun goToday() {
        _dayStartMs.update {
            if (_viewMode.value == ViewMode.WEEK) weekMondayMs(todayStart) else todayStart
        }
    }

    /** Called by the pager to sync the ViewModel when the user settles on a page. */
    fun setDayOffset(offset: Int) {
        _dayStartMs.update { todayStart + offset * DAY_MS }
    }

    /** Cycles through SINGLE → THREE_DAYS → WEEK → SINGLE. */
    fun cycleViewMode() {
        _viewMode.update { current ->
            when (current) {
                ViewMode.SINGLE     -> ViewMode.THREE_DAYS
                ViewMode.THREE_DAYS -> ViewMode.WEEK
                ViewMode.WEEK       -> ViewMode.SINGLE
            }
        }
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        /** Milliseconds in a full calendar day. */
        const val DAY_MS = 24L * 60L * 60L * 1_000L

        fun factory(app: LifekeeperApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { DayViewModel(app.modeRepository, app.timeRepository) }
        }
    }
}

// ── Utilities ─────────────────────────────────────────────────────────────────

/** Returns the epoch-ms of the local midnight for the current system time. */
internal fun todayMidnight(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/**
 * Returns the epoch-ms of the Monday midnight of the ISO week that contains [epochMs].
 * Uses Calendar.MONDAY = 2; SUNDAY = 1 in most locales, so the formula
 * `(dow – MONDAY + 7) % 7` gives 0 on Monday and increases toward Sunday.
 */
internal fun weekMondayMs(epochMs: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMs
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val dow      = cal.get(Calendar.DAY_OF_WEEK)
    val daysBack = (dow - Calendar.MONDAY + 7) % 7
    cal.add(Calendar.DAY_OF_YEAR, -daysBack)
    return cal.timeInMillis
}
