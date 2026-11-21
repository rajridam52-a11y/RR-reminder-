package com.example.integration.protection

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.AlarmEntity
import com.example.data.model.PlannerTaskEntity
import com.example.data.model.QuickNoteEntity
import com.example.data.model.ReminderEntity
import com.example.security.events.SecurityEventManager
import com.example.security.settings.SecurityLevel
import com.example.security.settings.SecuritySettingsManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CommandDeduplicationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SecuritySettingsManager.initialize(context)
        SecurityEventManager.initialize(context)
        DeviceRegistrationManager.initialize(context)
        EmergencyLockdownManager.initialize(context)
        RemoteSettingsSyncManager.initialize(context)
        db = AppDatabase.getDatabase(context)
    }

    @Test
    fun duplicateCommandId_isExecutedOnceAndDeduplicatedInRoom() = runBlocking {
        val cmdId = "cmd_dedup_${UUID.randomUUID()}"
        EmergencyLockdownManager.releaseLockdown("admin_override", isRemoteAdminAuthorized = true)
        assertThat(EmergencyLockdownManager.isLockdownActive()).isFalse()

        val first = CommandProcessor.processCommand(
            context = context,
            commandId = cmdId,
            data = mapOf(
                "action" to "LOCKDOWN",
                "reason" to "Anti-replay verification",
                "expiresAt" to (System.currentTimeMillis() + 60_000L)
            )
        )
        assertThat(first).isTrue()
        assertThat(EmergencyLockdownManager.isLockdownActive()).isTrue()
        assertThat(db.processedCommandDao().isCommandProcessed(cmdId)).isTrue()

        // Manually release lockdown; replaying the exact same commandId must be ignored cleanly
        EmergencyLockdownManager.releaseLockdown("admin_override", isRemoteAdminAuthorized = true)
        assertThat(EmergencyLockdownManager.isLockdownActive()).isFalse()

        val replay = CommandProcessor.processCommand(
            context = context,
            commandId = cmdId,
            data = mapOf(
                "action" to "LOCKDOWN",
                "reason" to "Replayed attacker packet",
                "expiresAt" to (System.currentTimeMillis() + 60_000L)
            )
        )
        assertThat(replay).isTrue()
        assertThat(EmergencyLockdownManager.isLockdownActive()).isFalse()
    }

    @Test
    fun expiredCommand_isRejectedAndDoesNotTriggerLockdown() = runBlocking {
        val cmdId = "cmd_exp_${UUID.randomUUID()}"
        EmergencyLockdownManager.releaseLockdown("admin_override", isRemoteAdminAuthorized = true)

        val result = CommandProcessor.processCommand(
            context = context,
            commandId = cmdId,
            data = mapOf(
                "action" to "LOCKDOWN",
                "reason" to "Expired command",
                "expiresAt" to (System.currentTimeMillis() - 10_000L)
            )
        )
        assertThat(result).isFalse()
        assertThat(EmergencyLockdownManager.isLockdownActive()).isFalse()
        assertThat(db.processedCommandDao().isCommandProcessed(cmdId)).isTrue()
    }

    @Test
    fun unknownAction_isRejectedAsInvalid() = runBlocking {
        val cmdId = "cmd_bad_${UUID.randomUUID()}"
        val result = CommandProcessor.processCommand(
            context = context,
            commandId = cmdId,
            data = mapOf(
                "action" to "WIPE_ALL_USER_DATA",
                "expiresAt" to (System.currentTimeMillis() + 60_000L)
            )
        )
        assertThat(result).isFalse()
        assertThat(db.processedCommandDao().isCommandProcessed(cmdId)).isTrue()
    }

    @Test
    fun syncSettingsCommand_preservesUserAlarmsRemindersNotesAndTasks() = runBlocking {
        db.alarmDao().insertAlarm(AlarmEntity(hour = 7, minute = 30, label = "Morning Alarm"))
        db.reminderDao().insertReminder(
            ReminderEntity(
                title = "Pay Electricity Bill",
                description = "Keep user note safe",
                timeMillis = System.currentTimeMillis() + 3_600_000L
            )
        )
        db.quickNoteDao().insertNote(QuickNoteEntity(title = "Grocery", content = "Milk, Coffee"))
        db.plannerTaskDao().insertTask(PlannerTaskEntity(taskText = "Finish Study", dateString = "2026-11-12"))

        val cmdId = "cmd_sync_${UUID.randomUUID()}"
        val result = CommandProcessor.processCommand(
            context = context,
            commandId = cmdId,
            data = mapOf(
                "action" to "SYNC_SETTINGS",
                "securityLevel" to "HIGH",
                "maxFailures" to 3,
                "lockoutSeconds" to 120,
                "autoLockdownOnTamper" to true,
                "expiresAt" to (System.currentTimeMillis() + 60_000L)
            )
        )

        assertThat(result).isTrue()
        assertThat(SecuritySettingsManager.getConfiguration().level).isEqualTo(SecurityLevel.HIGH)
        assertThat(SecuritySettingsManager.getConfiguration().maxConsecutiveFailures).isEqualTo(3)

        // Mandatory invariant: user reminders, alarms, notes, and planner tasks remain completely intact
        assertThat(db.alarmDao().getAllAlarmsFlow().first().any { it.label == "Morning Alarm" }).isTrue()
        assertThat(db.reminderDao().getAllRemindersFlow().first().any { it.title == "Pay Electricity Bill" }).isTrue()
        assertThat(db.quickNoteDao().getAllNotesFlow().first().any { it.title == "Grocery" }).isTrue()
        assertThat(db.plannerTaskDao().getTasksForDateFlow("2026-11-12").first().any { it.taskText == "Finish Study" }).isTrue()
    }

    @Test
    fun allSecurityLevels_workAndEnforceThresholdsViaRemoteCommands(): Unit = runBlocking {
        // 1. STANDARD: 5 failures, 60s lockout, biometrics not required
        CommandProcessor.processCommand(
            context = context,
            commandId = "cmd_lvl_std_${UUID.randomUUID()}",
            data = mapOf("action" to "SYNC_SETTINGS", "securityLevel" to "STANDARD")
        )
        val std = SecuritySettingsManager.getConfiguration()
        assertThat(std.level).isEqualTo(SecurityLevel.STANDARD)
        assertThat(std.maxConsecutiveFailures).isEqualTo(5)
        assertThat(std.lockoutDurationSeconds).isEqualTo(60L)
        assertThat(std.minAlertSeverity).isEqualTo("HIGH")
        assertThat(std.requireBiometricsForSensitiveOps).isFalse()

        // 2. HIGH: 3 failures, 120s lockout, MEDIUM alert threshold, biometrics required
        CommandProcessor.processCommand(
            context = context,
            commandId = "cmd_lvl_high_${UUID.randomUUID()}",
            data = mapOf("action" to "SYNC_SETTINGS", "securityLevel" to "HIGH")
        )
        val high = SecuritySettingsManager.getConfiguration()
        assertThat(high.level).isEqualTo(SecurityLevel.HIGH)
        assertThat(high.maxConsecutiveFailures).isEqualTo(3)
        assertThat(high.lockoutDurationSeconds).isEqualTo(120L)
        assertThat(high.minAlertSeverity).isEqualTo("MEDIUM")
        assertThat(high.requireBiometricsForSensitiveOps).isTrue()

        // 3. MAXIMUM: 2 failures, 300s lockout, MEDIUM alert threshold, biometrics required
        CommandProcessor.processCommand(
            context = context,
            commandId = "cmd_lvl_max_${UUID.randomUUID()}",
            data = mapOf("action" to "SYNC_SETTINGS", "securityLevel" to "MAXIMUM")
        )
        val max = SecuritySettingsManager.getConfiguration()
        assertThat(max.level).isEqualTo(SecurityLevel.MAXIMUM)
        assertThat(max.maxConsecutiveFailures).isEqualTo(2)
        assertThat(max.lockoutDurationSeconds).isEqualTo(300L)
        assertThat(max.minAlertSeverity).isEqualTo("MEDIUM")
        assertThat(max.requireBiometricsForSensitiveOps).isTrue()

        // Verify MAXIMUM actually triggers lockout after 2 failed attempts
        SecurityEventManager.reportSuccessfulLogin("Test", "owner") // reset counter
        val firstFail = SecurityEventManager.reportFailedLoginAttempt("Test", "owner")
        assertThat(firstFail).isNotEqualTo(com.example.security.events.SecurityAction.BLOCKED)
        assertThat(SecurityEventManager.isTemporaryLockoutActive()).isFalse()

        val secondFail = SecurityEventManager.reportFailedLoginAttempt("Test", "owner")
        assertThat(secondFail).isEqualTo(com.example.security.events.SecurityAction.BLOCKED)
        assertThat(SecurityEventManager.isTemporaryLockoutActive()).isTrue()
        assertThat(SecurityEventManager.getRemainingLockoutSeconds()).isAtLeast(250L)

        // Reset clean state to STANDARD for isolation
        SecurityEventManager.reportSuccessfulLogin("Test", "owner")
        CommandProcessor.processCommand(
            context = context,
            commandId = "cmd_lvl_reset_${UUID.randomUUID()}",
            data = mapOf("action" to "SYNC_SETTINGS", "securityLevel" to "STANDARD")
        )
    }
}
