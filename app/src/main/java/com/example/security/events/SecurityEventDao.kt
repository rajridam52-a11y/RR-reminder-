package com.example.security.events

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityEventDao {

    @Query("SELECT * FROM security_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEventsFlow(limit: Int = 100): Flow<List<SecurityEventEntity>>

    @Query("SELECT * FROM security_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int = 50): List<SecurityEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: SecurityEventEntity)

    @Query("SELECT COUNT(*) FROM security_events")
    suspend fun getEventCount(): Int

    @Query("DELETE FROM security_events WHERE eventId NOT IN (SELECT eventId FROM security_events ORDER BY timestamp DESC LIMIT 500)")
    suspend fun pruneOldEvents()
}
