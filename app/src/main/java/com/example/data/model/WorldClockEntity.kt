package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Entity(tableName = "world_clocks")
data class WorldClockEntity(
    @PrimaryKey
    val id: String, // TimeZone ID (e.g., "Asia/Kolkata", "America/New_York")
    val cityName: String,
    val countryName: String,
    val timeZoneId: String,
    val orderIndex: Int = 0
) {
    val formattedTime: String
        get() {
            val tz = TimeZone.getTimeZone(timeZoneId)
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault()).apply {
                timeZone = tz
            }
            return sdf.format(Date())
        }

    val formattedDate: String
        get() {
            val tz = TimeZone.getTimeZone(timeZoneId)
            val sdf = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).apply {
                timeZone = tz
            }
            return sdf.format(Date())
        }

    fun getTimeDifferenceFromLocal(): String {
        val localTz = TimeZone.getDefault()
        val targetTz = TimeZone.getTimeZone(timeZoneId)
        val now = System.currentTimeMillis()
        val diffMillis = targetTz.getOffset(now) - localTz.getOffset(now)
        val diffHours = diffMillis / (1000 * 60 * 60.0)

        return when {
            diffHours == 0.0 -> "Same as local"
            diffHours > 0 -> String.format(Locale.getDefault(), "+%.1f hrs ahead", diffHours)
            else -> String.format(Locale.getDefault(), "%.1f hrs behind", diffHours)
        }
    }
}
