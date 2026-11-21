package com.example.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.integration.firebase.FirestoreProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
abstract class FirestoreEmulatorTestBase {

    protected lateinit var appContext: Context
    protected lateinit var firestore: FirebaseFirestore
    protected lateinit var auth: FirebaseAuth
    protected lateinit var databaseId: String

    @Before
    open fun setUpFirebase() {
        appContext = ApplicationProvider.getApplicationContext()
        val resId = appContext.resources.getIdentifier(
            "firestore_database_id",
            "string",
            appContext.packageName
        )
        databaseId = if (resId != 0) {
            appContext.getString(resId)
        } else {
            NAMED_DATABASE_ID
        }
        FirestoreProvider.initWithDatabaseId(databaseId)

        val existingDefault = FirebaseApp.getApps(appContext)
            .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        if (existingDefault != null && existingDefault.options.projectId != PROJECT_ID) {
            existingDefault.delete()
        }

        val app = if (FirebaseApp.getApps(appContext).isEmpty()) {
            val newApp = FirebaseApp.initializeApp(
                appContext,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.rrreminder.mvkqtz")
                    .setProjectId(PROJECT_ID)
                    .setApiKey("fake-api-key-for-emulator")
                    .build()
            )
            val newFirestore = FirebaseFirestore.getInstance(newApp, databaseId)
            newFirestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
            newFirestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()

            val newAuth = FirebaseAuth.getInstance(newApp)
            newAuth.useEmulator(EMULATOR_HOST, AUTH_PORT)
            newApp
        } else {
            FirebaseApp.getInstance()
        }

        firestore = FirebaseFirestore.getInstance(app, databaseId)
        auth = FirebaseAuth.getInstance(app)
    }

    @After
    open fun tearDownFirebase() {
        try {
            auth.signOut()
        } catch (_: Exception) {
        }
    }

    // Local unit test helper ONLY under app/src/test/. Production sign-in uses Credential Manager.
    protected suspend fun signInTestUser(email: String): String = withContext(Dispatchers.IO) {
        withTimeout(AUTH_TIMEOUT_MS) {
            val result = try {
                auth.signInWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
            } catch (unused: FirebaseAuthInvalidUserException) {
                auth.createUserWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
            }
            checkNotNull(result.user?.uid) { "User auth failed for $email" }
        }
    }

    protected fun Throwable.unwrapFirestoreException(): FirebaseFirestoreException? {
        var current: Throwable? = this
        while (current != null) {
            if (current is FirebaseFirestoreException) return current
            current = current.cause
        }
        return null
    }

    protected inline fun <reified T : Throwable> assertThrowsType(block: () -> Unit): T {
        try {
            block()
        } catch (t: Throwable) {
            if (t is T) return t
            throw AssertionError("Expected ${T::class.java.simpleName}, but caught ${t::class.java.simpleName}: ${t.message}", t)
        }
        throw AssertionError("Expected ${T::class.java.simpleName} to be thrown, but nothing was thrown.")
    }

    private companion object {
        const val EMULATOR_HOST = "127.0.0.1"
        const val FIRESTORE_PORT = 8085
        const val AUTH_PORT = 9099
        const val PROJECT_ID = "demo-no-project"
        const val NAMED_DATABASE_ID = "ai-studio-android-rrremind-1d7fcad6-b298-4462-b625-7de65ff4b83c"
        const val DEFAULT_PASSWORD = "password123"
        const val AUTH_TIMEOUT_MS = 5000L
    }
}
