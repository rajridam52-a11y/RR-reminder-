package com.example.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.RRApplication
import com.example.data.model.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val NOTIFICATION_ID_BASE = 50000
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        Log.d(TAG, "onReceive with action: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val activeReminders = RRApplication.instance.database.reminderDao().getActiveRemindersFlow().firstOrNull() ?: emptyList()
                        for (rem in activeReminders) {
                            ReminderScheduler.scheduleReminder(context, rem)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed rescheduling reminders on boot", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ReminderScheduler.ACTION_REMINDER_FIRE -> {
                val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
                val title = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TITLE) ?: "Reminder"
                val desc = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_DESC) ?: ""
                val priority = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_PRIORITY) ?: "Medium"
                val category = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_CATEGORY) ?: "General"

                showReminderNotification(context, reminderId, title, desc, priority, category)
            }

            ReminderScheduler.ACTION_REMINDER_COMPLETE -> {
                val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel((NOTIFICATION_ID_BASE + reminderId).toInt())

                if (reminderId > 0) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            RRApplication.instance.database.reminderDao().setReminderCompleted(reminderId, true)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed marking reminder complete", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }

            ReminderScheduler.ACTION_REMINDER_SNOOZE -> {
                val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
                val title = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TITLE) ?: "Reminder"
                val desc = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_DESC) ?: ""
                val priority = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_PRIORITY) ?: "Medium"
                val category = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_CATEGORY) ?: "General"

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel((NOTIFICATION_ID_BASE + reminderId).toInt())

                // Reschedule for 10 minutes later
                val snoozedReminder = ReminderEntity(
                    id = reminderId,
                    title = title,
                    description = desc,
                    timeMillis = System.currentTimeMillis() + 600_000L,
                    isCompleted = false,
                    priority = priority,
                    category = category
                )
                ReminderScheduler.scheduleReminder(context, snoozedReminder)
            }
        }
    }

    private fun showReminderNotification(
        context: Context,
        reminderId: Long,
        title: String,
        desc: String,
        priority: String,
        category: String
    ) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "reminders")
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            (NOTIFICATION_ID_BASE + reminderId).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderScheduler.ACTION_REMINDER_COMPLETE
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            (NOTIFICATION_ID_BASE + reminderId + 1000).toInt(),
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderScheduler.ACTION_REMINDER_SNOOZE
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderScheduler.EXTRA_REMINDER_TITLE, title)
            putExtra(ReminderScheduler.EXTRA_REMINDER_DESC, desc)
            putExtra(ReminderScheduler.EXTRA_REMINDER_PRIORITY, priority)
            putExtra(ReminderScheduler.EXTRA_REMINDER_CATEGORY, category)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (NOTIFICATION_ID_BASE + reminderId + 2000).toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 350, 150, 350), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 350, 150, 350), -1)
            }
        } catch (_: Exception) {}

        val notifBuilder = NotificationCompat.Builder(context, RRApplication.CHANNEL_ALARM_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("[$category] $title")
            .setContentText(desc.ifBlank { "You have a scheduled reminder." })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .addAction(0, "✓ Done", completePendingIntent)
            .addAction(0, "⏰ Snooze 10m", snoozePendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify((NOTIFICATION_ID_BASE + reminderId).toInt(), notifBuilder.build())
    }
}
