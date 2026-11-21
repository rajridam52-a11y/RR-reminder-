package com.example.integration.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.integration.firebase.FirestoreProvider
import com.example.integration.firebase.OperationType
import com.example.integration.firebase.handleFirestoreError
import com.example.integration.protection.CommandProcessor
import com.example.integration.protection.DeviceRegistrationManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class CommandSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val currentUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        }
        if (currentUser == null) {
            Log.d(TAG, "User not authenticated; skipping pending command poll cleanly.")
            return Result.success()
        }

        val deviceId = DeviceRegistrationManager.getDeviceId()
        if (deviceId.isBlank()) {
            DeviceRegistrationManager.initialize(context)
        }
        val activeDeviceId = DeviceRegistrationManager.getDeviceId()
        if (activeDeviceId.isBlank()) {
            return Result.success()
        }

        val path = "devices/$activeDeviceId/pending_commands"
        return try {
            val firestore = FirestoreProvider.getFirestore(context)
            val querySnapshot = firestore.collection("devices")
                .document(activeDeviceId)
                .collection("pending_commands")
                .whereEqualTo("ownerUid", currentUser.uid)
                .whereEqualTo("status", "PENDING")
                .get()
                .await()

            for (doc in querySnapshot.documents) {
                val data = doc.data ?: continue
                CommandProcessor.processCommand(context, doc.id, data, firestore)
            }
            Result.success()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Log.w(TAG, "Failed to query pending commands from Firestore: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CommandSyncWorker"
    }
}
