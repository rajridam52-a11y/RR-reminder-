package com.example.timer

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.RRApplication

class TimerService : Service() {

    companion object {
        const val ACTION_START = "com.example.rrreminder.timer.ACTION_START"
        const val ACTION_PAUSE = "com.example.rrreminder.timer.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.rrreminder.timer.ACTION_RESUME"
        const val ACTION_RESET = "com.example.rrreminder.timer.ACTION_RESET"

        private const val NOTIFICATION_ID = 3001
        private const val COMPLETION_NOTIFICATION_ID = 3002
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val rem = TimerManager.remainingSeconds.value - 1
            if (rem <= 0) {
                TimerManager.updateRemaining(0)
                TimerManager.updateStatus(TimerStatus.COMPLETED)
                onTimerCompleted()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                TimerManager.updateRemaining(rem)
                updateNotification()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isRunning = true
                TimerManager.updateStatus(TimerStatus.RUNNING)
                startForeground(NOTIFICATION_ID, buildNotification())
                handler.removeCallbacks(timerRunnable)
                handler.postDelayed(timerRunnable, 1000)
            }
            ACTION_PAUSE -> {
                isRunning = false
                TimerManager.updateStatus(TimerStatus.PAUSED)
                handler.removeCallbacks(timerRunnable)
                updateNotification()
            }
            ACTION_RESUME -> {
                isRunning = true
                TimerManager.updateStatus(TimerStatus.RUNNING)
                handler.removeCallbacks(timerRunnable)
                handler.postDelayed(timerRunnable, 1000)
                updateNotification()
            }
            ACTION_RESET -> {
                isRunning = false
                handler.removeCallbacks(timerRunnable)
                TimerManager.updateRemaining(TimerManager.totalSeconds.value)
                TimerManager.updateStatus(TimerStatus.IDLE)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "timer")
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            301,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = if (isRunning) {
            val pauseIntent = Intent(this, TimerService::class.java).apply { action = ACTION_PAUSE }
            val pi = PendingIntent.getService(this, 302, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action.Builder(0, "Pause", pi).build()
        } else {
            val resumeIntent = Intent(this, TimerService::class.java).apply { action = ACTION_RESUME }
            val pi = PendingIntent.getService(this, 303, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action.Builder(0, "Resume", pi).build()
        }

        val resetIntent = Intent(this, TimerService::class.java).apply { action = ACTION_RESET }
        val resetPendingIntent = PendingIntent.getService(this, 304, resetIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val resetAction = NotificationCompat.Action.Builder(0, "Reset", resetPendingIntent).build()

        return NotificationCompat.Builder(this, RRApplication.CHANNEL_SERVICE_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Countdown Timer: ${TimerManager.getFormattedRemaining()}")
            .setContentText(if (isRunning) "Timer is running" else "Timer is paused")
            .setContentIntent(openPendingIntent)
            .setOngoing(isRunning)
            .setOnlyAlertOnce(true)
            .addAction(pauseResumeAction)
            .addAction(resetAction)
            .build()
    }

    private fun onTimerCompleted() {
        try {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, soundUri)
            ringtone?.play()
        } catch (_: Exception) {}

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 500), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
            }
        } catch (_: Exception) {}

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "timer")
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            305,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completionNotification = NotificationCompat.Builder(this, RRApplication.CHANNEL_TIMER_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Timer Complete!")
            .setContentText("Your countdown timer has finished.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(COMPLETION_NOTIFICATION_ID, completionNotification)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
        isRunning = false
    }
}
