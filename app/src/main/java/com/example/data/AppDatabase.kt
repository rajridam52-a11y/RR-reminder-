package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AlarmDao
import com.example.data.dao.PlannerTaskDao
import com.example.data.dao.ProcessedCommandDao
import com.example.data.dao.QuickNoteDao
import com.example.data.dao.ReminderDao
import com.example.data.dao.WorldClockDao
import com.example.data.model.AlarmEntity
import com.example.data.model.PlannerTaskEntity
import com.example.data.model.ProcessedCommandEntity
import com.example.data.model.QuickNoteEntity
import com.example.data.model.ReminderEntity
import com.example.data.model.WorldClockEntity
import com.example.security.events.SecurityEventDao
import com.example.security.events.SecurityEventEntity

@Database(
    entities = [
        AlarmEntity::class,
        ReminderEntity::class,
        WorldClockEntity::class,
        QuickNoteEntity::class,
        PlannerTaskEntity::class,
        SecurityEventEntity::class,
        ProcessedCommandEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao
    abstract fun reminderDao(): ReminderDao
    abstract fun worldClockDao(): WorldClockDao
    abstract fun quickNoteDao(): QuickNoteDao
    abstract fun plannerTaskDao(): PlannerTaskDao
    abstract fun securityEventDao(): SecurityEventDao
    abstract fun processedCommandDao(): ProcessedCommandDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createSecurityTablesIfMissing(db)
            }
        }

        private val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createSecurityTablesIfMissing(db)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createSecurityTablesIfMissing(db)
            }
        }

        private fun createSecurityTablesIfMissing(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `security_events` (
                    `eventId` TEXT NOT NULL,
                    `eventType` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `severity` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `accountIdentifier` TEXT,
                    `detectionSource` TEXT NOT NULL,
                    `verificationStatus` TEXT NOT NULL,
                    `actionTaken` TEXT NOT NULL,
                    `detailsJson` TEXT NOT NULL,
                    PRIMARY KEY(`eventId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `processed_commands` (
                    `commandId` TEXT NOT NULL,
                    `commandType` TEXT NOT NULL,
                    `processedAtMillis` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    PRIMARY KEY(`commandId`)
                )
                """.trimIndent()
            )
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rr_reminder_database"
                )
                    .addMigrations(MIGRATION_1_4, MIGRATION_2_4, MIGRATION_3_4)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
