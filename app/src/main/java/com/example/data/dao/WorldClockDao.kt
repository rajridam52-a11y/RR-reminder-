package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.WorldClockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldClockDao {

    @Query("SELECT * FROM world_clocks ORDER BY orderIndex ASC")
    fun getAllClocksFlow(): Flow<List<WorldClockEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClock(clock: WorldClockEntity)

    @Delete
    suspend fun deleteClock(clock: WorldClockEntity)

    @Query("DELETE FROM world_clocks WHERE id = :clockId")
    suspend fun deleteClockById(clockId: String)

    @Query("SELECT COUNT(*) FROM world_clocks")
    suspend fun getClockCount(): Int
}
