package com.message.bulksend.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
        entities =
                [
                        Campaign::class,
                        Setting::class,
                        ContactGroup::class,
                        ScheduledCampaign::class,
                        AgentFormEntity::class,
                        AutonomousSendQueueEntity::class],
        version = 14,
        exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun campaignDao(): CampaignDao
    abstract fun settingDao(): SettingDao
    abstract fun contactGroupDao(): ContactGroupDao
    abstract fun scheduledCampaignDao(): ScheduledCampaignDao
    abstract fun agentFormDao(): AgentFormDao
    abstract fun autonomousSendQueueDao(): AutonomousSendQueueDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 =
                object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                                "ALTER TABLE campaigns ADD COLUMN isRunning INTEGER NOT NULL DEFAULT 0"
                        )
                    }
                }

        val MIGRATION_2_3 =
                object : Migration(2, 3) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                                """
                    CREATE TABLE IF NOT EXISTS `contact_groups` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `contacts` TEXT NOT NULL
                    )
                """.trimIndent()
                        )
                    }
                }

        val MIGRATION_3_4 =
                object : Migration(3, 4) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                                "ALTER TABLE contact_groups ADD COLUMN timestamp INTEGER NOT NULL DEFAULT 0"
                        )
                    }
                }

        val MIGRATION_4_5 =
                object : Migration(4, 5) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                                "ALTER TABLE campaigns ADD COLUMN campaignType TEXT NOT NULL DEFAULT 'BULKSEND'"
                        )
                    }
                }

        val MIGRATION_5_6 =
                object : Migration(5, 6) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                                """
                    CREATE TABLE campaigns_new (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `groupId` TEXT NOT NULL,
                        `campaignName` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `totalContacts` INTEGER NOT NULL,
                        `contactStatuses` TEXT NOT NULL,
                        `isStopped` INTEGER NOT NULL,
                        `isRunning` INTEGER NOT NULL,
                        `campaignType` TEXT NOT NULL DEFAULT 'BULKSEND'
                    )
                """.trimIndent()
                        )
                        db.execSQL(
                                """
                    INSERT INTO campaigns_new (id, groupId, campaignName, message, timestamp, totalContacts, contactStatuses, isStopped, isRunning, campaignType)
                    SELECT id, groupId, campaignName, message, timestamp, totalContacts, contactStatuses, isStopped, isRunning, campaignType FROM campaigns
                """.trimIndent()
                        )
                        db.execSQL("DROP TABLE campaigns")
                        db.execSQL("ALTER TABLE campaigns_new RENAME TO campaigns")
                    }
                }

        val MIGRATION_6_7 =
                object : Migration(6, 7) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE campaigns ADD COLUMN sheetFileName TEXT")
                        db.execSQL("ALTER TABLE campaigns ADD COLUMN countryCode TEXT")
                    }
                }

        val MIGRATION_7_8 =
                object : Migration(7, 8) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE campaigns ADD COLUMN sheetDataJson TEXT")
                    }
                }

        val MIGRATION_8_9 =
                object : Migration(8, 9) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE campaigns ADD COLUMN mediaPath TEXT")
                    }
                }

        val MIGRATION_9_10 =
                object : Migration(9, 10) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // Add any missing columns or tables for version 10
                        // This migration ensures continuity from version 9 to 10
                    }
                }

        val MIGRATION_10_11 =
                object : Migration(10, 11) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                                """
                    CREATE TABLE IF NOT EXISTS `scheduled_campaigns` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `campaignType` TEXT NOT NULL,
                        `campaignName` TEXT NOT NULL,
                        `scheduledTime` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdTime` INTEGER NOT NULL,
                        `campaignDataJson` TEXT NOT NULL,
                        `contactCount` INTEGER NOT NULL DEFAULT 0,
                        `groupId` TEXT,
                        `groupName` TEXT
                    )
                """.trimIndent()
                        )
                    }
                }

        val MIGRATION_11_12 =
                object : Migration(11, 12) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                                """
                    CREATE TABLE IF NOT EXISTS `agent_forms` (
                        `formId` TEXT NOT NULL PRIMARY KEY,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `fieldsJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent()
                        )
                    }
                }

        val MIGRATION_12_13 =
                object : Migration(12, 13) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // `sheetUrl` was added in Campaign entity. Add it for upgraded users.
                        if (!hasColumn(db, "campaigns", "sheetUrl")) {
                            db.execSQL("ALTER TABLE campaigns ADD COLUMN sheetUrl TEXT")
                        }
                    }
                }

        val MIGRATION_13_14 =
                object : Migration(13, 14) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                                """
                    CREATE TABLE IF NOT EXISTS `autonomous_send_queue` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `campaignId` TEXT NOT NULL,
                        `contactNumber` TEXT NOT NULL,
                        `contactName` TEXT NOT NULL,
                        `plannedTimeMillis` INTEGER NOT NULL,
                        `dayIndex` INTEGER NOT NULL,
                        `hourOfDay` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `retryCount` INTEGER NOT NULL,
                        `lastError` TEXT,
                        `sentTimeMillis` INTEGER,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent()
                        )
                        db.execSQL(
                                "CREATE INDEX IF NOT EXISTS `index_autonomous_send_queue_campaignId` ON `autonomous_send_queue` (`campaignId`)"
                        )
                        db.execSQL(
                                "CREATE INDEX IF NOT EXISTS `index_autonomous_send_queue_campaignId_status` ON `autonomous_send_queue` (`campaignId`, `status`)"
                        )
                        db.execSQL(
                                "CREATE INDEX IF NOT EXISTS `index_autonomous_send_queue_campaignId_plannedTimeMillis` ON `autonomous_send_queue` (`campaignId`, `plannedTimeMillis`)"
                        )
                    }
                }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance =
                                Room.databaseBuilder(
                                                context.applicationContext,
                                                AppDatabase::class.java,
                                                "bulksend_database"
                                        )
                                        .addMigrations(
                                                MIGRATION_1_2,
                                                MIGRATION_2_3,
                                                MIGRATION_3_4,
                                                MIGRATION_4_5,
                                                MIGRATION_5_6,
                                                MIGRATION_6_7,
                                                MIGRATION_7_8,
                                                MIGRATION_8_9,
                                                MIGRATION_9_10,
                                                MIGRATION_10_11,
                                                MIGRATION_11_12,
                                                MIGRATION_12_13,
                                                MIGRATION_13_14
                                        )
                                        .fallbackToDestructiveMigration()
                                        .fallbackToDestructiveMigrationOnDowngrade()
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }

        private fun hasColumn(
                db: SupportSQLiteDatabase,
                tableName: String,
                columnName: String
        ): Boolean {
            db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == columnName) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
