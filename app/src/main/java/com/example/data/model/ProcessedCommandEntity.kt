package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SQLite Entity for deduplicating inbound remote commands.
 * Guarantees idempotency and anti-replay defense.
 */
@Entity(tableName = "processed_commands")
data class ProcessedCommandEntity(
    @PrimaryKey
    val commandId: String,
    val commandType: String,
    val processedAtMillis: Long = System.currentTimeMillis(),
    val status: String // "APPLIED", "REJECTED_EXPIRED", "REJECTED_INVALID"
)
