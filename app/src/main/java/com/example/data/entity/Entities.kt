package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER,
    DEPOSIT,
    WITHDRAWAL,
    FEE,
    INTERNAL_TRANSFER,
    UNKNOWN
}

/** Money direction from the account holder's point of view. */
enum class FlowDirection {
    CREDIT,
    DEBIT,
    UNKNOWN
}

enum class ManualSourceKind(val label: String) {
    CASH("Cash in Hand"),
    SALARY("Salary & Wages"),
    BUSINESS("Business Revenue"),
    RENTAL("Rental Income"),
    INVESTMENT("Investment / Dividends"),
    WALLET("Digital Wallet"),
    BANK("Bank Account"),
    OTHER("Other Income")
}

enum class SmsProcessingStatus {
    RECEIVED,
    PROCESSING,
    PARSED,
    FAILED,
    UNKNOWN_FORMAT,
    UNKNOWN_SENDER,
    IGNORED,
    DUPLICATE
}

enum class VerificationStatus {
    VALID,
    WARNING,
    INVALID,
    NEEDS_REVIEW
}

enum class SourceType {
    BANK,
    MOBILE_MONEY,
    WALLET,
    OTHER
}

enum class SenderIdentifierType {
    NAME,
    PHONE_NUMBER,
    ALIAS
}

enum class MatchMethod {
    EXACT,
    CASE_INSENSITIVE,
    CONTAINS,
    REGEX
}

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String = "USER-001",
    val name: String = "Nahom Tadele",
    val email: String = "user@example.com",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "registered_phones")
data class RegisteredPhoneEntity(
    @PrimaryKey val phoneId: String, // e.g. "DEVICE-001"
    val userId: String = "USER-001",
    val friendlyName: String, // e.g. "Personal Phone"
    val modelName: String, // e.g. "Samsung Galaxy S24"
    val phoneNumber: String = "",
    val isCurrentDevice: Boolean = false,
    val registeredAt: Long = System.currentTimeMillis(),
    val assignedIncomeSourceId: String? = null
)

@Entity(tableName = "financial_sources")
data class FinancialSourceEntity(
    @PrimaryKey val sourceId: String, // e.g. "telebirr", "cbe", "cbe_birr", "boa"
    val name: String, // e.g. "Telebirr"
    val type: SourceType,
    val institutionName: String, // e.g. "Ethio Telecom"
    val isActive: Boolean = true,
    val senderIdentifiers: String = "", // fallback comma-separated string
    val colorHex: String = "#10B981",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sms_sender_identifiers")
data class SmsSenderIdentifierEntity(
    @PrimaryKey val id: String, // e.g. "SND-001"
    val sourceId: String, // References FinancialSourceEntity.sourceId
    val senderName: String? = null,
    val senderNumber: String? = null,
    val identifierType: SenderIdentifierType = SenderIdentifierType.NAME,
    val matchMethod: MatchMethod = MatchMethod.CASE_INSENSITIVE,
    val isActive: Boolean = true,
    val priority: Int = 1,
    val description: String = "",
    val createdBy: String = "ADMIN",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sms_formats")
data class SmsFormatEntity(
    @PrimaryKey val formatId: String, // e.g. "telebirr_recv_v1"
    val sourceId: String,
    val formatName: String,
    val version: Int = 1,
    val isActive: Boolean = true,
    val keywords: String, // comma-separated required keywords
    val regexPattern: String,
    val defaultTransactionType: TransactionType,
    val amountGroupIndex: Int = 1,
    val partyGroupIndex: Int = 2,
    val dateGroupIndex: Int = 3,
    val refGroupIndex: Int = 4,
    val balanceGroupIndex: Int = 5,
    val sampleSms: String = ""
)

@Entity(
    tableName = "raw_sms",
    indices = [Index(value = ["bodyHash"])]
)
data class RawSmsEntity(
    @PrimaryKey val rawSmsId: String,
    val userId: String = "USER-001",
    val phoneId: String,
    val sender: String,
    val body: String,
    val receivedTimestamp: Long,
    val deviceTimestamp: Long = receivedTimestamp,
    val processingStatus: SmsProcessingStatus,
    val detectedSourceId: String? = null,
    val detectedFormatId: String? = null,
    val processingError: String? = null,
    val processingAttempts: Int = 1,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val bodyHash: String
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["referenceNumber"]),
        Index(value = ["rawSmsId"]),
        Index(value = ["phoneId", "financialAccountId", "createdTimestamp"])
    ]
)
data class TransactionEntity(
    @PrimaryKey val transactionId: String,
    val userId: String = "USER-001",
    val phoneId: String,
    val financialAccountId: String,
    val sourceId: String,
    val rawSmsId: String,
    val transactionType: TransactionType,
    val amount: Double,
    val currency: String = "ETB",
    val transactionDate: String,
    val transactionTime: String,
    val sender: String? = null,
    val recipient: String? = null,
    val merchant: String? = null,
    val referenceNumber: String? = null,
    val balanceAfter: Double? = null,
    val fee: Double = 0.0,
    val description: String = "",
    val categoryId: String? = null,
    val subcategory: String? = null,
    val parsingConfidence: Float = 1.0f,
    val verificationStatus: VerificationStatus = VerificationStatus.VALID,
    val parserVersion: Int = 1,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val userNotes: String? = null,
    // Whether money came in (CREDIT) or went out (DEBIT) of the account, incl. internal transfers
    val direction: FlowDirection = FlowDirection.UNKNOWN,
    // True for synthetic rows created from a balance gap between two consecutive SMS
    val isReconciliation: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val categoryId: String,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val isCustom: Boolean = true,
    val isActive: Boolean = true,
    // What the user files under this expense type; given to the AI advisor as context
    val description: String = ""
)

@Entity(tableName = "income_sources")
data class IncomeSourceEntity(
    @PrimaryKey val sourceId: String,
    val name: String,
    val colorHex: String = "#10B981",
    val iconName: String = "storefront",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "internal_transfer_configs")
data class InternalTransferConfigEntity(
    @PrimaryKey val configId: String,
    val identityName: String, // e.g. "Nahom Telebirr"
    val identifierValue: String, // e.g. "0911223344" or "10002345678"
    val financialInstitution: String, // e.g. "Telebirr" or "CBE"
    val aliases: String = "", // comma-separated aliases
    val isActive: Boolean = true,
    val description: String = ""
)

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey val goalId: String,
    val userId: String = "USER-001",
    val goalName: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val targetDate: String,
    val startingAmount: Double = 0.0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val details: String,
    val phoneId: String? = null,
    val rawSmsId: String? = null
)

/**
 * A cash source the user tracks by hand (cash at home, a safe, a bank without SMS alerts...).
 * Rows are soft-deleted so removals propagate to paired devices during sync.
 */
@Entity(tableName = "manual_sources")
data class ManualSourceEntity(
    @PrimaryKey val manualSourceId: String,
    val name: String,
    val kind: ManualSourceKind = ManualSourceKind.CASH,
    val balance: Double = 0.0,
    val phoneId: String? = null, // optional owner device, used for the "By Phone" grouping
    val note: String = "",
    val colorHex: String = "#F59E0B",
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Kinds of things the user owns or owes beyond bank/wallet balances (net-worth ledger). */
enum class WealthKind(val label: String, val isLiability: Boolean, val quantityLabel: String) {
    EQUB("Equb (rotating savings)", false, "Paid in so far, not yet received (ETB)"),
    GOLD("Gold", false, "Weight (grams)"),
    SHARES("Shares", false, "Number of shares"),
    DEPOSIT("Fixed deposit / T-bill / investment", false, "Amount (ETB)"),
    RECEIVABLE("Money lent out / owed to me", false, "Amount still owed to me (ETB)"),
    INVENTORY("Business stock / inventory", false, "Value at cost (ETB)"),
    PROPERTY("Land, house or vehicle", false, "Current value (ETB)"),
    OTHER_ASSET("Other asset", false, "Value (ETB)"),
    LOAN("Loan I owe", true, "Amount still owed (ETB)"),
    PAYABLE("Unpaid bill / supplier credit", true, "Amount owed (ETB)")
}

/**
 * One line of the net-worth ledger: equb, gold, shares, loans, receivables, stock, property...
 * Soft-deleted so a removal can be undone. Local to this phone.
 */
@Entity(tableName = "wealth_items")
data class WealthItemEntity(
    @PrimaryKey val itemId: String,
    val kind: WealthKind,
    val name: String,
    // ETB amount, grams of gold or number of shares (see WealthKind.quantityLabel)
    val quantity: Double,
    // Price per gram/share; 0 = gold uses the market price, ETB items need none
    val unitPriceEtb: Double = 0.0,
    // Yearly interest/profit rate for loans, receivables and deposits
    val ratePct: Double = 0.0,
    val counterparty: String = "",
    // dd/MM/yyyy: equb payout, loan due date, deposit maturity
    val dueDate: String = "",
    // Equb contribution or loan instalment, every recurringEveryDays days
    val recurringAmountEtb: Double = 0.0,
    val recurringEveryDays: Int = 0,
    val notes: String = "",
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Small key/value table for app state (local device id, last inbox scan position...). */
@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val stateKey: String,
    val stateValue: String
)

/**
 * Local changes waiting to be uploaded to the cloud sync group. Rows are added in the same DB
 * transaction as the change itself, so nothing is lost if the app closes or there is no internet.
 */
@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
    @PrimaryKey val itemKey: String, // e.g. "TX:TXN-ABC", "RECON:DEV-1|cbe:0032"
    val kind: String,
    val refId: String,
    val queuedAt: Long = System.currentTimeMillis()
)
