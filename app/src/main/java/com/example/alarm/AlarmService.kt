package com.example.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.RRApplication

class AlarmService : Service() {

    companion object {
        private const val TAG = "AlarmService"
        private const val NOTIFICATION_ID = 2001
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == AlarmScheduler.ACTION_ALARM_DISMISS) {
            stopAlarm()
            stopSelf()
            return START_NOT_STICKY
        }

        val alarmId = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) ?: -1L
        val label = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL) ?: "Alarm"
        val isVibrate = intent?.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, true) ?: true
        val soundTitle = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_SOUND) ?: "Radar Beep"
        val snoozeMins = intent?.getIntExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, 5) ?: 5

        startForeground(NOTIFICATION_ID, buildForegroundNotification(alarmId, label, snoozeMins, isVibrate, soundTitle))
        startAlarmAudio(soundTitle)
        if (isVibrate) {
            startAlarmVibration()
        }

        return START_STICKY
    }

    private fun buildForegroundNotification(
        alarmId: Long,
        label: String,
        snoozeMins: Int,
        isVibrate: Boolean,
        soundTitle: String
    ): Notification {
        // Dismiss action
        val dismissIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_ALARM_DISMISS
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            this,
            (alarmId + 1000).toInt(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action
        val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_ALARM_SNOOZE
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, snoozeMins)
            putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, isVibrate)
            putExtra(AlarmScheduler.EXTRA_ALARM_SOUND, soundTitle)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this,
            (alarmId + 2000).toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(this, AlarmAlertActivity::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, snoozeMins)
            putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, isVibrate)
            putExtra(AlarmScheduler.EXTRA_ALARM_SOUND, soundTitle)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarmId.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, RRApplication.CHANNEL_ALARM_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⏰ $label")
            .setContentText("Alarm ringing! Tap Dismiss or Snooze.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .addAction(0, "Snooze (${snoozeMins}m)", snoozePendingIntent)
            .build()
    }

    private fun startAlarmAudio(soundTitle: String) {
        if (!RRApplication.instance.settingsRepository.isSoundEnabled()) return
        try {
            val alertUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed playing alarm ringtone", e)
        }
    }

    private fun startAlarmVibration() {
        if (!RRApplication.instance.settingsRepository.isVibrationEnabled()) return
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 800, 400, 800, 400, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting vibration", e)
        }
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }
}
