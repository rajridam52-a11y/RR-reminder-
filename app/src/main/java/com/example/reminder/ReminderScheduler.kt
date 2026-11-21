package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.MainActivity
import com.example.data.model.ReminderEntity

object ReminderScheduler {

    private const val TAG = "ReminderScheduler"
    const val ACTION_REMINDER_FIRE = "com.example.rrreminder.ACTION_REMINDER_FIRE"
    const val ACTION_REMINDER_COMPLETE = "com.example.rrreminder.ACTION_REMINDER_COMPLETE"
    const val ACTION_REMINDER_SNOOZE = "com.example.rrreminder.ACTION_REMINDER_SNOOZE"

    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
    const val EXTRA_REMINDER_DESC = "extra_reminder_desc"
    const val EXTRA_REMINDER_PRIORITY = "extra_reminder_priority"
    const val EXTRA_REMINDER_CATEGORY = "extra_reminder_category"

    fun scheduleReminder(context: Context, reminder: ReminderEntity) {
        if (reminder.isCompleted || reminder.timeMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Exact alarms permission restricted")
            }
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER_FIRE
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_REMINDER_TITLE, reminder.title)
            putExtra(EXTRA_REMINDER_DESC, reminder.description)
            putExtra(EXTRA_REMINDER_PRIORITY, reminder.priority)
            putExtra(EXTRA_REMINDER_CATEGORY, reminder.category)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (reminder.id + 50000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "reminders")
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            (reminder.id + 50000).toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val clockInfo = AlarmManager.AlarmClockInfo(reminder.timeMillis, showPendingIntent)
            alarmManager.setAlarmClock(clockInfo, pendingIntent)
            Log.d(TAG, "Reminder ${reminder.id} scheduled for ${reminder.timeMillis}")
        } catch (e: Exception) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.timeMillis, pendingIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed scheduling reminder exact alarm", e2)
            }
        }
    }

    fun cancelReminder(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER_FIRE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId + 50000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Reminder $reminderId cancelled")
    }
}
