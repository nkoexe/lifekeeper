package com.lifekeeper.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lifekeeper.app.data.db.dao.ModeDao
import com.lifekeeper.app.data.db.dao.TimeEntryDao
import com.lifekeeper.app.data.model.Mode
import com.lifekeeper.app.data.model.TimeEntry

@Database(
    entities = [Mode::class, TimeEntry::class],
    version = 1,
    exportSchema = false
)
abstract class LifekeeperDatabase : RoomDatabase() {

    abstract fun modeDao(): ModeDao
    abstract fun timeEntryDao(): TimeEntryDao

    companion object {
        @Volatile
        private var INSTANCE: LifekeeperDatabase? = null

        fun getInstance(context: Context): LifekeeperDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LifekeeperDatabase::class.java,
                    "lifekeeper.db"
                ).build().also { INSTANCE = it }
            }
    }
}
