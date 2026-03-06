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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

// ── Public state ──────────────────────────────────────────────────────────────

/**
 * UI state for the infinite-scroll calendar.
 *
 * [entries] covers [windowStartMs]..[windowEndMs] — a rolling window that
 * expands as the user scrolls toward its edges.
 *
 * [allModes] contains every known mode so the filter bar can list them all,
 * even if no entry for a given mode falls inside the current window.
 */
data class DayUiState(
    /** Epoch-ms of the local midnight of today — controls the now-indicator. */
    val todayStartMs  : Long,
    /** Start of the loaded entry window (inclusive), aligned to midnight. */
    val windowStartMs : Long,
    /** End of the loaded entry window (exclusive), aligned to midnight. */
    val windowEndMs   : Long,
    /** All entries in [windowStartMs]..[windowEndMs] after applying [filterModeIds]. */
    val entries       : List<TimeEntry> = emptyList(),
    /** Mode map keyed by id — covers every mode, not just those in [entries]. */
    val modes         : Map<Long, Mode> = emptyMap(),
    /** Flat mode list in insertion order — used to build filter chips. */
    val allModes      : List<Mode> = emptyList(),
    /** Current wall-clock time; refreshed every 30 s normally, every second near planned entries. */
    val nowMs         : Long,
    /** Active filter: empty = show all, otherwise only entries whose modeId is in this set. */
    val filterModeIds : Set<Long> = emptySet(),
)

/** Distinguishes what kind of edit is being undone. */
enum class EditType { RESIZE_TOP, RESIZE_BOTTOM, MOVE, DELETE_ADJ }

/**
 * Snapshot of the calendar state _before_ a resize or move was committed,
 * so the action can be reverted atomically.
 */
data class UndoSnapshot(
    val editType       : EditType,
    /** Human-readable label shown in the undo snackbar. */
    val label          : String,
    /** DB id of the entry owning [prevEndMs]. */
    val entryId        : Long,
    /** DB id of the entry owning [prevStartMs] (not equal to [entryId] only in merged blocks). */
    val startEntryId   : Long,
    val prevStartMs    : Long,
    val prevEndMs      : Long?,   // null when the entry was open at edit-time
    val adjacentId     : Long?  = null,
    val adjPrevStartMs : Long?  = null,
    val adjPrevEndMs   : Long?  = null,
    val adjacent2Id    : Long?  = null,
    val adj2PrevStartMs: Long?  = null,
    val adj2PrevEndMs  : Long?  = null,
    // DELETE_ADJ only: the full deleted entry so it can be re-inserted on undo.
    val deletedEntry   : TimeEntry? = null,
    // Entries auto-merged as a side-effect of the user edit; re-inserted on undo
    // before the main boundary restoration so adjacency is fully reversed.
    val mergedEntries  : List<TimeEntry> = emptyList(),
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class DayViewModel(
    private val modeRepo: ModeRepository,
    private val timeRepo: TimeRepository,
) : ViewModel() {

    private val todayStart: Long = todayMidnight()

    private val _nowMs         = MutableStateFlow(System.currentTimeMillis())
    private val _filterModeIds = MutableStateFlow<Set<Long>>(emptySet())

    // The visible window starts at +/-7 days around today.
    // ensureWindowCovers() grows this range as the user scrolls.
    private val _windowStartMs = MutableStateFlow(todayStart - 7L * DAY_MS)
    private val _windowEndMs   = MutableStateFlow(todayStart + 2L * DAY_MS)

    private val _uiState = MutableStateFlow(
        DayUiState(
            todayStartMs  = todayStart,
            windowStartMs = _windowStartMs.value,
            windowEndMs   = _windowEndMs.value,
            nowMs         = System.currentTimeMillis(),
        ),
    )
    val uiState: StateFlow<DayUiState> = _uiState.asStateFlow()

    private val _pendingUndo = MutableStateFlow<UndoSnapshot?>(null)
    val pendingUndo: StateFlow<UndoSnapshot?> = _pendingUndo.asStateFlow()

    init {
        // ── Clock + Planned-entry scheduler ───────────────────────────────────
        //
        // A single imperative loop — no nested Flows — so DB writes from
        // activatePlannedEntry never feed back into the scheduler and cause the
        // infinite-loop / mode-flip bug.
        //
        // Activation gate: we only consider entries whose startEpochMs falls in
        // the half-open window (lastNowMs, now].  Using a one-shot snapshot with
        // startEpochMs > lastNowMs and then filtering ≤ now means:
        //   • Pre-existing closed history is never touched (startEpochMs ≤ lastNowMs).
        //   • A just-due entry is activated exactly once (after activation its
        //     endEpochMs becomes NULL, so it vanishes from future snapshots).
        //   • No reactive feedback: we never subscribe to a DB-backed Flow here.
        //
        // Clock update is intentionally done AFTER activations, so that when the
        // UI state combiner (below) re-collects with the new nowMs value, the DB
        // already reflects the activated entry — no intermediate flicker.
        viewModelScope.launch {
            var lastNowMs = System.currentTimeMillis()
            while (true) {
                val now = System.currentTimeMillis()

                // Activate any planned entries whose start just crossed into the past.
                val justDue = timeRepo.getPlannedEntriesSnapshot(lastNowMs)
                    .filter { it.startEpochMs <= now }
                for (entry in justDue) {
                    timeRepo.activatePlannedEntry(entry)
                }

                // Advance the clock after DB is settled.
                _nowMs.update { now }
                lastNowMs = now

                // Adaptive tick: 1 s when the next planned entry starts within 2 min.
                val upcoming = timeRepo.getPlannedEntriesSnapshot(now).firstOrNull()
                val msUntilNext = upcoming?.let { it.startEpochMs - now } ?: Long.MAX_VALUE
                val tickMs = if (msUntilNext <= 2 * 60_000L) 1_000L else 30_000L
                delay(tickMs)
            }
        }

        // Re-subscribe whenever the window, filter, or now-time changes.
        viewModelScope.launch {
            combine(
                _windowStartMs,
                _windowEndMs,
                _filterModeIds,
                _nowMs,
            ) { wStart, wEnd, filters, now -> Quad(wStart, wEnd, filters, now) }
                .flatMapLatest { (wStart, wEnd, filters, now) ->
                    combine(
                        timeRepo.getEntriesInRange(wStart, wEnd),
                        modeRepo.modes,
                    ) { entries, modeList ->
                        val modesMap = modeList.associateBy { it.id }
                        val filtered = if (filters.isNotEmpty())
                            entries.filter { it.modeId in filters }
                        else
                            entries
                        DayUiState(
                            todayStartMs  = todayStart,
                            windowStartMs = wStart,
                            windowEndMs   = wEnd,
                            entries       = filtered,
                            modes         = modesMap,
                            allModes      = modeList,
                            nowMs         = now,
                            filterModeIds = filters,
                        )
                    }
                }
                .collect { state -> _uiState.update { state } }
        }
    }

    // ── Window management ─────────────────────────────────────────────────────

    /**
     * Called by the scroll handler when the visible date range changes.
     * Expands the loaded window if [rangeStartMs] or [rangeEndMs] are within
     * 3 days of an edge. The window only ever grows.
     */
    fun ensureWindowCovers(rangeStartMs: Long, rangeEndMs: Long) {
        val margin = 3L * DAY_MS
        val neededStart = rangeStartMs - margin
        val neededEnd   = rangeEndMs   + margin
        if (neededStart < _windowStartMs.value) _windowStartMs.update { minOf(it, neededStart) }
        if (neededEnd   > _windowEndMs.value)   _windowEndMs.update   { maxOf(it, neededEnd)   }
    }

    // ── Filter ────────────────────────────────────────────────────────────────

    /** Toggles [modeId] in the active filter set. Empty set = show all entries. */
    fun toggleFilter(modeId: Long) {
        _filterModeIds.update { if (modeId in it) it - modeId else it + modeId }
    }

    // ── Planning ahead ────────────────────────────────────────────────────────

    /**
     * Adds a completely new planned entry for [modeId] over [startMs]..[endMs].
     * Both times must be in the future; [endMs] must be after [startMs].
     * The scheduler will automatically activate it when the time comes.
     */
    fun addPlannedEntry(modeId: Long, startMs: Long, endMs: Long) {
        viewModelScope.launch {
            timeRepo.addPlannedEntry(modeId, startMs, endMs)
        }
    }

    // ── Entry editing ─────────────────────────────────────────────────────────

    fun resizeEntry(
        entryId        : Long,
        newStartMs     : Long?  = null,
        newEndMs       : Long?  = null,
        adjacentId     : Long?  = null,
        adjacentStartMs: Long?  = null,
        adjacentEndMs  : Long?  = null,
        undoSnapshot   : UndoSnapshot? = null,
    ) {
        viewModelScope.launch {
            val merged = timeRepo.resizeEntry(
                entryId         = entryId,
                newStartMs      = newStartMs,
                newEndMs        = newEndMs,
                adjacentId      = adjacentId,
                adjacentStartMs = adjacentStartMs,
                adjacentEndMs   = adjacentEndMs,
            )
            if (undoSnapshot != null) _pendingUndo.update { undoSnapshot.copy(mergedEntries = merged) }
        }
    }

    fun moveEntry(
        entryId           : Long,
        startEntryId      : Long,
        newStartMs        : Long,
        newEndMs          : Long?,
        prevAdjId         : Long?  = null,
        prevAdjNewEndMs   : Long?  = null,
        nextAdjId         : Long?  = null,
        nextAdjNewStartMs : Long?  = null,
        undoSnapshot      : UndoSnapshot? = null,
    ) {
        viewModelScope.launch {
            val merged = timeRepo.moveEntry(
                entryId           = entryId,
                startEntryId      = startEntryId,
                newStartMs        = newStartMs,
                newEndMs          = newEndMs,
                prevAdjId         = prevAdjId,
                prevAdjNewEndMs   = prevAdjNewEndMs,
                nextAdjId         = nextAdjId,
                nextAdjNewStartMs = nextAdjNewStartMs,
            )
            if (undoSnapshot != null) _pendingUndo.update { undoSnapshot.copy(mergedEntries = merged) }
        }
    }

    fun undo() {
        val snap = _pendingUndo.value ?: return
        _pendingUndo.update { null }
        viewModelScope.launch {
            // Re-insert any entries that were auto-merged as part of the forward operation.
            // They must be restored before boundary timestamps are reset so that the
            // adjacent update queries find valid rows.
            for (entry in snap.mergedEntries) timeRepo.insertEntry(entry)
            when (snap.editType) {
                EditType.RESIZE_TOP -> timeRepo.resizeEntry(
                    entryId         = snap.entryId,
                    newStartMs      = snap.prevStartMs,
                    newEndMs        = null,
                    adjacentId      = snap.adjacentId,
                    adjacentStartMs = null,
                    adjacentEndMs   = snap.adjPrevEndMs,
                )
                EditType.RESIZE_BOTTOM -> timeRepo.resizeEntry(
                    entryId         = snap.entryId,
                    newStartMs      = null,
                    newEndMs        = snap.prevEndMs,
                    adjacentId      = snap.adjacentId,
                    adjacentStartMs = snap.adjPrevStartMs,
                    adjacentEndMs   = null,
                )
                EditType.MOVE -> timeRepo.moveEntry(
                    entryId           = snap.entryId,
                    startEntryId      = snap.startEntryId,
                    newStartMs        = snap.prevStartMs,
                    newEndMs          = snap.prevEndMs,
                    prevAdjId         = snap.adjacentId,
                    prevAdjNewEndMs   = snap.adjPrevEndMs,
                    nextAdjId         = snap.adjacent2Id,
                    nextAdjNewStartMs = snap.adj2PrevStartMs,
                )
                EditType.DELETE_ADJ -> {
                    val deleted = snap.deletedEntry ?: return@launch
                    timeRepo.restoreDeletedAdj(
                        adjEntry           = deleted,
                        primaryId          = snap.entryId,
                        primaryPrevStartMs = snap.prevStartMs,
                        primaryPrevEndMs   = snap.prevEndMs,
                    )
                }
            }
        }
    }

    fun clearUndo() { _pendingUndo.update { null } }

    /**
     * Deletes an adjacent entry and expands the primary entry to absorb its time.
     *
     * [adjId] is the DB id being deleted. [adjIsNext] = true means it’s the next block
     * (primary grows forward); false means it’s the prev block (primary grows backward).
     *
     * The deleted entry’s original data is stashed in the [UndoSnapshot] so the
     * operation can be fully reversed by [undo].
     */
    fun deleteAndAbsorbAdjacent(
        primaryId      : Long,
        primaryStartMs : Long,
        primaryEndMs   : Long?,
        adjId          : Long,
        adjIsNext       : Boolean,
        adjStartMs     : Long,
        adjEndMs       : Long?,
        adjModeId      : Long,
    ) {
        viewModelScope.launch {
            // Re-fetch the full adjacent entry from DB before deleting it so
            // we can restore it exactly on undo.
            val adjEntry = timeRepo.getEntryById(adjId)
                ?: return@launch   // already gone, no-op
            val merged = timeRepo.deleteAndAbsorb(
                primaryId  = primaryId,
                adjId      = adjId,
                adjIsNext  = adjIsNext,
                adjStartMs = adjStartMs,
                adjEndMs   = adjEndMs,
            )
            _pendingUndo.update {
                UndoSnapshot(
                    editType      = EditType.DELETE_ADJ,
                    label         = "Activity deleted",
                    entryId       = primaryId,
                    startEntryId  = primaryId,
                    prevStartMs   = primaryStartMs,
                    prevEndMs     = primaryEndMs,
                    deletedEntry  = adjEntry,
                    mergedEntries = merged,
                )
            }
        }
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        const val DAY_MS = 24L * 60L * 60L * 1_000L

        fun factory(app: LifekeeperApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { DayViewModel(app.modeRepository, app.timeRepository) }
        }
    }
}

// ── Tiny helpers ──────────────────────────────────────────────────────────────

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
private operator fun <A, B, C, D> Quad<A, B, C, D>.component1() = a
private operator fun <A, B, C, D> Quad<A, B, C, D>.component2() = b
private operator fun <A, B, C, D> Quad<A, B, C, D>.component3() = c
private operator fun <A, B, C, D> Quad<A, B, C, D>.component4() = d

/** Returns the epoch-ms of the local midnight for the current system time. */
internal fun todayMidnight(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** Returns the epoch-ms of the local midnight of the day containing [epochMs]. */
internal fun dayMidnight(epochMs: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMs
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
