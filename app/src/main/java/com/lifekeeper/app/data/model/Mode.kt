package com.lifekeeper.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "modes")
data class Mode(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val colorHex: String,   // e.g. "#6750A4"
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
