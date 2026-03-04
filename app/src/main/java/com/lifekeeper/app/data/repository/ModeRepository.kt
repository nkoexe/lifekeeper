package com.lifekeeper.app.data.repository

import com.lifekeeper.app.data.db.dao.ModeDao
import com.lifekeeper.app.data.model.Mode
import kotlinx.coroutines.flow.Flow

class ModeRepository(private val dao: ModeDao) {

    val modes: Flow<List<Mode>> = dao.getAll()

    suspend fun seedDefaultsIfEmpty() {
        if (dao.count() > 0) return
        val defaults = listOf(
            Mode(name = "Work",     colorHex = "#6750A4", sortOrder = 0),
            Mode(name = "Rest",     colorHex = "#B5838D", sortOrder = 1),
            Mode(name = "Exercise", colorHex = "#4CAF50", sortOrder = 2),
            Mode(name = "Sleep",    colorHex = "#1A73E8", sortOrder = 3),
        )
        dao.insertAll(defaults)
    }

    suspend fun addMode(name: String, colorHex: String): Long =
        dao.insert(Mode(name = name, colorHex = colorHex))

    suspend fun deleteMode(mode: Mode) = dao.delete(mode)

    suspend fun updateMode(mode: Mode) = dao.update(mode)

    suspend fun reorderModes(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            dao.updateSortOrder(id, index)
        }
    }
}
