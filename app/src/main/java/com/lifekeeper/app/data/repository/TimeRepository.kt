package com.lifekeeper.app.data.repository

import androidx.room.withTransaction
import com.lifekeeper.app.data.db.LifekeeperDatabase
import com.lifekeeper.app.data.model.TimeEntry
import com.lifekeeper.app.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Calendar

class TimeRepository(
    private val db: LifekeeperDatabase,
    private val prefsRepo: UserPreferencesRepository,
) {

    private val dao get() = db.timeEntryDao()

    // ── Today helpers ─────────────────────────────────────────────────────────

    /** Flow of all time entries recorded today (since midnight). */
    fun getTodayEntries(): Flow<List<TimeEntry>> =
        dao.getEntriesSince(todayMidnightMs())

    /** Snapshot (suspend) of all time entries recorded today — used by the widget. */
    suspend fun getTodayEntriesSnapshot(): List<TimeEntry> =
        dao.getEntriesSinceSnapshot(todayMidnightMs())

    /** Returns the currently active entry, or null if none. */
    suspend fun getActiveEntry(): TimeEntry? = dao.getActiveEntry()

    /** Reactive stream of the currently active entry; emits on every change. */
    fun getActiveEntryFlow(): Flow<TimeEntry?> = dao.getActiveEntryFlow()

    // ── Range helpers ─────────────────────────────────────────────────────────

    /**
     * Flow of all entries whose interval overlaps [startMs]..[endMs].
     * An open entry (endEpochMs == null) is included if it started before [endMs].
     */
    fun getEntriesInRange(startMs: Long, endMs: Long): Flow<List<TimeEntry>> =
        dao.getEntriesInRange(startMs, endMs)

    /** One-shot version of [getEntriesInRange]. */
    suspend fun getEntriesInRangeSnapshot(startMs: Long, endMs: Long): List<TimeEntry> =
        dao.getEntriesInRangeSnapshot(startMs, endMs)

    /** Fetches a single entry by id \u2014 used to snapshot adj before delete for undo. */
    suspend fun getEntryById(id: Long): TimeEntry? = dao.getEntryById(id)

    // ── Planned (future) entries ──────────────────────────────────────────────

    /**
     * Flow of all planned entries that start after [nowMs].
     * Used by the auto-switch scheduler to watch for upcoming mode changes.
     */
    fun getPlannedEntriesFlow(nowMs: Long): Flow<List<TimeEntry>> =
        dao.getPlannedEntriesFlow(nowMs)

    /**
     * One-shot snapshot of all planned entries that start after [nowMs].
     * Used by the clock/scheduler loop — avoids reactive feedback from DB writes.
     */
    suspend fun getPlannedEntriesSnapshot(nowMs: Long): List<TimeEntry> =
        dao.getPlannedEntriesSnapshot(nowMs)

    /**
     * Inserts a new planned future entry. Unlike [switchMode], this does NOT
     * close the current active entry — it simply adds a closed block in the future.
     * The auto-switch scheduler will activate it when the time arrives.
     *
     * @param modeId   Mode to plan for.
     * @param startMs  Future start time (must be > now).
     * @param endMs    Future end time (must be > startMs).
     * @return the new entry's DB id.
     */
    suspend fun addPlannedEntry(modeId: Long, startMs: Long, endMs: Long): Long =
        db.withTransaction {
            val id = dao.insert(TimeEntry(modeId = modeId, startEpochMs = startMs, endEpochMs = endMs))
            // Merge with adjacent same-mode entries (e.g. two consecutive planned blocks).
            mergeAdjacentSameMode(
                TimeEntry(id = id, modeId = modeId, startEpochMs = startMs, endEpochMs = endMs)
            )
            id
        }

    /**
     * Activates the planned entry [entryId] whose scheduled start time has arrived.
     *
     * - Closes the current active entry at exactly [entry.startEpochMs] (so history
     *   is gapless and the transition is backdated to the planned time).
     * - Reopens the planned entry (sets endEpochMs = null), making it the new active.
     *
     * All writes are in a single transaction.
     *
     * @param entry The planned entry to activate (should have startEpochMs ≤ now).
     */
    suspend fun activatePlannedEntry(entry: TimeEntry) {
        require(entry.endEpochMs != null) { "activatePlannedEntry called with already-open entry" }
        db.withTransaction {
            val active = dao.getActiveEntry()
            if (active != null && active.id != entry.id) {
                // Close the current active entry at the planned entry's start time.
                dao.closeEntry(active.id, entry.startEpochMs)
            }
            // Reopen the planned entry.
            dao.reopenEntry(entry.id)
            // Merge with a same-mode predecessor if exact adjacency was created
            // (e.g. the active mode was the same as this planned entry's mode —
            // closing active at entry.startEpochMs makes them touch at gap = 0).
            mergeAdjacentSameMode(
                TimeEntry(id = entry.id, modeId = entry.modeId,
                          startEpochMs = entry.startEpochMs, endEpochMs = null)
            )
        }
    }

    // ── Edit past entries ─────────────────────────────────────────────────────

    /**
     * Resizes a past entry's boundaries, optionally adjusting an adjacent entry
     * at the same time so there are no gaps or overlaps.
     *
     * @param entryId         Primary DB row to resize.
     * @param newStartMs      New start for [entryId], or null to leave unchanged.
     * @param newEndMs        New end for [entryId], or null to leave unchanged.
     * @param adjacentId      Neighbouring entry whose boundary must be updated to
     *                        match (i.e. the one that "shares" the dragged handle).
     * @param adjacentStartMs New start for [adjacentId], or null to leave unchanged.
     * @param adjacentEndMs   New end for [adjacentId], or null to leave unchanged.
     */
    suspend fun resizeEntry(
        entryId      : Long,
        newStartMs   : Long?,
        newEndMs     : Long?,
        adjacentId   : Long?       = null,
        adjacentStartMs: Long?     = null,
        adjacentEndMs  : Long?     = null,
    ): List<TimeEntry> {
        return db.withTransaction {
            if (newStartMs != null) dao.updateStartTime(entryId, newStartMs)
            if (newEndMs   != null) dao.updateEndTime(entryId, newEndMs)
            if (adjacentId != null) {
                if (adjacentStartMs != null) dao.updateStartTime(adjacentId, adjacentStartMs)
                if (adjacentEndMs   != null) dao.updateEndTime(adjacentId, adjacentEndMs)
            }
            // After moving a boundary, check whether the updated entry now touches a
            // same-mode neighbour at gap = 0 and merge if so.
            val merged = mutableListOf<TimeEntry>()
            val updatedEntry = dao.getEntryById(entryId)
            if (updatedEntry != null) merged += mergeAdjacentSameMode(updatedEntry)
            // Also check the adjacent entry unless it was already merged away.
            if (adjacentId != null && merged.none { it.id == adjacentId }) {
                val updatedAdj = dao.getEntryById(adjacentId)
                if (updatedAdj != null) merged += mergeAdjacentSameMode(updatedAdj)
            }
            merged
        }
    }

    /**
     * Moves a block by shifting both endpoints, and optionally updates the
     * immediate adjacent entries so the calendar stays gapless.
     *
     * @param prevAdjId          DB id of the entry that owns the prev-block's endMs.
     * @param prevAdjNewEndMs    New endMs for [prevAdjId] (= block's newStartMs).
     * @param nextAdjId          DB id of the entry that owns the next-block's startMs.
     * @param nextAdjNewStartMs  New startMs for [nextAdjId] (= block's newEndMs).
     */
    suspend fun moveEntry(
        entryId           : Long,
        startEntryId      : Long,
        newStartMs        : Long,
        newEndMs          : Long?,
        prevAdjId         : Long? = null,
        prevAdjNewEndMs   : Long? = null,
        nextAdjId         : Long? = null,
        nextAdjNewStartMs : Long? = null,
    ): List<TimeEntry> {
        return db.withTransaction {
            if (startEntryId == entryId) {
                // newEndMs == null means the entry is still open — only shift start.
                if (newEndMs != null) dao.updateBothTimes(entryId, newStartMs, newEndMs)
                else dao.updateStartTime(entryId, newStartMs)
            } else {
                dao.updateStartTime(startEntryId, newStartMs)
                if (newEndMs != null) dao.updateEndTime(entryId, newEndMs)
            }
            if (prevAdjId != null && prevAdjNewEndMs != null)
                dao.updateEndTime(prevAdjId, prevAdjNewEndMs)
            if (nextAdjId != null && nextAdjNewStartMs != null)
                dao.updateStartTime(nextAdjId, nextAdjNewStartMs)
            // After the move, check whether the entry now sits exactly adjacent to
            // a same-mode block at either boundary and merge if so.
            val merged = mutableListOf<TimeEntry>()
            val updatedEntry = dao.getEntryById(entryId)
            if (updatedEntry != null) merged += mergeAdjacentSameMode(updatedEntry)
            merged
        }
    }

    // ── Boundary calculators (all local-time aware) ───────────────────────────

    /**
     * Midnight of today + [offsetDays] in local time (e.g. -1 = yesterday midnight,
     * 0 = today midnight, 1 = tomorrow midnight / today's end).
     */
    fun dayBoundaryMs(offsetDays: Int = 0): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, offsetDays)
        return cal.timeInMillis
    }

    /** Start of the 7-day window ending today (today midnight - 6 days). */
    fun weekWindowStartMs(): Long = dayBoundaryMs(-6)

    /** Start of the 30-day window ending today (today midnight - 29 days). */
    fun monthWindowStartMs(): Long = dayBoundaryMs(-29)

    /** Exclusive end of today (= tomorrow midnight). */
    fun todayEndMs(): Long = dayBoundaryMs(1)

    // ── Switch ────────────────────────────────────────────────────────────────

    /**
     * Minimum duration (ms) for a time entry to be considered meaningful.
     * Entries shorter than this are treated as accidental taps and removed
     * retroactively when the next switch occurs.
     *
     * The threshold is read fresh from [UserPreferencesRepository] on every
     * switch so that changes in Settings take effect immediately without
     * restarting the app.
     *
     * Configurable via Settings → Tracking → Minimum session.
     *
     * TODO: When the day-boundary preference is implemented, [dayBoundaryMs] and
     * [todayMidnightMs] must also read the hour offset from [prefsRepo] so that
     * "today" is correctly shifted for night-shift users.
     */
    private suspend fun minEntryDurationMs(): Long =
        prefsRepo.preferences.first().minSessionDurationSeconds * 1_000L

    /**
     * Switch tracking to [modeId].
     *
     * If the currently active entry is shorter than [MIN_ENTRY_DURATION_MS]
     * it is considered accidental and removed retroactively:
     * - The preceding closed entry (direct predecessor) is extended to [now],
     *   reclaiming the short entry's time for the previous mode.
     * - If the user is switching back to the same mode as the predecessor,
     *   that entry is simply reopened (its endEpochMs is cleared), avoiding
     *   a duplicate entry.
     * - If there is no predecessor (the short entry was the very first one
     *   of the day), it is deleted outright and a fresh entry starts now.
     *
     * All writes are wrapped in a single Room transaction so Flows fire once.
     */
    suspend fun switchMode(modeId: Long) {
        val now    = System.currentTimeMillis()
        val active = dao.getActiveEntry()
        if (active?.modeId == modeId) return
        val minDurationMs = minEntryDurationMs()
        db.withTransaction {
            if (active != null) {
                val duration = now - active.startEpochMs
                if (duration < minDurationMs) {
                    // Short entry — remove it and reclaim its time.
                    dao.deleteEntry(active.id)

                    // Find the direct predecessor (must chain exactly to this entry).
                    val prev = dao.getLastClosedEntry()
                    if (prev != null && prev.endEpochMs == active.startEpochMs) {
                        when (prev.modeId) {
                            modeId -> {
                                // Switching back to the same mode the user was in before
                                // the accidental tap — reopen that entry seamlessly.
                                dao.updateEndTime(prev.id, null)
                                return@withTransaction  // no new insert needed
                            }
                            else -> {
                                // Different mode — extend predecessor to now so it
                                // reclaims the short entry's time.
                                dao.updateEndTime(prev.id, now)
                            }
                        }
                    }
                    // No valid predecessor (first entry of day, or chain broken):
                    // just start fresh with the new mode below.
                } else {
                    dao.closeEntry(active.id, now)
                }
            }
            val newId = dao.insert(TimeEntry(modeId = modeId, startEpochMs = now))
            // Rare edge case: a planned entry of the same mode may end exactly at `now`
            // (e.g. a back-to-back planned block).  Merge them into one open entry.
            mergeAdjacentSameMode(
                TimeEntry(id = newId, modeId = modeId, startEpochMs = now, endEpochMs = null)
            )
        }
    }

    // ── Delete adjacent (absorb) ──────────────────────────────────────────────

    /**
     * Deletes [adjId] and expands [primaryId] to absorb its time span.
     *
     * When [adjIsNext] = true (next-block deleted):
     *   primary.endMs is extended to adjEndMs (absorb forward).
     * When [adjIsNext] = false (prev-block deleted):
     *   primary.startMs is pulled back to adjStartMs (absorb backward).
     *
     * All writes are transactional.
     */
    suspend fun deleteAndAbsorb(
        primaryId  : Long,
        adjId      : Long,
        adjIsNext  : Boolean,
        adjStartMs : Long,
        adjEndMs   : Long?,
    ): List<TimeEntry> {
        return db.withTransaction {
            dao.deleteEntry(adjId)
            if (adjIsNext) {
                // Absorb next: extend primary end to adjEndMs (or keep open if adj was open)
                val newEnd = adjEndMs
                if (newEnd != null) dao.updateEndTime(primaryId, newEnd)
                // if adjEndMs == null (adj was open) the primary becomes the new open entry
            } else {
                // Absorb prev: pull primary start back to adjStartMs
                dao.updateStartTime(primaryId, adjStartMs)
            }
            // After absorbing, the primary may now be exactly adjacent to the entry
            // that was on the far side of the deleted block — merge if same mode.
            val primary = dao.getEntryById(primaryId)
            if (primary != null) mergeAdjacentSameMode(primary) else emptyList()
        }
    }

    /**
     * Restores a deleted adjacent entry and shrinks [primaryId] back to its pre-delete size.
     * Used to undo a [deleteAndAbsorb] operation.
     *
     * @param adjEntry  The original [TimeEntry] to re-insert (with its original id).
     * @param primaryId The primary entry whose boundary must be restored.
     * @param primaryPrevStartMs The primary entry's start before the delete (null = unchanged).
     * @param primaryPrevEndMs   The primary entry's end before the delete (null = unchanged).
     */
    suspend fun restoreDeletedAdj(
        adjEntry          : TimeEntry,
        primaryId         : Long,
        primaryPrevStartMs: Long,
        primaryPrevEndMs  : Long?,
    ) {
        db.withTransaction {
            dao.insert(adjEntry)
            dao.updateStartTime(primaryId, primaryPrevStartMs)
            dao.updateEndTime(primaryId, primaryPrevEndMs)
        }
    }

    // ── Delete all ────────────────────────────────────────────────────────────

    /** Permanently deletes every time entry — used by Settings → Data → Delete tracking history. */
    suspend fun deleteAllEntries() = dao.deleteAllEntries()

    // ── Dev / seed ────────────────────────────────────────────────────────────

    /**
     * Fills the last 30 days with random time entries drawn from [modeIds].
     *
     * - Deletes all existing entries in the window [now-30d .. now].
     * - Generates consecutive, gapless blocks from 30 days ago up to the
     *   current moment.  Each block has a random mode and a random duration
     *   between 10 minutes and 10 hours.
     * - The very last entry is left open (endEpochMs = null) so it becomes
     *   the currently active entry.
     *
     * All writes run in a single transaction.
     */
    suspend fun fillWithRandomData(modeIds: List<Long>) {
        if (modeIds.isEmpty()) return
        val now      = System.currentTimeMillis()
        val windowStart = now - 30L * 24 * 60 * 60 * 1_000
        val rng      = java.util.Random()
        val minMs    = 10L  * 60 * 1_000          // 10 minutes
        val maxMs    = 10L  * 60 * 60 * 1_000     // 10 hours
        db.withTransaction {
            dao.deleteEntriesInRange(windowStart, now)
            var cursor = windowStart
            while (cursor < now) {
                val modeId   = modeIds[rng.nextInt(modeIds.size)]
                val duration = minMs + (rng.nextLong().and(Long.MAX_VALUE) % (maxMs - minMs))
                val end      = (cursor + duration).coerceAtMost(now)
                val isLast   = end >= now
                dao.insert(
                    TimeEntry(
                        modeId       = modeId,
                        startEpochMs = cursor,
                        endEpochMs   = if (isLast) null else end,
                    )
                )
                if (isLast) break
                cursor = end
            }
        }
    }

    /** Re-inserts a previously deleted [TimeEntry] (preserving its original id). Used by undo. */
    suspend fun insertEntry(entry: TimeEntry) { dao.insert(entry) }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Merges [entry] with any same-mode neighbor that is **exactly** adjacent (gap = 0 ms).
     *
     * Algorithm:
     * 1. Look for a predecessor whose `endEpochMs == entry.startEpochMs` and same `modeId`.
     *    If found: extend predecessor to `entry.endEpochMs`, delete `entry`.
     *    The predecessor becomes the working "current" entry.
     * 2. Look for a successor whose `startEpochMs == current.endEpochMs` and same `modeId`.
     *    Only possible when `current` is a closed entry (`endEpochMs != null`).
     *    If found: extend current to successor's end, delete successor.
     *
     * Returns the list of DB rows **deleted** by merging (empty if nothing was merged).
     * Callers can store these in [UndoSnapshot.mergedEntries] for full undo support.
     *
     * **Must be called inside an existing [withTransaction] block.**
     */
    private suspend fun mergeAdjacentSameMode(entry: TimeEntry): List<TimeEntry> {
        val deleted = mutableListOf<TimeEntry>()
        var current = entry

        // 1. Predecessor: a same-mode entry ending exactly where this one starts.
        val prev = dao.findEntryEndingAt(current.modeId, current.startEpochMs)
        if (prev != null) {
            dao.updateEndTime(prev.id, current.endEpochMs)
            dao.deleteEntry(current.id)
            deleted += current
            current = prev.copy(endEpochMs = current.endEpochMs)
        }

        // 2. Successor: a same-mode entry starting exactly where this one ends.
        //    Only closed entries can have a meaningful successor start-time.
        val endMs = current.endEpochMs ?: return deleted
        val next = dao.findEntryStartingAt(current.modeId, endMs)
        if (next != null) {
            dao.updateEndTime(current.id, next.endEpochMs)
            dao.deleteEntry(next.id)
            deleted += next
        }

        return deleted
    }

    /** Milliseconds since epoch at the start of today (local time). */
    private fun todayMidnightMs(): Long = dayBoundaryMs(0)
}
