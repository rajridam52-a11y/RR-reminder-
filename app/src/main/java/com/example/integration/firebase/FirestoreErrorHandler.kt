package com.example.integration.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

object FirestoreProvider {
    @Volatile
    private var cachedDatabaseId: String? = null

    fun init(context: Context) {
        val dbId = context.applicationContext.getString(R.string.firestore_database_id)
        if (dbId.isNotBlank()) {
            cachedDatabaseId = dbId
        }
    }

    @androidx.annotation.VisibleForTesting
    fun initWithDatabaseId(databaseId: String) {
        cachedDatabaseId = databaseId
    }

    fun getDatabaseId(context: Context): String {
        return cachedDatabaseId ?: context.applicationContext.getString(R.string.firestore_database_id).also {
            cachedDatabaseId = it
        }
    }

    fun getFirestore(context: Context): FirebaseFirestore {
        return FirebaseFirestore.getInstance(getDatabaseId(context))
    }

    fun getFirestoreOrNull(context: Context): FirebaseFirestore? {
        return try {
            getFirestore(context)
        } catch (e: Exception) {
            Log.w("FirestoreProvider", "Firebase not initialized in this environment: ${e.message}")
            null
        }
    }
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = try {
        FirebaseAuth.getInstance().currentUser
    } catch (_: Exception) {
        null
    }

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}
