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
     * Switch tracking to [modeId].
     * Wraps both writes in a real Room transaction via [withTransaction] so
     * Room's InvalidationTracker fires exactly once (no transient null flash)
     * and the connection pool is managed correctly even with active Flows.
     */
    suspend fun switchMode(modeId: Long) {
        val now    = System.currentTimeMillis()
        val active = dao.getActiveEntry()
        if (active?.modeId == modeId) return
        db.withTransaction {
            if (active != null) dao.closeEntry(active.id, now)
            dao.insert(TimeEntry(modeId = modeId, startEpochMs = now))
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /** Milliseconds since epoch at the start of today (local time). */
    private fun todayMidnightMs(): Long = dayBoundaryMs(0)
}
