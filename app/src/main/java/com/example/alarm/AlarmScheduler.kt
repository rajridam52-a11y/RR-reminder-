package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.MainActivity
import com.example.data.model.AlarmEntity

object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    const val ACTION_ALARM_FIRE = "com.example.rrreminder.ACTION_ALARM_FIRE"
    const val ACTION_ALARM_SNOOZE = "com.example.rrreminder.ACTION_ALARM_SNOOZE"
    const val ACTION_ALARM_DISMISS = "com.example.rrreminder.ACTION_ALARM_DISMISS"

    const val EXTRA_ALARM_ID = "extra_alarm_id"
    const val EXTRA_ALARM_LABEL = "extra_alarm_label"
    const val EXTRA_ALARM_HOUR = "extra_alarm_hour"
    const val EXTRA_ALARM_MINUTE = "extra_alarm_minute"
    const val EXTRA_ALARM_VIBRATE = "extra_alarm_vibrate"
    const val EXTRA_ALARM_SOUND = "extra_alarm_sound"
    const val EXTRA_ALARM_SNOOZE_MINS = "extra_alarm_snooze_mins"

    fun scheduleAlarm(context: Context, alarm: AlarmEntity) {
        if (!alarm.isEnabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Cannot schedule exact alarms; permission missing")
            }
        }

        val triggerTime = alarm.calculateNextTriggerMillis()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRE
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_LABEL, alarm.label)
            putExtra(EXTRA_ALARM_HOUR, alarm.hour)
            putExtra(EXTRA_ALARM_MINUTE, alarm.minute)
            putExtra(EXTRA_ALARM_VIBRATE, alarm.isVibrate)
            putExtra(EXTRA_ALARM_SOUND, alarm.soundTitle)
            putExtra(EXTRA_ALARM_SNOOZE_MINS, alarm.snoozeMinutes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
        try {
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Alarm ${alarm.id} scheduled for $triggerTime")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while scheduling alarm clock", e)
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed fallback exact alarm", e2)
            }
        }
    }

    fun cancelAlarm(context: Context, alarmId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Alarm $alarmId cancelled")
    }

    fun scheduleSnooze(context: Context, alarmId: Long, label: String, snoozeMinutes: Int, isVibrate: Boolean, soundTitle: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRE
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, "$label (Snoozed)")
            putExtra(EXTRA_ALARM_VIBRATE, isVibrate)
            putExtra(EXTRA_ALARM_SOUND, soundTitle)
            putExtra(EXTRA_ALARM_SNOOZE_MINS, snoozeMinutes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
        try {
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Snoozed alarm $alarmId for $snoozeMinutes minutes")
        } catch (e: Exception) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }
}
