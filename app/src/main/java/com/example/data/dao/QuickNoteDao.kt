package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.QuickNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickNoteDao {

    @Query("SELECT * FROM quick_notes ORDER BY isPinned DESC, updatedAtMillis DESC")
    fun getAllNotesFlow(): Flow<List<QuickNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: QuickNoteEntity): Long

    @Update
    suspend fun updateNote(note: QuickNoteEntity)

    @Delete
    suspend fun deleteNote(note: QuickNoteEntity)

    @Query("DELETE FROM quick_notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: Long)
}
