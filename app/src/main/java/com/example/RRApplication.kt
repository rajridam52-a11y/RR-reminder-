package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.SettingsRepository
import com.example.integration.firebase.FirestoreProvider
import com.example.integration.protection.DeviceRegistrationManager
import com.example.integration.protection.EmergencyLockdownManager
import com.example.integration.protection.RemoteSettingsSyncManager
import com.example.integration.worker.CommandSyncScheduler
import com.example.security.events.SecurityEventManager
import com.example.security.settings.SecuritySettingsManager

class RRApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "Initializing RR Reminder application components...")

        createNotificationChannels()

        FirestoreProvider.init(this)
        SecuritySettingsManager.initialize(this)
        SecurityEventManager.initialize(this)
        DeviceRegistrationManager.initialize(this)
        EmergencyLockdownManager.initialize(this)
        RemoteSettingsSyncManager.initialize(this)

        CommandSyncScheduler.schedulePeriodicRecovery(this)
        CommandSyncScheduler.registerNetworkCallback(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM_ID,
                getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.alarm_channel_desc)
                enableVibration(true)
                enableLights(true)
            }

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.service_channel_desc)
            }

            val timerChannel = NotificationChannel(
                CHANNEL_TIMER_ID,
                getString(R.string.timer_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.timer_channel_desc)
                enableVibration(true)
            }

            val securityChannel = NotificationChannel(
                CHANNEL_SECURITY_ID,
                getString(R.string.security_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.security_channel_desc)
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(
                listOf(alarmChannel, serviceChannel, timerChannel, securityChannel)
            )
        }
    }

    companion object {
        private const val TAG = "RRApplication"

        @Volatile
        lateinit var instance: RRApplication
            private set

        const val CHANNEL_ALARM_ID = "rr_alarm_channel"
        const val CHANNEL_SERVICE_ID = "rr_service_channel"
        const val CHANNEL_TIMER_ID = "rr_timer_channel"
        const val CHANNEL_SECURITY_ID = "rr_security_channel"
    }
}
