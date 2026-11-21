package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY timeMillis ASC")
    fun getAllRemindersFlow(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY timeMillis ASC")
    fun getActiveRemindersFlow(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 1 ORDER BY timeMillis DESC")
    fun getCompletedRemindersFlow(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE category = :category ORDER BY timeMillis ASC")
    fun getRemindersByCategoryFlow(category: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :reminderId LIMIT 1")
    suspend fun getReminderById(reminderId: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :reminderId")
    suspend fun deleteReminderById(reminderId: Long)

    @Query("UPDATE reminders SET isCompleted = :isCompleted WHERE id = :reminderId")
    suspend fun setReminderCompleted(reminderId: Long, isCompleted: Boolean)
}
