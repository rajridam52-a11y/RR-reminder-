package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "planner_tasks")
data class PlannerTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskText: String,
    val dateString: String, // "YYYY-MM-DD"
    val isCompleted: Boolean = false,
    val category: String = "General",
    val orderIndex: Int = 0
)
