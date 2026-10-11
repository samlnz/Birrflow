package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.FinancialDao
import com.example.data.entity.AppStateEntity
import com.example.data.entity.AuditLogEntity
import com.example.data.entity.CategoryEntity
import com.example.data.entity.FinancialSourceEntity
import com.example.data.entity.IncomeSourceEntity
import com.example.data.entity.InternalTransferConfigEntity
import com.example.data.entity.ManualSourceEntity
import com.example.data.entity.RawSmsEntity
import com.example.data.entity.RegisteredPhoneEntity
import com.example.data.entity.SavingsGoalEntity
import com.example.data.entity.SmsFormatEntity
import com.example.data.entity.SmsSenderIdentifierEntity
import com.example.data.entity.SyncOutboxEntity
import com.example.data.entity.TransactionEntity
import com.example.data.entity.UserEntity
import com.example.data.entity.WealthItemEntity

@Database(
    entities = [
        UserEntity::class,
        RegisteredPhoneEntity::class,
        FinancialSourceEntity::class,
        SmsSenderIdentifierEntity::class,
        SmsFormatEntity::class,
        RawSmsEntity::class,
        TransactionEntity::class,
        CategoryEntity::class,
        IncomeSourceEntity::class,
        InternalTransferConfigEntity::class,
        SavingsGoalEntity::class,
        AuditLogEntity::class,
        ManualSourceEntity::class,
        AppStateEntity::class,
        SyncOutboxEntity::class,
        WealthItemEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun financialDao(): FinancialDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v3 -> v4 keeps every existing transaction. It adds the credit/debit direction,
         * reconciliation flag, manual cash sources and the key/value app state table.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `direction` TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isReconciliation` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `transactions` SET `updatedAt` = `createdTimestamp`")
                db.execSQL(
                    "UPDATE `transactions` SET `direction` = CASE " +
                        "WHEN `transactionType` IN ('INCOME','DEPOSIT') THEN 'CREDIT' " +
                        "WHEN `transactionType` IN ('EXPENSE','WITHDRAWAL','FEE','TRANSFER') THEN 'DEBIT' " +
                        "WHEN `transactionType` = 'INTERNAL_TRANSFER' AND `recipient` IS NOT NULL THEN 'DEBIT' " +
                        "WHEN `transactionType` = 'INTERNAL_TRANSFER' THEN 'CREDIT' " +
                        "ELSE 'UNKNOWN' END"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_referenceNumber` ON `transactions` (`referenceNumber`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_phoneId_financialAccountId_createdTimestamp` " +
                        "ON `transactions` (`phoneId`, `financialAccountId`, `createdTimestamp`)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_rawSmsId` ON `transactions` (`rawSmsId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_raw_sms_bodyHash` ON `raw_sms` (`bodyHash`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `manual_sources` (" +
                        "`manualSourceId` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`balance` REAL NOT NULL, `phoneId` TEXT, `note` TEXT NOT NULL, `colorHex` TEXT NOT NULL, " +
                        "`isDeleted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`manualSourceId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_state` (" +
                        "`stateKey` TEXT NOT NULL, `stateValue` TEXT NOT NULL, PRIMARY KEY(`stateKey`))"
                )
            }
        }

        /** v4 -> v5 adds the cloud sync outbox. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_outbox` (" +
                        "`itemKey` TEXT NOT NULL, `kind` TEXT NOT NULL, `refId` TEXT NOT NULL, `queuedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`itemKey`))"
                )
            }
        }

        /** v5 -> v6 adds income sources table, assignedIncomeSourceId to phones, and resets old default categories */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `registered_phones` ADD COLUMN `assignedIncomeSourceId` TEXT DEFAULT NULL")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `income_sources` (
                        `sourceId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `colorHex` TEXT NOT NULL,
                        `iconName` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`sourceId`)
                    )
                    """.trimIndent()
                )
                // Clear old hardcoded default spending categories on debits so they become uncategorized expenses:
                db.execSQL("UPDATE `transactions` SET `categoryId` = NULL WHERE `direction` = 'DEBIT' AND `categoryId` IN ('cat_shopping', 'cat_bills', 'cat_food', 'cat_transport', 'cat_rent', 'cat_health', 'cat_other', 'cat_salary', 'cat_business')")
                // Delete non-custom default categories from categories table
                db.execSQL("DELETE FROM `categories` WHERE `isCustom` = 0")
            }
        }

        /** v6 -> v7 adds the user-written description of each expense category (AI advisor context). */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v7 -> v8 adds the net-worth ledger (equb, gold, shares, loans, receivables, stock, property). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `wealth_items` (" +
                        "`itemId` TEXT NOT NULL, `kind` TEXT NOT NULL, `name` TEXT NOT NULL, `quantity` REAL NOT NULL, " +
                        "`unitPriceEtb` REAL NOT NULL, `ratePct` REAL NOT NULL, `counterparty` TEXT NOT NULL, `dueDate` TEXT NOT NULL, " +
                        "`recurringAmountEtb` REAL NOT NULL, `recurringEveryDays` INTEGER NOT NULL, `notes` TEXT NOT NULL, " +
                        "`isDeleted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`itemId`))"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "birrflow_financial.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    // Only very old pre-release schemas (< v3) are rebuilt; v3+ data is always migrated.
                    .fallbackToDestructiveMigrationFrom(true, 1, 2)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
