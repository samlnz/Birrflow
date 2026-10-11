package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
import com.example.data.entity.SmsProcessingStatus
import com.example.data.entity.SmsSenderIdentifierEntity
import com.example.data.entity.SyncOutboxEntity
import com.example.data.entity.TransactionEntity
import com.example.data.entity.UserEntity
import com.example.data.entity.WealthItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialDao {

    // --- Users ---
    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    fun getUser(userId: String): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    // --- Registered Phones ---
    @Query("SELECT * FROM registered_phones ORDER BY registeredAt ASC")
    fun getAllPhones(): Flow<List<RegisteredPhoneEntity>>

    @Query("SELECT * FROM registered_phones WHERE phoneId = :phoneId LIMIT 1")
    suspend fun getPhoneById(phoneId: String): RegisteredPhoneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhone(phone: RegisteredPhoneEntity)

    @Update
    suspend fun updatePhone(phone: RegisteredPhoneEntity)

    @Delete
    suspend fun deletePhone(phone: RegisteredPhoneEntity)

    // --- Financial Sources ---
    @Query("SELECT * FROM financial_sources ORDER BY name ASC")
    fun getAllSources(): Flow<List<FinancialSourceEntity>>

    @Query("SELECT * FROM financial_sources WHERE isActive = 1")
    suspend fun getActiveSources(): List<FinancialSourceEntity>

    @Query("SELECT * FROM financial_sources WHERE sourceId = :sourceId LIMIT 1")
    suspend fun getSourceById(sourceId: String): FinancialSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: FinancialSourceEntity)

    @Update
    suspend fun updateSource(source: FinancialSourceEntity)

    // --- SMS Sender Identifiers ---
    @Query("SELECT * FROM sms_sender_identifiers ORDER BY priority ASC, createdAt ASC")
    fun getAllSenderIdentifiers(): Flow<List<SmsSenderIdentifierEntity>>

    @Query("SELECT * FROM sms_sender_identifiers WHERE isActive = 1 ORDER BY priority ASC, createdAt ASC")
    suspend fun getActiveSenderIdentifiers(): List<SmsSenderIdentifierEntity>

    @Query("SELECT * FROM sms_sender_identifiers WHERE sourceId = :sourceId ORDER BY priority ASC")
    fun getSenderIdentifiersForSource(sourceId: String): Flow<List<SmsSenderIdentifierEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSenderIdentifier(identifier: SmsSenderIdentifierEntity)

    @Update
    suspend fun updateSenderIdentifier(identifier: SmsSenderIdentifierEntity)

    @Delete
    suspend fun deleteSenderIdentifier(identifier: SmsSenderIdentifierEntity)

    // --- SMS Formats ---
    @Query("SELECT * FROM sms_formats ORDER BY sourceId ASC, version DESC")
    fun getAllFormats(): Flow<List<SmsFormatEntity>>

    @Query("SELECT * FROM sms_formats WHERE sourceId = :sourceId AND isActive = 1")
    suspend fun getActiveFormatsForSource(sourceId: String): List<SmsFormatEntity>

    @Query("SELECT * FROM sms_formats WHERE isActive = 1")
    suspend fun getAllActiveFormats(): List<SmsFormatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFormat(format: SmsFormatEntity)

    @Update
    suspend fun updateFormat(format: SmsFormatEntity)

    // --- Raw SMS ---
    @Query("SELECT * FROM raw_sms ORDER BY receivedTimestamp DESC")
    fun getAllRawSms(): Flow<List<RawSmsEntity>>

    @Query("SELECT * FROM raw_sms WHERE rawSmsId = :rawSmsId LIMIT 1")
    suspend fun getRawSmsById(rawSmsId: String): RawSmsEntity?

    @Query("SELECT * FROM raw_sms WHERE bodyHash = :hash LIMIT 1")
    suspend fun findRawSmsByHash(hash: String): RawSmsEntity?

    /** Same SMS body seen close in time (inbox scan vs. live broadcast carry slightly different timestamps). */
    @Query("SELECT * FROM raw_sms WHERE bodyHash = :hash AND ABS(receivedTimestamp - :timestamp) <= :windowMillis LIMIT 1")
    suspend fun findRawSmsByHashNear(hash: String, timestamp: Long, windowMillis: Long): RawSmsEntity?

    @Query("SELECT bodyHash, receivedTimestamp FROM raw_sms WHERE phoneId = :phoneId")
    suspend fun getRawSmsFingerprints(phoneId: String): List<RawSmsFingerprint>

    @Query("SELECT * FROM raw_sms ORDER BY receivedTimestamp ASC")
    suspend fun getAllRawSmsOnce(): List<RawSmsEntity>

    @Query("SELECT * FROM raw_sms WHERE processingStatus IN ('UNKNOWN_FORMAT', 'FAILED') AND detectedSourceId IS NOT NULL")
    suspend fun getFailedOrUnknownRawSmsOnce(): List<RawSmsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawSmsList(rawSms: List<RawSmsEntity>)

    @Query("SELECT * FROM raw_sms WHERE processingStatus IN (:statuses) ORDER BY receivedTimestamp DESC")
    fun getFailedOrUnknownSms(statuses: List<SmsProcessingStatus>): Flow<List<RawSmsEntity>>

    @Query("SELECT * FROM raw_sms WHERE processingStatus = 'UNKNOWN_SENDER' ORDER BY receivedTimestamp DESC")
    fun getUnknownSenderSms(): Flow<List<RawSmsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawSms(rawSms: RawSmsEntity)

    @Update
    suspend fun updateRawSms(rawSms: RawSmsEntity)

    @Query("DELETE FROM raw_sms WHERE rawSmsId = :id")
    suspend fun deleteRawSms(id: String)

    // --- Transactions ---
    @Query("SELECT * FROM transactions ORDER BY createdTimestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE phoneId = :phoneId ORDER BY createdTimestamp DESC")
    fun getTransactionsByPhone(phoneId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE sourceId = :sourceId ORDER BY createdTimestamp DESC")
    fun getTransactionsBySource(sourceId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE phoneId = :phoneId AND sourceId = :sourceId ORDER BY createdTimestamp DESC")
    fun getTransactionsByPhoneAndSource(phoneId: String, sourceId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE referenceNumber = :refNumber LIMIT 1")
    suspend fun findTransactionByRef(refNumber: String): TransactionEntity?

    /** Duplicate check is scoped per phone + institution + direction so both legs of a self-transfer survive. */
    @Query(
        "SELECT * FROM transactions WHERE referenceNumber = :refNumber AND phoneId = :phoneId " +
            "AND sourceId = :sourceId AND direction = :direction AND isReconciliation = 0 LIMIT 1"
    )
    suspend fun findTransactionByRefScoped(refNumber: String, phoneId: String, sourceId: String, direction: String): TransactionEntity?

    @Query("SELECT referenceNumber, phoneId, sourceId, direction FROM transactions WHERE phoneId = :phoneId AND referenceNumber IS NOT NULL AND isReconciliation = 0")
    suspend fun getReferenceKeys(phoneId: String): List<TransactionRefKey>

    @Query("SELECT * FROM transactions WHERE rawSmsId = :rawSmsId LIMIT 1")
    suspend fun getTransactionByRawSmsId(rawSmsId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE phoneId = :phoneId AND financialAccountId = :accountId ORDER BY createdTimestamp ASC")
    suspend fun getAccountTransactions(phoneId: String, accountId: String): List<TransactionEntity>

    @Query("SELECT DISTINCT phoneId, financialAccountId FROM transactions")
    suspend fun getAllAccountKeys(): List<AccountKey>

    @Query("DELETE FROM transactions WHERE phoneId = :phoneId AND financialAccountId = :accountId AND isReconciliation = 1")
    suspend fun deleteReconciliationRows(phoneId: String, accountId: String)

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactionsOnce(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Query("SELECT * FROM transactions WHERE transactionId = :id LIMIT 1")
    suspend fun getTransactionById(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE transactionId = :id")
    suspend fun deleteTransaction(id: String)

    @Query("DELETE FROM transactions WHERE rawSmsId = :rawSmsId")
    suspend fun deleteTransactionByRawSmsId(rawSmsId: String)

    // --- Categories ---
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAllCategoriesOnce(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("UPDATE transactions SET categoryId = NULL, updatedAt = :now WHERE categoryId = :categoryId")
    suspend fun unassignCategoryFromTransactions(categoryId: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM categories WHERE isCustom = 0")
    suspend fun deleteDefaultCategories()

    // --- Income Sources ---
    @Query("SELECT * FROM income_sources ORDER BY name ASC")
    fun getAllIncomeSources(): Flow<List<IncomeSourceEntity>>

    @Query("SELECT * FROM income_sources")
    suspend fun getAllIncomeSourcesOnce(): List<IncomeSourceEntity>

    @Query("SELECT * FROM income_sources WHERE sourceId = :sourceId LIMIT 1")
    suspend fun getIncomeSourceById(sourceId: String): IncomeSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncomeSource(source: IncomeSourceEntity)

    @Update
    suspend fun updateIncomeSource(source: IncomeSourceEntity)

    @Delete
    suspend fun deleteIncomeSource(source: IncomeSourceEntity)

    @Query("DELETE FROM income_sources WHERE sourceId = :sourceId")
    suspend fun deleteIncomeSourceById(sourceId: String)

    // --- Phone Income Mapping & Auto-categorization ---
    @Query("UPDATE registered_phones SET assignedIncomeSourceId = :incomeSourceId WHERE phoneId = :phoneId")
    suspend fun updatePhoneIncomeSource(phoneId: String, incomeSourceId: String?)

    @Query("UPDATE transactions SET categoryId = :incomeSourceId, updatedAt = :now WHERE phoneId = :phoneId AND (direction = 'CREDIT' OR transactionType IN ('INCOME', 'DEPOSIT'))")
    suspend fun updateIncomesCategoryForPhone(phoneId: String, incomeSourceId: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET categoryId = NULL, updatedAt = :now WHERE phoneId = :phoneId AND (direction = 'CREDIT' OR transactionType IN ('INCOME', 'DEPOSIT'))")
    suspend fun clearIncomesCategoryForPhone(phoneId: String, now: Long = System.currentTimeMillis())

    /**
     * Every credit of a phone that has an income source belongs to that source, including credits
     * synced from other devices and balance-recovered credits. Returns the number of rows fixed.
     */
    @Query("""
        UPDATE transactions
        SET categoryId = (SELECT p.assignedIncomeSourceId FROM registered_phones p WHERE p.phoneId = transactions.phoneId)
        WHERE (direction = 'CREDIT' OR transactionType IN ('INCOME', 'DEPOSIT'))
          AND phoneId IN (SELECT phoneId FROM registered_phones WHERE assignedIncomeSourceId IS NOT NULL AND assignedIncomeSourceId != '')
          AND (categoryId IS NULL OR categoryId != (SELECT p2.assignedIncomeSourceId FROM registered_phones p2 WHERE p2.phoneId = transactions.phoneId))
    """)
    suspend fun applyAssignedIncomeSourcesToCredits(): Int

    // --- Uncategorized Expenses ---
    @Query("""
        SELECT * FROM transactions 
        WHERE (direction = 'DEBIT' OR transactionType IN ('EXPENSE', 'WITHDRAWAL', 'FEE', 'TRANSFER'))
          AND (categoryId IS NULL OR categoryId = '' OR categoryId = 'uncategorized')
        ORDER BY createdTimestamp DESC
    """)
    fun getUncategorizedExpenses(): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions 
        WHERE (direction = 'DEBIT' OR transactionType IN ('EXPENSE', 'WITHDRAWAL', 'FEE', 'TRANSFER'))
          AND (categoryId IS NULL OR categoryId = '' OR categoryId = 'uncategorized')
        ORDER BY createdTimestamp DESC
        LIMIT :limit
    """)
    suspend fun getUncategorizedExpensesOnce(limit: Int = 100): List<TransactionEntity>

    /** Debits the user already filed under a category: training data for the category learner. */
    @Query("""
        SELECT * FROM transactions
        WHERE (direction = 'DEBIT' OR transactionType IN ('EXPENSE', 'WITHDRAWAL', 'FEE', 'TRANSFER'))
          AND categoryId IS NOT NULL AND categoryId != '' AND categoryId != 'uncategorized' AND isReconciliation = 0
        ORDER BY createdTimestamp DESC
        LIMIT :limit
    """)
    suspend fun getCategorizedDebitsOnce(limit: Int = 3000): List<TransactionEntity>

    // --- Internal Transfer Configs ---
    @Query("SELECT * FROM internal_transfer_configs ORDER BY identityName ASC")
    fun getAllInternalTransferConfigs(): Flow<List<InternalTransferConfigEntity>>

    @Query("SELECT * FROM internal_transfer_configs WHERE isActive = 1")
    suspend fun getActiveInternalTransferConfigs(): List<InternalTransferConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternalTransferConfig(config: InternalTransferConfigEntity)

    @Update
    suspend fun updateInternalTransferConfig(config: InternalTransferConfigEntity)

    @Delete
    suspend fun deleteInternalTransferConfig(config: InternalTransferConfigEntity)

    // --- Savings Goals ---
    @Query("SELECT * FROM savings_goals ORDER BY createdAt DESC")
    fun getAllSavingsGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals")
    suspend fun getAllSavingsGoalsOnce(): List<SavingsGoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavingsGoal(goal: SavingsGoalEntity)

    @Update
    suspend fun updateSavingsGoal(goal: SavingsGoalEntity)

    @Delete
    suspend fun deleteSavingsGoal(goal: SavingsGoalEntity)

    // --- Manual Cash Sources ---
    @Query("SELECT * FROM manual_sources WHERE isDeleted = 0 ORDER BY createdAt ASC")
    fun getActiveManualSources(): Flow<List<ManualSourceEntity>>

    @Query("SELECT * FROM manual_sources")
    suspend fun getAllManualSourcesOnce(): List<ManualSourceEntity>

    @Query("SELECT * FROM manual_sources WHERE manualSourceId = :id LIMIT 1")
    suspend fun getManualSourceById(id: String): ManualSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertManualSource(source: ManualSourceEntity)

    // --- Net-worth ledger ---
    @Query("SELECT * FROM wealth_items WHERE isDeleted = 0 ORDER BY createdAt ASC")
    fun getActiveWealthItems(): Flow<List<WealthItemEntity>>

    @Query("SELECT * FROM wealth_items WHERE isDeleted = 0")
    suspend fun getActiveWealthItemsOnce(): List<WealthItemEntity>

    @Query("SELECT * FROM wealth_items WHERE itemId = :id LIMIT 1")
    suspend fun getWealthItemById(id: String): WealthItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWealthItem(item: WealthItemEntity)

    // --- App State (key/value) ---
    @Query("SELECT stateValue FROM app_state WHERE stateKey = :key LIMIT 1")
    suspend fun getState(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putState(state: AppStateEntity)

    // --- Device identity re-keying (legacy "DEVICE-001" -> unique per-install id) ---
    @Query("UPDATE transactions SET phoneId = :newId WHERE phoneId = :oldId")
    suspend fun rekeyTransactionsPhone(oldId: String, newId: String)

    @Query("UPDATE raw_sms SET phoneId = :newId WHERE phoneId = :oldId")
    suspend fun rekeyRawSmsPhone(oldId: String, newId: String)

    @Query("UPDATE manual_sources SET phoneId = :newId WHERE phoneId = :oldId")
    suspend fun rekeyManualSourcesPhone(oldId: String, newId: String)

    @Query("SELECT * FROM registered_phones")
    suspend fun getAllPhonesOnce(): List<RegisteredPhoneEntity>

    // --- Cloud sync outbox ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueOutbox(items: List<SyncOutboxEntity>)

    @Query("SELECT * FROM sync_outbox ORDER BY queuedAt ASC LIMIT :limit")
    suspend fun peekOutbox(limit: Int): List<SyncOutboxEntity>

    /** Only removes the entry if it was not queued again while it was being uploaded. */
    @Query("DELETE FROM sync_outbox WHERE itemKey = :itemKey AND queuedAt <= :queuedAt")
    suspend fun ackOutbox(itemKey: String, queuedAt: Long)

    @Query("DELETE FROM sync_outbox WHERE itemKey IN (:keys)")
    suspend fun ackOutboxBatch(keys: List<String>)

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun outboxCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun getOutboxCountOnce(): Int

    @Query("DELETE FROM sync_outbox")
    suspend fun clearOutbox()

    @Query("SELECT transactionId FROM transactions WHERE phoneId = :phoneId AND isReconciliation = 0")
    suspend fun getLocalTransactionIds(phoneId: String): List<String>

    @Query("SELECT DISTINCT financialAccountId FROM transactions WHERE phoneId = :phoneId")
    suspend fun getAccountIdsForPhone(phoneId: String): List<String>

    @Query("SELECT manualSourceId FROM manual_sources")
    suspend fun getManualSourceIds(): List<String>

    @Query("SELECT * FROM transactions WHERE transactionId IN (:ids)")
    suspend fun getTransactionsByIds(ids: List<String>): List<TransactionEntity>

    @Query("SELECT * FROM manual_sources WHERE manualSourceId IN (:ids)")
    suspend fun getManualSourcesByIds(ids: List<String>): List<ManualSourceEntity>

    @Query("DELETE FROM transactions WHERE transactionId IN (:ids)")
    suspend fun deleteTransactionsByIds(ids: List<String>)

    @Query("SELECT * FROM transactions WHERE phoneId = :phoneId AND financialAccountId = :accountId AND isReconciliation = 1")
    suspend fun getReconciliationRows(phoneId: String, accountId: String): List<TransactionEntity>

    // --- Audit Logs ---
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentAuditLogs(): Flow<List<AuditLogEntity>>

    @Insert
    suspend fun insertAuditLog(log: AuditLogEntity)
}

data class RawSmsFingerprint(
    val bodyHash: String,
    val receivedTimestamp: Long
)

data class TransactionRefKey(
    val referenceNumber: String,
    val phoneId: String,
    val sourceId: String,
    val direction: String
)

data class AccountKey(
    val phoneId: String,
    val financialAccountId: String
)
