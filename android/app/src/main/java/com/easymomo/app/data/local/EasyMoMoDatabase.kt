package com.easymomo.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        TransactionEventEntity::class,
        UssdFlowConfigEntity::class,
        DataBundleEntity::class,
        SyncQueueEntity::class,
        CustomerNumberEntity::class,
        UssdCaptureSessionEntity::class,
        UssdCaptureEventEntity::class,
        CustomUssdFlowEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class EasyMoMoDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun ussdFlowConfigDao(): UssdFlowConfigDao
    abstract fun dataBundleDao(): DataBundleDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun customerNumberDao(): CustomerNumberDao
    abstract fun customUssdDao(): CustomUssdDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `customer_numbers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `phoneNumber` TEXT NOT NULL,
                        `normalizedPhoneNumber` TEXT NOT NULL,
                        `nickname` TEXT,
                        `firstUsedAt` INTEGER NOT NULL,
                        `lastUsedAt` INTEGER NOT NULL,
                        `usageCount` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_customer_numbers_normalizedPhoneNumber` ON `customer_numbers` (`normalizedPhoneNumber`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_customer_numbers_phoneNumber` ON `customer_numbers` (`phoneNumber`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_customer_numbers_lastUsedAt` ON `customer_numbers` (`lastUsedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_customer_numbers_usageCount` ON `customer_numbers` (`usageCount`)"
                )
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `customerNumberId` INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ussd_capture_sessions` (
                        `id` TEXT NOT NULL,
                        `flowName` TEXT NOT NULL,
                        `network` TEXT NOT NULL,
                        `entryCode` TEXT NOT NULL,
                        `subscriptionId` INTEGER,
                        `startedAt` INTEGER NOT NULL,
                        `finishedAt` INTEGER,
                        `status` TEXT NOT NULL,
                        `capturedEventCount` INTEGER NOT NULL,
                        `captureVersion` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ussd_capture_sessions_status` ON `ussd_capture_sessions` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ussd_capture_sessions_startedAt` ON `ussd_capture_sessions` (`startedAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ussd_capture_events` (
                        `id` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `sequenceNumber` INTEGER NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `promptText` TEXT NOT NULL,
                        `visibleOptionsJson` TEXT NOT NULL,
                        `selectedAction` TEXT,
                        `inputControlType` TEXT,
                        `observedNodeClass` TEXT,
                        `nodeMetadataSummary` TEXT,
                        `observedTimestamp` INTEGER NOT NULL,
                        `confidence` TEXT NOT NULL,
                        `isSensitive` INTEGER NOT NULL,
                        `isComplete` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`sessionId`) REFERENCES `ussd_capture_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ussd_capture_events_sessionId` ON `ussd_capture_events` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ussd_capture_events_sequenceNumber` ON `ussd_capture_events` (`sequenceNumber`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_ussd_flows` (
                        `id` TEXT NOT NULL,
                        `flowName` TEXT NOT NULL,
                        `network` TEXT NOT NULL,
                        `entryCode` TEXT NOT NULL,
                        `serviceDescription` TEXT NOT NULL,
                        `transactionCategory` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `sourceCaptureSessionId` TEXT,
                        `stepsJson` TEXT NOT NULL,
                        `stepCount` INTEGER NOT NULL,
                        `flowVersion` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_ussd_flows_network` ON `custom_ussd_flows` (`network`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_ussd_flows_status` ON `custom_ussd_flows` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_ussd_flows_updatedAt` ON `custom_ussd_flows` (`updatedAt`)")
            }
        }

        @Volatile
        private var INSTANCE: EasyMoMoDatabase? = null

        fun getInstance(context: Context): EasyMoMoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    EasyMoMoDatabase::class.java,
                    "easymomo_terminal.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            // Safe with WAL mode; reduces fsync stalls on low-end eMMC flash
                            runCatching {
                                db.execSQL("PRAGMA synchronous = NORMAL")
                                db.execSQL("PRAGMA temp_store = MEMORY")
                                db.execSQL("PRAGMA cache_size = -2000")
                            }
                        }
                    })
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
