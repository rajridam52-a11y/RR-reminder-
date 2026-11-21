package com.example.integration.protection

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.example.integration.firebase.FirestoreProvider
import com.example.integration.firebase.OperationType
import com.example.integration.firebase.handleFirestoreError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

object DeviceRegistrationManager {
    private const val TAG = "DeviceRegistration"
    private const val PREFS = "rr_device_reg"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_REGISTERED_AT = "registered_at"

    private var deviceId: String = ""
    private var fcmToken: String = ""
    private var registeredAt: Long = System.currentTimeMillis()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _registrationState = MutableStateFlow(DeviceAuthStatus.UNREGISTERED)
    val registrationState: StateFlow<DeviceAuthStatus> = _registrationState.asStateFlow()

    fun initialize(context: Context) {
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        deviceId = sp.getString(KEY_DEVICE_ID, null) ?: run {
            val newId = "dev_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            sp.edit().putString(KEY_DEVICE_ID, newId).apply()
            newId
        }
        fcmToken = sp.getString(KEY_FCM_TOKEN, "") ?: ""
        registeredAt = sp.getLong(KEY_REGISTERED_AT, 0L).let {
            if (it == 0L) {
                val now = System.currentTimeMillis()
                sp.edit().putLong(KEY_REGISTERED_AT, now).apply()
                now
            } else {
                it
            }
        }
        val signedIn = try {
            FirebaseAuth.getInstance().currentUser != null
        } catch (_: Exception) {
            false
        }
        _registrationState.value = if (signedIn) DeviceAuthStatus.AUTHENTICATED else DeviceAuthStatus.UNREGISTERED
        Log.i(TAG, "Device initialized with ID: $deviceId")
    }

    fun getDeviceId(): String = deviceId

    fun getCachedFcmToken(): String = fcmToken

    fun getDeviceInfo(): RegisteredDeviceInfo {
        return RegisteredDeviceInfo(
            deviceId = deviceId,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            registeredAt = registeredAt,
            lastActiveAt = System.currentTimeMillis(),
            authStatus = _registrationState.value
        )
    }

    fun updateFcmToken(context: Context, token: String) {
        fcmToken = token
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FCM_TOKEN, token)
            .apply()
        Log.d(TAG, "FCM Token cached locally")

        val user = try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        }
        if (user != null) {
            scope.launch {
                syncDeviceToFirestore(context.applicationContext, user.uid, token)
            }
        }
    }

    fun updateFcmToken(token: String) {
        fcmToken = token
        Log.d(TAG, "FCM Token updated in memory")
    }

    suspend fun registerOrSyncDevice(
        context: Context,
        firestoreOverride: com.google.firebase.firestore.FirebaseFirestore? = null
    ): Result<RegisteredDeviceInfo> {
        if (deviceId.isBlank()) {
            initialize(context)
        }
        val user = try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        } ?: return Result.failure(IllegalStateException("Google Sign-In is required to register this device."))

        val resolvedToken = if (firestoreOverride == null) {
            try {
                FirebaseMessaging.getInstance().token.await().also { tok ->
                    fcmToken = tok
                    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_FCM_TOKEN, tok)
                        .apply()
                }
            } catch (e: Exception) {
                Log.w(TAG, "FCM token fetch deferred: ${e.message}")
                fcmToken
            }
        } else {
            fcmToken.ifBlank { "emulator_test_fcm_token" }
        }

        val synced = syncDeviceToFirestore(context.applicationContext, user.uid, resolvedToken, firestoreOverride)
        return if (synced.isSuccess) {
            _registrationState.value = DeviceAuthStatus.AUTHENTICATED
            Result.success(getDeviceInfo())
        } else {
            Result.failure(synced.exceptionOrNull() ?: Exception("Device sync failed"))
        }
    }

    private suspend fun syncDeviceToFirestore(
        context: Context,
        ownerUid: String,
        token: String,
        firestoreOverride: com.google.firebase.firestore.FirebaseFirestore? = null
    ): Result<Unit> {
        val firestore = firestoreOverride ?: FirestoreProvider.getFirestoreOrNull(context)
            ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val docRef = firestore.collection("devices").document(deviceId)
        return try {
            val existing = docRef.get().await()
            val payload = hashMapOf<String, Any>(
                "deviceId" to deviceId,
                "ownerUid" to ownerUid,
                "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(128),
                "fcmToken" to token.take(512),
                "status" to "ACTIVE",
                "appVersion" to BuildConfig.VERSION_NAME,
                "lastActiveAt" to FieldValue.serverTimestamp()
            )
            if (!existing.exists()) {
                payload["registeredAt"] = FieldValue.serverTimestamp()
                docRef.set(payload).await()
            } else {
                docRef.update(payload).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }
}
