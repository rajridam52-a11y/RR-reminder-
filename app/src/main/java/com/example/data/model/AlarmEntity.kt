package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar
import java.util.Locale

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "Alarm",
    val isEnabled: Boolean = true,
    val repeatDaysMask: Int = 0, // Bitmask: bit 1 = Mon (2^1 = 2), bit 2 = Tue (4), ... bit 7 = Sun (128)
    val isVibrate: Boolean = true,
    val soundTitle: String = "Radar Beep",
    val soundUri: String? = null,
    val snoozeMinutes: Int = 5,
    val isGradualVolume: Boolean = false,
    val isMultiStage: Boolean = false,
    val stageMinutesBefore: Int = 10,
    val toneCategory: String = "Alert"
) {
    val formattedTime: String
        get() {
            val period = if (hour >= 12) "PM" else "AM"
            val displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            return String.format(Locale.getDefault(), "%02d:%02d %s", displayHour, minute, period)
        }

    val isOneTime: Boolean
        get() = repeatDaysMask == 0

    val isWeekdays: Boolean
        get() = repeatDaysMask == 62 // Mon(2) + Tue(4) + Wed(8) + Thu(16) + Fri(32) = 62

    val isWeekends: Boolean
        get() = repeatDaysMask == 192 // Sat(64) + Sun(128) = 192

    val isEveryday: Boolean
        get() = repeatDaysMask == 254 // Mon to Sun = 254

    val repeatDaysSummary: String
        get() = when {
            isOneTime -> "Once"
            isEveryday -> "Every day"
            isWeekdays -> "Mon - Fri"
            isWeekends -> "Weekends"
            else -> {
                val days = mutableListOf<String>()
                if ((repeatDaysMask and 2) != 0) days.add("Mon")
                if ((repeatDaysMask and 4) != 0) days.add("Tue")
                if ((repeatDaysMask and 8) != 0) days.add("Wed")
                if ((repeatDaysMask and 16) != 0) days.add("Thu")
                if ((repeatDaysMask and 32) != 0) days.add("Fri")
                if ((repeatDaysMask and 64) != 0) days.add("Sat")
                if ((repeatDaysMask and 128) != 0) days.add("Sun")
                days.joinToString(", ")
            }
        }

    fun calculateNextTriggerMillis(): Long {
        val now = Calendar.getInstance()
        val alarm = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (isOneTime) {
            if (alarm.before(now) || alarm.timeInMillis <= now.timeInMillis) {
                alarm.add(Calendar.DAY_OF_YEAR, 1)
            }
            return alarm.timeInMillis
        }

        // Repeating alarm: Find next matching day
        for (i in 0..7) {
            val checkCal = Calendar.getInstance().apply {
                timeInMillis = alarm.timeInMillis + (i * 86400000L)
            }
            val dayOfWeek = checkCal.get(Calendar.DAY_OF_WEEK)
            // Convert Calendar.DAY_OF_WEEK (Sun=1, Mon=2, ... Sat=7) to bitmask
            val maskBit = when (dayOfWeek) {
                Calendar.MONDAY -> 2
                Calendar.TUESDAY -> 4
                Calendar.WEDNESDAY -> 8
                Calendar.THURSDAY -> 16
                Calendar.FRIDAY -> 32
                Calendar.SATURDAY -> 64
                Calendar.SUNDAY -> 128
                else -> 0
            }

            if ((repeatDaysMask and maskBit) != 0) {
                if (checkCal.after(now)) {
                    return checkCal.timeInMillis
                }
            }
        }
        return alarm.timeInMillis + 86400000L
    }
}
