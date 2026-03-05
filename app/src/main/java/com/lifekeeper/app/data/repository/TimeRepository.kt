package com.lifekeeper.app.data.repository

import androidx.room.withTransaction
import com.lifekeeper.app.data.db.LifekeeperDatabase
import com.lifekeeper.app.data.model.TimeEntry
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class TimeRepository(private val db: LifekeeperDatabase) {

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
     */
    private val MIN_ENTRY_DURATION_MS = 60_000L   // 1 minute

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
        db.withTransaction {
            if (active != null) {
                val duration = now - active.startEpochMs
                if (duration < MIN_ENTRY_DURATION_MS) {
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
            dao.insert(TimeEntry(modeId = modeId, startEpochMs = now))
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /** Milliseconds since epoch at the start of today (local time). */
    private fun todayMidnightMs(): Long = dayBoundaryMs(0)
}
