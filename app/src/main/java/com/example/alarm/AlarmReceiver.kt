package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.RRApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        Log.d(TAG, "onReceive action: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val alarms = RRApplication.instance.database.alarmDao().getEnabledAlarms()
                        for (alarm in alarms) {
                            AlarmScheduler.scheduleAlarm(context, alarm)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed rescheduling alarms on boot", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            AlarmScheduler.ACTION_ALARM_FIRE -> {
                val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
                val label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL) ?: "Alarm"
                val hour = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_HOUR, 0)
                val minute = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_MINUTE, 0)
                val isVibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, true)
                val soundTitle = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_SOUND) ?: "Radar Beep"
                val snoozeMins = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, 5)

                // Start AlarmService foreground service
                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
                    putExtra(AlarmScheduler.EXTRA_ALARM_HOUR, hour)
                    putExtra(AlarmScheduler.EXTRA_ALARM_MINUTE, minute)
                    putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, isVibrate)
                    putExtra(AlarmScheduler.EXTRA_ALARM_SOUND, soundTitle)
                    putExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, snoozeMins)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

                // Launch AlarmAlertActivity
                val alertIntent = Intent(context, AlarmAlertActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
                    putExtra(AlarmScheduler.EXTRA_ALARM_HOUR, hour)
                    putExtra(AlarmScheduler.EXTRA_ALARM_MINUTE, minute)
                    putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, isVibrate)
                    putExtra(AlarmScheduler.EXTRA_ALARM_SOUND, soundTitle)
                    putExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, snoozeMins)
                }
                context.startActivity(alertIntent)
            }

            AlarmScheduler.ACTION_ALARM_DISMISS -> {
                val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    this.action = AlarmScheduler.ACTION_ALARM_DISMISS
                }
                context.stopService(serviceIntent)

                if (alarmId > 0) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val alarm = RRApplication.instance.database.alarmDao().getAlarmById(alarmId)
                            if (alarm != null) {
                                if (alarm.isOneTime) {
                                    RRApplication.instance.database.alarmDao().setAlarmEnabled(alarmId, false)
                                } else {
                                    AlarmScheduler.scheduleAlarm(context, alarm)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling alarm dismiss", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }

            AlarmScheduler.ACTION_ALARM_SNOOZE -> {
                val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
                val label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL) ?: "Alarm"
                val snoozeMins = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, 5)
                val isVibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, true)
                val soundTitle = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_SOUND) ?: "Radar Beep"

                val serviceIntent = Intent(context, AlarmService::class.java)
                context.stopService(serviceIntent)

                AlarmScheduler.scheduleSnooze(context, alarmId, label, snoozeMins, isVibrate, soundTitle)
            }
        }
    }
}
