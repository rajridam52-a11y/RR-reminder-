package com.example.integration.fcm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.RRApplication
import com.example.integration.protection.CommandProcessor
import com.example.integration.protection.DeviceRegistrationManager
import com.example.integration.worker.CommandSyncScheduler
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RRFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM registration token received")
        DeviceRegistrationManager.updateFcmToken(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        Log.d(TAG, "Remote FCM message received with keys: ${data.keys}")

        val commandId = data["commandId"] ?: data["id"]
        if (commandId.isNullOrBlank()) {
            // Data wake-up signal without payload ID: recover all pending commands via WorkManager
            CommandSyncScheduler.triggerImmediateSync(applicationContext)
            return
        }

        val action = (data["action"] ?: data["type"] ?: "").uppercase()
        if (action == "LOCKDOWN") {
            postSecurityNotification(
                title = "Emergency Lockdown Activated",
                body = data["reason"] ?: "Remote administrator command applied."
            )
        }

        scope.launch {
            CommandProcessor.processCommand(applicationContext, commandId, data)
        }
    }

    private fun postSecurityNotification(title: String, body: String) {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_to", "home")
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                7001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(this, RRApplication.CHANNEL_SECURITY_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(7001, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed posting security notification: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        private const val TAG = "RR_FCM"
    }
}
