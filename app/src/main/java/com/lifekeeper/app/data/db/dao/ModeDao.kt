package com.lifekeeper.app.data.db.dao

import androidx.room.*
import com.lifekeeper.app.data.model.Mode
import kotlinx.coroutines.flow.Flow

@Dao
interface ModeDao {

    @Query("SELECT * FROM modes ORDER BY sortOrder ASC, createdAt ASC")
    fun getAll(): Flow<List<Mode>>

    @Query("SELECT COUNT(*) FROM modes")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(mode: Mode): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(modes: List<Mode>)

    @Update
    suspend fun update(mode: Mode)

    @Delete
    suspend fun delete(mode: Mode)

    @Query("UPDATE modes SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)
}
