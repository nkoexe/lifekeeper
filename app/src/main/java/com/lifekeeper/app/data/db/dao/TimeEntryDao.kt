package com.lifekeeper.app.data.db.dao

import androidx.room.*
import com.lifekeeper.app.data.model.TimeEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeEntryDao {

    @Query("SELECT * FROM time_entries WHERE endEpochMs IS NULL LIMIT 1")
    suspend fun getActiveEntry(): TimeEntry?

    /** Reactive version — Room emits whenever the table changes. */
    @Query("SELECT * FROM time_entries WHERE endEpochMs IS NULL LIMIT 1")
    fun getActiveEntryFlow(): Flow<TimeEntry?>

    @Query("UPDATE time_entries SET endEpochMs = :endMs WHERE id = :id")
    suspend fun closeEntry(id: Long, endMs: Long)

    /**
     * Sets endEpochMs to an arbitrary value (including null to reopen an entry).
     * Used during short-entry cleanup in [TimeRepository.switchMode].
     */
    @Query("UPDATE time_entries SET endEpochMs = :endMs WHERE id = :id")
    suspend fun updateEndTime(id: Long, endMs: Long?)

    /** Updates only the start time — used when dragging the top resize handle. */
    @Query("UPDATE time_entries SET startEpochMs = :startMs WHERE id = :id")
    suspend fun updateStartTime(id: Long, startMs: Long)

    /** Updates both times atomically — used when moving a block (shift only). */
    @Query("UPDATE time_entries SET startEpochMs = :startMs, endEpochMs = :endMs WHERE id = :id")
    suspend fun updateBothTimes(id: Long, startMs: Long, endMs: Long)

    /** Deletes a single entry by id — used to remove entries that are below the minimum duration. */
    @Query("DELETE FROM time_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    /**
     * Returns the most recent closed entry (endEpochMs IS NOT NULL), or null if none exists.
     * Used to find the direct predecessor of a short entry so its time can be reclaimed.
     */
    @Query("SELECT * FROM time_entries WHERE endEpochMs IS NOT NULL ORDER BY startEpochMs DESC LIMIT 1")
    suspend fun getLastClosedEntry(): TimeEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TimeEntry): Long

    /** Fetches a single entry by id — used during undo-delete restoration. */
    @Query("SELECT * FROM time_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): TimeEntry?

    @Query("SELECT * FROM time_entries WHERE startEpochMs >= :startMs ORDER BY startEpochMs ASC")
    fun getEntriesSince(startMs: Long): Flow<List<TimeEntry>>

    @Query("SELECT * FROM time_entries WHERE startEpochMs >= :startMs ORDER BY startEpochMs ASC")
    suspend fun getEntriesSinceSnapshot(startMs: Long): List<TimeEntry>

    /**
     * Returns all entries whose interval overlaps the [startMs]..[endMs] window.
     * An entry overlaps when its start is before the window closes AND its end
     * (or "now" if still open) is after the window opens.
     */
    @Query("""
        SELECT * FROM time_entries
        WHERE startEpochMs < :endMs
          AND (endEpochMs IS NULL OR endEpochMs > :startMs)
        ORDER BY startEpochMs ASC
    """)
    fun getEntriesInRange(startMs: Long, endMs: Long): Flow<List<TimeEntry>>

    /** Suspend snapshot version of [getEntriesInRange] — used in one-shot contexts. */
    @Query("""
        SELECT * FROM time_entries
        WHERE startEpochMs < :endMs
          AND (endEpochMs IS NULL OR endEpochMs > :startMs)
        ORDER BY startEpochMs ASC
    """)
    suspend fun getEntriesInRangeSnapshot(startMs: Long, endMs: Long): List<TimeEntry>

    /**
     * Returns all planned (future) entries: both start and end are in the future
     * (startEpochMs > [nowMs]). These are entries the user has scheduled ahead.
     */
    @Query("""
        SELECT * FROM time_entries
        WHERE startEpochMs > :nowMs
          AND endEpochMs IS NOT NULL
        ORDER BY startEpochMs ASC
    """)
    fun getPlannedEntriesFlow(nowMs: Long): Flow<List<TimeEntry>>

    /** One-shot snapshot of planned entries — used by the scheduler. */
    @Query("""
        SELECT * FROM time_entries
        WHERE startEpochMs > :nowMs
          AND endEpochMs IS NOT NULL
        ORDER BY startEpochMs ASC
    """)
    suspend fun getPlannedEntriesSnapshot(nowMs: Long): List<TimeEntry>

    /** Clears endEpochMs (reopens) a planned entry, making it the active one. */
    @Query("UPDATE time_entries SET endEpochMs = NULL WHERE id = :id")
    suspend fun reopenEntry(id: Long)

    /** Deletes all entries in [startMs]..[endMs]. */
    @Query("DELETE FROM time_entries WHERE startEpochMs >= :startMs AND startEpochMs <= :endMs")
    suspend fun deleteEntriesInRange(startMs: Long, endMs: Long)

    /** Permanently deletes every time entry. Used for Settings → Delete tracking history. */
    @Query("DELETE FROM time_entries")
    suspend fun deleteAllEntries()

    /**
     * Finds the entry of [modeId] whose endEpochMs is exactly [endMs].
     * Used to detect exact same-mode predecessors during DB-level adjacency merging.
     */
    @Query("SELECT * FROM time_entries WHERE modeId = :modeId AND endEpochMs = :endMs LIMIT 1")
    suspend fun findEntryEndingAt(modeId: Long, endMs: Long): TimeEntry?

    /**
     * Finds an entry of [modeId] whose startEpochMs is exactly [startMs].
     * Used to detect exact same-mode successors during DB-level adjacency merging.
     */
    @Query("SELECT * FROM time_entries WHERE modeId = :modeId AND startEpochMs = :startMs LIMIT 1")
    suspend fun findEntryStartingAt(modeId: Long, startMs: Long): TimeEntry?
}
