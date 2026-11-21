package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.PlannerTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannerTaskDao {

    @Query("SELECT * FROM planner_tasks WHERE dateString = :dateString ORDER BY orderIndex ASC, id ASC")
    fun getTasksForDateFlow(dateString: String): Flow<List<PlannerTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: PlannerTaskEntity): Long

    @Update
    suspend fun updateTask(task: PlannerTaskEntity)

    @Delete
    suspend fun deleteTask(task: PlannerTaskEntity)

    @Query("DELETE FROM planner_tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Long)

    @Query("UPDATE planner_tasks SET isCompleted = :isCompleted WHERE id = :taskId")
    suspend fun setTaskCompleted(taskId: Long, isCompleted: Boolean)
}
