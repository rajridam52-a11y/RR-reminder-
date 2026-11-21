package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val timeMillis: Long,
    val isCompleted: Boolean = false,
    val priority: String = "Medium", // Low, Medium, High, Urgent
    val category: String = "General", // Work, Personal, Health, Finance, General
    val repeatInterval: String = "None", // None, Daily, Weekly, Monthly
    val hasAlarmAlert: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    val formattedDateTime: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.getDefault())
            return sdf.format(Date(timeMillis))
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("EEE, MMM dd", Locale.getDefault())
            return sdf.format(Date(timeMillis))
        }

    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            return sdf.format(Date(timeMillis))
        }

    val isOverdue: Boolean
        get() = !isCompleted && timeMillis < System.currentTimeMillis()
}
