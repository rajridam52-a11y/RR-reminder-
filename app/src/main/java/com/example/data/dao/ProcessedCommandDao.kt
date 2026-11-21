package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ProcessedCommandEntity

@Dao
interface ProcessedCommandDao {

    @Query("SELECT EXISTS(SELECT 1 FROM processed_commands WHERE commandId = :commandId)")
    suspend fun isCommandProcessed(commandId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordProcessedCommand(command: ProcessedCommandEntity)

    @Query("DELETE FROM processed_commands WHERE processedAtMillis < :olderThanMillis")
    suspend fun pruneOldCommands(olderThanMillis: Long)
}
