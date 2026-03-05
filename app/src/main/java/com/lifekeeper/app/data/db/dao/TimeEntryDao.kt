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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TimeEntry): Long

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
}
