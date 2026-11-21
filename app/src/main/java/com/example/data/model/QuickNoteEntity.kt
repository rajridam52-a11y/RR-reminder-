package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quick_notes")
data class QuickNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val colorTag: String = "Default",
    val isPinned: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
