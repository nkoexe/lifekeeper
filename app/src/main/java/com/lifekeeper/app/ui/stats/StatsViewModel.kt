package com.lifekeeper.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifekeeper.app.LifekeeperApp
import com.lifekeeper.app.data.model.DailySummary
import com.lifekeeper.app.data.model.Mode
import com.lifekeeper.app.data.model.ModeSummary
import com.lifekeeper.app.data.model.TimeEntry
import com.lifekeeper.app.data.model.elapsedOverlapMs
import com.lifekeeper.app.data.repository.ModeRepository
import com.lifekeeper.app.data.repository.TimeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ── Range model ───────────────────────────────────────────────────────────────

/**
 * How the stats time window is defined.
 *
 * - [ROLLING]      : a window of N days that always ends at "today".
 * - [SINGLE_DAY]   : exactly one calendar day chosen by the user.
 * - [SINGLE_WEEK]  : the ISO week (Mon–Sun) that contains the chosen day.
 * - [SINGLE_MONTH] : the calendar month that contains the chosen day.
 */
enum class StatsRangeMode(val label: String) {
    ROLLING("Rolling"),
    SINGLE_DAY("Day"),
    SINGLE_WEEK("Week"),
    SINGLE_MONTH("Month"),
}

/** Parallel arrays: ROLLING_DAYS[i] is displayed as ROLLING_LABELS[i]. */
val ROLLING_DAYS   = listOf(1, 7, 30, 90, 180, 365)
val ROLLING_LABELS = listOf("1d", "7d", "30d", "3mo", "6mo", "1yr")

// ── UI state ──────────────────────────────────────────────────────────────────

data class StatsUiState(
    val rangeMode     : StatsRangeMode     = StatsRangeMode.ROLLING,
    /** The active rolling window in days; only relevant when [rangeMode] == ROLLING. */
    val rollingDays   : Int                = 7,
    /**
     * Local-timezone midnight of the day the user picked; used only when
     * [rangeMode] is SINGLE_DAY / SINGLE_WEEK / SINGLE_MONTH.
     */
    val fixedAnchorMs : Long               = 0L,
    /** Human-readable label for the current window (e.g. "Last 7d", "March 2026"). */
    val windowLabel   : String             = "",
    /** Per-mode totals for the window — drives the donut chart. */
    val modeSummaries : List<ModeSummary>  = emptyList(),
    /** Sum of all mode totals across the window. */
    val totalMs       : Long               = 0L,
    /** Per-day breakdown — drives the stacked bar chart. */
    val dailySummaries: List<DailySummary> = emptyList(),
    /** Raw entries — available for the hourly calendar section. */
    val entries       : List<TimeEntry>    = emptyList(),
    /** All known modes (colour look-ups in charts). */
    val modes         : List<Mode>         = emptyList(),
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val modeRepo: ModeRepository,
    private val timeRepo: TimeRepository,
) : ViewModel() {

    // Three independent pieces of range state; combined before each subscription.
    private val _rangeMode   = MutableStateFlow(StatsRangeMode.ROLLING)
    private val _rollingDays = MutableStateFlow(7)
    /** Local midnight of the user-selected anchor day (defaults to today). */
    private val _anchorMs    = MutableStateFlow(timeRepo.dayBoundaryMs(0))

    // Tick that keeps live-active-entry duration updated every second.
    private val _tick = MutableStateFlow(0L)

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    // ── Public actions ────────────────────────────────────────────────────────

    fun setRangeMode(mode: StatsRangeMode) { _rangeMode.update { mode } }
    fun setRollingDays(days: Int)          { _rollingDays.update { days } }

    /**
     * Called with a UTC-midnight millis value from M3 DatePicker.
     * Converts to the local-timezone midnight of the same calendar date.
     */
    fun setFixedAnchor(utcMs: Long) { _anchorMs.update { utcDateToLocalMidnight(utcMs) } }

    /** Current anchor as UTC millis for pre-populating DatePicker. */
    fun anchorAsUtcMs(): Long = localMidnightToUtc(_anchorMs.value)

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        // Keep elapsed totals live for both open and scheduled-active entries.
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                _tick.update { it + 1 }
            }
        }

        // Main pipeline: reacts to ANY range change, re-subscribes to DB.
        viewModelScope.launch {
            combine(_rangeMode, _rollingDays, _anchorMs) { mode, days, anchor ->
                Triple(mode, days, anchor)
            }
                .flatMapLatest { (mode, days, anchor) ->
                    val (startMs, endMs) = windowBounds(mode, days, anchor)
                    combine(
                        timeRepo.getEntriesInRange(startMs, endMs),
                        modeRepo.modes,
                        _tick,
                    ) { entries, modes, _ ->
                        compute(mode, days, anchor, modes, entries, startMs, endMs)
                    }
                }
                .collect { state -> _uiState.update { state } }
        }
    }

    // ── Private: window bounds ────────────────────────────────────────────────

    private fun windowBounds(
        mode  : StatsRangeMode,
        days  : Int,
        anchor: Long,
    ): Pair<Long, Long> = when (mode) {
        StatsRangeMode.ROLLING -> {
            // Rolling: window ends at tomorrow midnight (exclusive), starts N days ago.
            timeRepo.dayBoundaryMs(-(days - 1)) to timeRepo.todayEndMs()
        }
        StatsRangeMode.SINGLE_DAY -> {
            val start = localMidnight(anchor)
            start to (start + DAY_MS)
        }
        StatsRangeMode.SINGLE_WEEK -> {
            val mon = weekMondayMs(anchor)
            mon to (mon + 7 * DAY_MS)
        }
        StatsRangeMode.SINGLE_MONTH -> monthBounds(anchor)
    }

    // ── Private: computation ──────────────────────────────────────────────────

    private fun compute(
        mode       : StatsRangeMode,
        rollingDays: Int,
        anchor     : Long,
        modes      : List<Mode>,
        entries    : List<TimeEntry>,
        windowStart: Long,
        windowEnd  : Long,
    ): StatsUiState {
        val now     = System.currentTimeMillis()
        val modeMap = modes.associateBy { it.id }

        // Per-mode totals.
        val modeTotals = mutableMapOf<Long, Long>()
        for (entry in entries) {
            val ms = entry.elapsedOverlapMs(windowStart, windowEnd, now)
            if (ms > 0L) modeTotals[entry.modeId] = (modeTotals[entry.modeId] ?: 0L) + ms
        }
        val modeSummaries = modeTotals.mapNotNull { (id, ms) ->
            val m = modeMap[id] ?: return@mapNotNull null
            ModeSummary(id, m.name, m.colorHex, ms)
        }.sortedByDescending { it.totalMs }

        // Per-day breakdown.
        val dayList = buildDayList(mode, rollingDays, anchor, windowStart)
        val dailySummaries = dayList.map { dayStart ->
            val dayEnd       = dayStart + DAY_MS
            val dayDurations = mutableMapOf<Long, Long>()
            for (entry in entries) {
                val ms = entry.elapsedOverlapMs(dayStart, dayEnd, now)
                if (ms > 0L) dayDurations[entry.modeId] = (dayDurations[entry.modeId] ?: 0L) + ms
            }
            DailySummary(dayStart, dayDurations)
        }

        return StatsUiState(
            rangeMode      = mode,
            rollingDays    = rollingDays,
            fixedAnchorMs  = anchor,
            windowLabel    = buildWindowLabel(mode, rollingDays, anchor, windowStart, windowEnd),
            modeSummaries  = modeSummaries,
            totalMs        = modeTotals.values.sum(),
            dailySummaries = dailySummaries,
            entries        = entries,
            modes          = modes,
        )
    }

    private fun buildDayList(
        mode       : StatsRangeMode,
        rollingDays: Int,
        anchor     : Long,
        windowStart: Long,
    ): List<Long> {
        val dayCount = when (mode) {
            StatsRangeMode.ROLLING     -> rollingDays
            StatsRangeMode.SINGLE_DAY  -> 1
            StatsRangeMode.SINGLE_WEEK -> 7
            StatsRangeMode.SINGLE_MONTH -> {
                val cal = Calendar.getInstance().apply { timeInMillis = anchor }
                cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            }
        }
        val cal = Calendar.getInstance().apply { timeInMillis = windowStart }
        return List(dayCount) {
            val ms = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            ms
        }
    }

    private fun buildWindowLabel(
        mode       : StatsRangeMode,
        rollingDays: Int,
        anchor     : Long,
        windowStart: Long,
        windowEnd  : Long,
    ): String {
        val short = SimpleDateFormat("d MMM", Locale.getDefault())
        return when (mode) {
            StatsRangeMode.ROLLING -> {
                val idx = ROLLING_DAYS.indexOf(rollingDays)
                "Last ${if (idx >= 0) ROLLING_LABELS[idx] else "${rollingDays}d"}"
            }
            StatsRangeMode.SINGLE_DAY ->
                SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(anchor))
            StatsRangeMode.SINGLE_WEEK ->
                "${short.format(Date(windowStart))} – ${short.format(Date(windowEnd - 1))}"
            StatsRangeMode.SINGLE_MONTH ->
                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(anchor))
        }
    }

    // ── Private: date utilities ───────────────────────────────────────────────

    /** Local midnight of the day that contains [epochMs]. */
    private fun localMidnight(epochMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0);      cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Monday midnight of the ISO week that contains [epochMs]. */
    private fun weekMondayMs(epochMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0);      cal.set(Calendar.MILLISECOND, 0)
        val dow      = cal.get(Calendar.DAY_OF_WEEK)
        val daysBack = (dow - Calendar.MONDAY + 7) % 7
        cal.add(Calendar.DAY_OF_YEAR, -daysBack)
        return cal.timeInMillis
    }

    /** Start and end (exclusive) of the calendar month containing [epochMs]. */
    private fun monthBounds(epochMs: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0);      cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return start to cal.timeInMillis
    }

    /**
     * M3 DatePicker emits UTC-midnight millis for the chosen date.
     * Converts to the local-timezone midnight of that same calendar date,
     * which is what all DB boundary calculations expect.
     */
    private fun utcDateToLocalMidnight(utcMs: Long): Long {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMs
        }
        val localCal = Calendar.getInstance()
        localCal.set(
            utcCal.get(Calendar.YEAR),
            utcCal.get(Calendar.MONTH),
            utcCal.get(Calendar.DAY_OF_MONTH),
            0, 0, 0,
        )
        localCal.set(Calendar.MILLISECOND, 0)
        return localCal.timeInMillis
    }

    /**
     * Converts a local-timezone midnight back to UTC midnight of the same
     * calendar date, for pre-populating M3 DatePicker.
     */
    private fun localMidnightToUtc(localMs: Long): Long {
        val localCal = Calendar.getInstance().apply { timeInMillis = localMs }
        val utcCal   = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utcCal.set(
            localCal.get(Calendar.YEAR),
            localCal.get(Calendar.MONTH),
            localCal.get(Calendar.DAY_OF_MONTH),
            0, 0, 0,
        )
        utcCal.set(Calendar.MILLISECOND, 0)
        return utcCal.timeInMillis
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        const val DAY_MS = 24L * 60L * 60L * 1_000L

        fun factory(app: LifekeeperApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { StatsViewModel(app.modeRepository, app.timeRepository) }
        }
    }
}
