package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.dao.AccountKey
import com.example.data.dao.FinancialDao
import com.example.data.database.AppDatabase
import com.example.data.entity.AppStateEntity
import com.example.data.entity.AuditLogEntity
import com.example.ai.CategoryLearner
import com.example.data.entity.CategoryEntity
import com.example.data.entity.FinancialSourceEntity
import com.example.data.entity.FlowDirection
import com.example.data.entity.InternalTransferConfigEntity
import com.example.data.entity.ManualSourceEntity
import com.example.data.entity.MatchMethod
import com.example.data.entity.RawSmsEntity
import com.example.data.entity.RegisteredPhoneEntity
import com.example.data.entity.SavingsGoalEntity
import com.example.data.entity.SenderIdentifierType
import com.example.data.entity.SmsFormatEntity
import com.example.data.entity.SmsProcessingStatus
import com.example.data.entity.SmsSenderIdentifierEntity
import com.example.data.entity.SourceType
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransactionType
import com.example.data.entity.UserEntity
import com.example.data.entity.VerificationStatus
import com.example.domain.BalanceReconciler
import com.example.parser.ParsedSmsResult
import com.example.parser.SmsParserEngine
import com.example.data.entity.SyncOutboxEntity
import com.example.sync.ReconSet
import com.example.sync.SyncCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.BirrFlowApplication
import com.example.data.entity.IncomeSourceEntity
import com.example.data.entity.WealthItemEntity
import com.example.service.CategorizeNotificationHelper
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed class IngestionResult {
    data class Success(val transaction: TransactionEntity, val isDuplicate: Boolean = false) : IngestionResult()
    data class Duplicate(val rawSmsId: String) : IngestionResult()
    data class UnknownSender(val rawSmsId: String, val sender: String) : IngestionResult()
    data class UnknownFormat(val rawSmsId: String, val reason: String) : IngestionResult()
    data class NeedsReview(val transaction: TransactionEntity, val warnings: List<String>) : IngestionResult()
    data class Failed(val rawSmsId: String, val error: String) : IngestionResult()
}

/** One bank SMS read from the inbox or a live broadcast. */
data class IncomingSms(
    val sender: String,
    val body: String,
    val receivedTimestamp: Long
)

data class BatchIngestionSummary(
    val processed: Int = 0,
    val newTransactions: Int = 0,
    val duplicates: Int = 0,
    val unknownFormats: Int = 0,
    val affectedAccounts: Set<AccountKey> = emptySet()
) {
    operator fun plus(o: BatchIngestionSummary) = BatchIngestionSummary(
        processed + o.processed,
        newTransactions + o.newTransactions,
        duplicates + o.duplicates,
        unknownFormats + o.unknownFormats,
        affectedAccounts + o.affectedAccounts
    )
}

data class SyncMergeSummary(
    val phones: Int = 0,
    val transactionsAdded: Int = 0,
    val transactionsUpdated: Int = 0,
    val manualSources: Int = 0
)

/** Records received from other phones of the same cloud sync group. */
data class RemoteChanges(
    val phones: List<RegisteredPhoneEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    /** Transaction id -> updatedAt of the deletion on the other phone. */
    val deletedTransactions: Map<String, Long> = emptyMap(),
    val manualSources: List<ManualSourceEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val reconSets: List<ReconSet> = emptyList()
) {
    val isEmpty: Boolean
        get() = phones.isEmpty() && transactions.isEmpty() && deletedTransactions.isEmpty() &&
            manualSources.isEmpty() && categories.isEmpty() && reconSets.isEmpty()
}

/** An SMS after the CPU-heavy work (hashing + parsing), which runs in parallel before the DB write. */
class PreparedSms(
    val msg: IncomingSms,
    val hash: String,
    val rawSmsId: String,
    val transactionId: String,
    val parsed: ParsedSmsResult
)

/**
 * Everything the parser needs, loaded once per batch instead of four queries per SMS
 * (the old per-message reload was the main reason a first scan of thousands of SMS was slow).
 */
class ParseContext(
    val sources: List<FinancialSourceEntity>,
    val identifiers: List<SmsSenderIdentifierEntity>,
    val formats: List<SmsFormatEntity>,
    val internalConfigs: List<InternalTransferConfigEntity>,
    val phoneIncomeSourceMap: Map<String, String> = emptyMap()
) {
    private val bankSourceIds = sources.filter { it.type == SourceType.BANK }.map { it.sourceId }.toSet()
    fun isBankAccount(sourceId: String) = sourceId in bankSourceIds
}

/** In-memory duplicate index for one phone, so a batch never queries the DB per message. */
class DedupeIndex(
    private val hashTimes: MutableMap<String, MutableList<Long>>,
    private val refKeys: MutableSet<String>
) {
    fun isDuplicateSms(hash: String, timestamp: Long): Boolean =
        hashTimes[hash]?.any { kotlin.math.abs(it - timestamp) <= FinancialRepository.DUPLICATE_WINDOW_MS } == true

    fun addSms(hash: String, timestamp: Long) {
        hashTimes.getOrPut(hash) { mutableListOf() }.add(timestamp)
    }

    fun isDuplicateRef(key: String) = key in refKeys
    fun addRef(key: String) {
        refKeys.add(key)
    }
}

class FinancialRepository(private val database: AppDatabase) {

    private val dao: FinancialDao = database.financialDao()

    // Serializes writers (inbox scan, live SMS, sync import) so reconciliation never sees half a batch
    private val writeMutex = Mutex()

    val allPhones: Flow<List<RegisteredPhoneEntity>> = dao.getAllPhones()
    val allSources: Flow<List<FinancialSourceEntity>> = dao.getAllSources()
    val allSenderIdentifiers: Flow<List<SmsSenderIdentifierEntity>> = dao.getAllSenderIdentifiers()
    val allFormats: Flow<List<SmsFormatEntity>> = dao.getAllFormats()
    val allTransactions: Flow<List<TransactionEntity>> = dao.getAllTransactions()
    val allRawSms: Flow<List<RawSmsEntity>> = dao.getAllRawSms()
    val allCategories: Flow<List<CategoryEntity>> = dao.getAllCategories()
    val allIncomeSources: Flow<List<IncomeSourceEntity>> = dao.getAllIncomeSources()
    val uncategorizedExpenses: Flow<List<TransactionEntity>> = dao.getUncategorizedExpenses()
    val allInternalConfigs: Flow<List<InternalTransferConfigEntity>> = dao.getAllInternalTransferConfigs()
    val allSavingsGoals: Flow<List<SavingsGoalEntity>> = dao.getAllSavingsGoals()
    val recentAuditLogs: Flow<List<AuditLogEntity>> = dao.getRecentAuditLogs()
    val unknownSenderSms: Flow<List<RawSmsEntity>> = dao.getUnknownSenderSms()
    val manualSources: Flow<List<ManualSourceEntity>> = dao.getActiveManualSources()
    val wealthItems: Flow<List<WealthItemEntity>> = dao.getActiveWealthItems()

    fun getFailedOrUnknownSms(): Flow<List<RawSmsEntity>> = dao.getFailedOrUnknownSms(
        listOf(
            SmsProcessingStatus.UNKNOWN_SENDER,
            SmsProcessingStatus.UNKNOWN_FORMAT,
            SmsProcessingStatus.FAILED,
            SmsProcessingStatus.DUPLICATE
        )
    )

    suspend fun getRawSmsById(id: String): RawSmsEntity? = dao.getRawSmsById(id)

    // ------------------------------------------------------------------
    // App state & device identity
    // ------------------------------------------------------------------

    @Volatile
    private var cachedDeviceId: String? = null

    suspend fun getState(key: String): String? = dao.getState(key)
    suspend fun putState(key: String, value: String) = dao.putState(AppStateEntity(key, value))

    /** Unique id of this install. Older builds used "DEVICE-001" on every phone, which breaks pairing. */
    suspend fun localDeviceId(): String {
        cachedDeviceId?.let { return it }
        val existing = dao.getState(KEY_DEVICE_ID)
        if (existing != null) {
            cachedDeviceId = existing
            return existing
        }
        val id = "DEV-" + UUID.randomUUID().toString().replace("-", "").take(12).uppercase(Locale.ROOT)
        dao.putState(AppStateEntity(KEY_DEVICE_ID, id))
        cachedDeviceId = id
        return id
    }

    /**
     * Startup housekeeping: moves legacy "DEVICE-001" rows to this install's unique id and makes sure
     * the reconciliation category exists. Safe to call on every launch.
     */
    suspend fun ensureLocalDeviceIdentity(): Unit = writeMutex.withLock {
        val id = localDeviceId()
        val existing = dao.getPhoneById(id)
        val model = android.os.Build.MODEL.ifBlank { "Android Phone" }
        if (existing == null) {
            val currentPhone = RegisteredPhoneEntity(
                phoneId = id,
                userId = "USER-001",
                friendlyName = "Primary Phone ($model)",
                modelName = model,
                phoneNumber = "",
                isCurrentDevice = true
            )
            database.withTransaction { dao.insertPhone(currentPhone) }
        } else if (!existing.isCurrentDevice) {
            database.withTransaction { dao.updatePhone(existing.copy(isCurrentDevice = true)) }
        }

        val legacy = dao.getPhoneById(LEGACY_DEVICE_ID)
        if (legacy != null) {
            database.withTransaction {
                dao.deletePhone(legacy)
                dao.rekeyTransactionsPhone(LEGACY_DEVICE_ID, id)
                dao.rekeyRawSmsPhone(LEGACY_DEVICE_ID, id)
                dao.rekeyManualSourcesPhone(LEGACY_DEVICE_ID, id)
            }
        }

        // Deduplicate devices on the phone: only one current device, and no duplicate devices
        val allPhones = dao.getAllPhonesOnce()
        for (p in allPhones) {
            if (p.phoneId != id && p.isCurrentDevice) {
                database.withTransaction { dao.updatePhone(p.copy(isCurrentDevice = false)) }
            }
        }
        val nonLocal = dao.getAllPhonesOnce().filter { it.phoneId != id }
        val seenNames = HashSet<String>()
        for (p in nonLocal) {
            val key = "${p.friendlyName.lowercase().trim()}|${p.modelName.lowercase().trim()}"
            if (!seenNames.add(key)) {
                database.withTransaction { dao.deletePhone(p) }
            }
        }

        if (dao.getActiveSources().isNotEmpty()) {
            val categories = dao.getAllCategoriesOnce()
            if (categories.none { it.categoryId == RECONCILED_CATEGORY.categoryId }) dao.insertCategory(RECONCILED_CATEGORY)
        }
        applyAssignedIncomeSources()
        Unit
    }

    /** Re-files every credit of an assigned phone under its income source (cheap no-op when already correct). */
    private suspend fun applyAssignedIncomeSources() {
        try {
            dao.applyAssignedIncomeSourcesToCredits()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    suspend fun loadParseContext(): ParseContext {
        val phoneIncomeMap = try {
            dao.getAllPhonesOnce()
                .mapNotNull { p -> p.assignedIncomeSourceId?.let { p.phoneId to it } }
                .toMap()
        } catch (_: Throwable) {
            emptyMap()
        }
        return ParseContext(
            sources = dao.getActiveSources(),
            identifiers = dao.getActiveSenderIdentifiers(),
            formats = dao.getAllActiveFormats(),
            internalConfigs = dao.getActiveInternalTransferConfigs(),
            phoneIncomeSourceMap = phoneIncomeMap
        )
    }

    suspend fun loadDedupeIndex(phoneId: String): DedupeIndex {
        val hashes = HashMap<String, MutableList<Long>>()
        dao.getRawSmsFingerprints(phoneId).forEach { hashes.getOrPut(it.bodyHash) { mutableListOf() }.add(it.receivedTimestamp) }
        val refs = dao.getReferenceKeys(phoneId).mapTo(HashSet()) { refKey(it.referenceNumber, it.sourceId, it.direction) }
        return DedupeIndex(hashes, refs)
    }

    // ------------------------------------------------------------------
    // Ingestion pipeline
    // ------------------------------------------------------------------

    /**
     * Single SMS (live broadcast, simulator). Uses the same pipeline as the batch scan and then
     * re-checks the balance chain of the affected account so the dashboard is correct immediately.
     */
    suspend fun processIncomingSms(
        phoneId: String,
        sender: String,
        body: String,
        receivedTimestamp: Long = System.currentTimeMillis()
    ): IngestionResult = writeMutex.withLock {
        val ctx = loadParseContext()
        val dedupe = loadDedupeIndex(phoneId)
        val results = mutableListOf<IngestionResult>()
        val prepared = prepare(phoneId, listOf(IncomingSms(sender, body, receivedTimestamp)), ctx)
        val summary = ingestBatchLocked(phoneId, prepared, ctx, dedupe, results)
        reconcileAccountsLocked(summary.affectedAccounts, ctx)
        results.firstOrNull() ?: IngestionResult.Failed("", "Nothing processed")
    }

    /**
     * Hashing + parsing, spread over all CPU cores. Pure (no DB access), so it runs outside the
     * write lock while the previous chunk is being written.
     */
    suspend fun prepare(phoneId: String, messages: List<IncomingSms>, ctx: ParseContext): List<PreparedSms> {
        if (messages.size < PARALLEL_THRESHOLD) return messages.map { prepareOne(phoneId, it, ctx) }
        val slice = (messages.size + CPU_COUNT - 1) / CPU_COUNT
        return coroutineScope {
            messages.chunked(slice)
                .map { part -> async(Dispatchers.Default) { part.map { prepareOne(phoneId, it, ctx) } } }
                .awaitAll()
                .flatten()
        }
    }

    private fun prepareOne(phoneId: String, msg: IncomingSms, ctx: ParseContext): PreparedSms {
        val ids = stableIds(phoneId, msg.sender, msg.body, msg.receivedTimestamp)
        val parsed = SmsParserEngine.parseSms(
            sender = msg.sender,
            body = msg.body,
            sources = ctx.sources,
            formats = ctx.formats,
            identifiers = ctx.identifiers,
            internalConfigs = ctx.internalConfigs,
            receivedTimestamp = msg.receivedTimestamp
        )
        return PreparedSms(msg, smsHash(phoneId, msg.sender, msg.body), ids.rawSmsId, ids.transactionId, parsed)
    }

    /** Batch ingestion used by the inbox scanner: one DB transaction per chunk. */
    suspend fun ingestBatch(
        phoneId: String,
        prepared: List<PreparedSms>,
        ctx: ParseContext,
        dedupe: DedupeIndex
    ): BatchIngestionSummary = writeMutex.withLock {
        ingestBatchLocked(phoneId, prepared, ctx, dedupe, null)
    }

    private suspend fun ingestBatchLocked(
        phoneId: String,
        messages: List<PreparedSms>,
        ctx: ParseContext,
        dedupe: DedupeIndex,
        results: MutableList<IngestionResult>?
    ): BatchIngestionSummary {
        val rawRows = ArrayList<RawSmsEntity>(messages.size)
        val txRows = ArrayList<TransactionEntity>(messages.size)
        val audit = ArrayList<AuditLogEntity>()
        var batchCategories: List<CategoryEntity>? = null
        var batchLearner: CategoryLearner? = null
        val affected = HashSet<AccountKey>()
        var duplicates = 0
        var unknown = 0

        for (item in messages) {
            val msg = item.msg
            val hash = item.hash
            val ids = StableIds(item.rawSmsId, item.transactionId)

            // 1. Same physical SMS already stored (inbox scan + live broadcast report slightly different times)
            if (dedupe.isDuplicateSms(hash, msg.receivedTimestamp)) {
                duplicates++
                results?.add(IngestionResult.Duplicate(ids.rawSmsId))
                continue
            }

            val parsed = item.parsed

            // 2. Non-bank SMS are never stored
            if (parsed.isUnknownSender) {
                results?.add(IngestionResult.UnknownSender(ids.rawSmsId, msg.sender))
                continue
            }
            dedupe.addSms(hash, msg.receivedTimestamp)

            val status = statusFor(parsed)
            val raw = RawSmsEntity(
                rawSmsId = ids.rawSmsId,
                userId = "USER-001",
                phoneId = phoneId,
                sender = msg.sender,
                body = msg.body,
                receivedTimestamp = msg.receivedTimestamp,
                processingStatus = status,
                detectedSourceId = parsed.detectedSourceId,
                detectedFormatId = parsed.detectedFormatId,
                processingError = parsed.validationErrors.takeIf { it.isNotEmpty() }?.joinToString("; "),
                processingAttempts = 1,
                bodyHash = hash
            )

            if (status == SmsProcessingStatus.UNKNOWN_FORMAT || status == SmsProcessingStatus.FAILED) {
                rawRows += raw
                unknown++
                // Unknown wording can still carry a balance -> the reconciler uses it as an anchor
                parsed.detectedSourceId?.let { src ->
                    affected += AccountKey(phoneId, SmsParserEngine.accountKeyForBody(src, msg.body, ctx.isBankAccount(src)))
                }
                results?.add(IngestionResult.UnknownFormat(ids.rawSmsId, parsed.validationErrors.firstOrNull() ?: "Format not recognized"))
                continue
            }

            val direction = resolveDirection(parsed)
            val sourceId = parsed.detectedSourceId ?: "unknown"

            // 3. Same bank reference on the same phone, institution and direction = the same transaction
            if (!parsed.referenceNumber.isNullOrEmpty()) {
                val key = refKey(parsed.referenceNumber, sourceId, direction.name)
                if (dedupe.isDuplicateRef(key)) {
                    rawRows += raw.copy(processingStatus = SmsProcessingStatus.DUPLICATE, processingError = "Duplicate reference number: ${parsed.referenceNumber}")
                    duplicates++
                    results?.add(IngestionResult.Duplicate(ids.rawSmsId))
                    continue
                }
                dedupe.addRef(key)
            }

            rawRows += raw
            var tx = buildTransaction(ids.transactionId, phoneId, ids.rawSmsId, msg.receivedTimestamp, parsed, direction, "USER-001", ctx)
            affected += AccountKey(phoneId, tx.financialAccountId)

            if (direction == FlowDirection.DEBIT && tx.categoryId.isNullOrBlank()) {
                // Learned once per batch from the debits the user already filed
                val categories = batchCategories ?: dao.getAllCategoriesOnce().also { batchCategories = it }
                val learner = batchLearner ?: CategoryLearner.train(dao.getCategorizedDebitsOnce(), categories).also { batchLearner = it }
                val certain = if (isAutoFileEnabled()) learner.certainCategory(tx) else null
                if (certain != null) {
                    tx = tx.copy(categoryId = certain)
                    audit += AuditLogEntity(
                        action = "AUTO_CATEGORIZED",
                        details = "Filed ${tx.transactionId} under $certain (same payee always filed there)",
                        phoneId = phoneId,
                        rawSmsId = tx.rawSmsId
                    )
                } else {
                    try {
                        val app = runCatching { BirrFlowApplication.instance }.getOrNull()
                        if (app != null) {
                            val srcName = ctx.sources.firstOrNull { it.sourceId == sourceId }?.name ?: sourceId
                            val phoneName = dao.getPhoneById(phoneId)?.friendlyName ?: phoneId
                            CategorizeNotificationHelper.showCategorizeNotification(
                                context = app,
                                transaction = tx,
                                categories = categories,
                                sourceName = srcName,
                                phoneName = phoneName,
                                suggestions = learner.suggest(tx)
                            )
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            }
            txRows += tx

            results?.add(
                if (parsed.verificationStatus == VerificationStatus.NEEDS_REVIEW || parsed.verificationStatus == VerificationStatus.WARNING) {
                    IngestionResult.NeedsReview(tx, parsed.validationErrors)
                } else {
                    IngestionResult.Success(tx)
                }
            )
        }

        if (txRows.isNotEmpty()) {
            audit += AuditLogEntity(
                action = "TRANSACTIONS_IMPORTED",
                details = "${txRows.size} transaction(s) imported on $phoneId (${txRows.groupingBy { it.sourceId }.eachCount().entries.joinToString { "${it.key}: ${it.value}" }})",
                phoneId = phoneId
            )
        }
        if (unknown > 0) {
            audit += AuditLogEntity(action = "UNKNOWN_FORMAT_DETECTED", details = "$unknown bank SMS with unrecognized wording kept for review", phoneId = phoneId)
        }

        if (rawRows.isNotEmpty() || audit.isNotEmpty()) {
            database.withTransaction {
                if (rawRows.isNotEmpty()) dao.insertRawSmsList(rawRows)
                if (txRows.isNotEmpty()) dao.insertTransactions(txRows)
                audit.forEach { dao.insertAuditLog(it) }
                enqueue(txRows.map { outboxItem(KIND_TX, it.transactionId) })
            }
        }

        return BatchIngestionSummary(
            processed = messages.size,
            newTransactions = txRows.size,
            duplicates = duplicates,
            unknownFormats = unknown,
            affectedAccounts = affected
        )
    }

    private fun statusFor(parsed: ParsedSmsResult): SmsProcessingStatus = when {
        parsed.isUnknownSender -> SmsProcessingStatus.UNKNOWN_SENDER
        parsed.amount == null || parsed.amount <= 0 -> SmsProcessingStatus.UNKNOWN_FORMAT
        parsed.verificationStatus == VerificationStatus.INVALID -> SmsProcessingStatus.FAILED
        else -> SmsProcessingStatus.PARSED
    }

    private fun resolveDirection(parsed: ParsedSmsResult): FlowDirection = when {
        parsed.direction != FlowDirection.UNKNOWN -> parsed.direction
        parsed.transactionType == TransactionType.INCOME || parsed.transactionType == TransactionType.DEPOSIT -> FlowDirection.CREDIT
        parsed.transactionType == TransactionType.EXPENSE || parsed.transactionType == TransactionType.WITHDRAWAL ||
            parsed.transactionType == TransactionType.FEE -> FlowDirection.DEBIT
        else -> FlowDirection.UNKNOWN
    }

    private fun buildTransaction(
        transactionId: String,
        phoneId: String,
        rawSmsId: String,
        receivedTimestamp: Long,
        parsed: ParsedSmsResult,
        direction: FlowDirection,
        userId: String,
        ctx: ParseContext,
        keep: TransactionEntity? = null
    ): TransactionEntity {
        val isCredit = direction == FlowDirection.CREDIT || parsed.transactionType == TransactionType.INCOME || parsed.transactionType == TransactionType.DEPOSIT
        val defaultCategoryId = if (isCredit) {
            // Incomes are automatically categorized by the phone's assigned income source
            ctx.phoneIncomeSourceMap[phoneId]
        } else {
            // Debits are manual and NOT auto-assigned to default spending categories!
            null
        }
        val parsedTs = SmsParserEngine.parseTimestampFromDateTime(parsed.transactionDate, parsed.transactionTime)
        val effectiveTimestamp = parsedTs ?: receivedTimestamp
        val date = Date(effectiveTimestamp)
        val effectiveDate = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(date)
        val effectiveTime = SimpleDateFormat("HH:mm:ss", Locale.US).format(date)
        val sourceId = parsed.detectedSourceId ?: "unknown"
        return TransactionEntity(
            transactionId = transactionId,
            userId = userId,
            phoneId = phoneId,
            financialAccountId = parsed.accountKey ?: sourceId,
            sourceId = sourceId,
            rawSmsId = rawSmsId,
            transactionType = parsed.transactionType,
            amount = parsed.amount ?: 0.0,
            currency = parsed.currency,
            transactionDate = effectiveDate,
            transactionTime = effectiveTime,
            sender = parsed.sender,
            recipient = parsed.recipient,
            merchant = parsed.merchant,
            referenceNumber = parsed.referenceNumber,
            balanceAfter = parsed.balanceAfter,
            fee = parsed.fee,
            description = parsed.description,
            // A category or note the user picked by hand survives re-parsing
            categoryId = keep?.categoryId ?: defaultCategoryId,
            parsingConfidence = parsed.confidence,
            verificationStatus = parsed.verificationStatus,
            parserVersion = parsed.parserVersion,
            createdTimestamp = effectiveTimestamp,
            userNotes = keep?.userNotes,
            direction = direction,
            isReconciliation = false,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Reprocess a previously stored raw SMS (e.g. after adding sender identifier or updating format).
     * The transaction keeps its id, category and notes.
     */
    suspend fun reprocessRawSms(rawSmsId: String): IngestionResult = writeMutex.withLock {
        val ctx = loadParseContext()
        val (result, affected) = reprocessLocked(rawSmsId, ctx)
        reconcileAccountsLocked(affected, ctx)
        result
    }

    private suspend fun reprocessLocked(rawSmsId: String, ctx: ParseContext): Pair<IngestionResult, Set<AccountKey>> {
        val raw = dao.getRawSmsById(rawSmsId) ?: return IngestionResult.Failed(rawSmsId, "Raw SMS not found") to emptySet()
        val previous = dao.getTransactionByRawSmsId(rawSmsId)
        val affected = HashSet<AccountKey>()
        previous?.let { affected += AccountKey(it.phoneId, it.financialAccountId) }

        val parsed = SmsParserEngine.parseSms(
            sender = raw.sender,
            body = raw.body,
            sources = ctx.sources,
            formats = ctx.formats,
            identifiers = ctx.identifiers,
            internalConfigs = ctx.internalConfigs,
            receivedTimestamp = raw.receivedTimestamp
        )
        val newStatus = statusFor(parsed)
        val updatedRaw = raw.copy(
            processingStatus = newStatus,
            detectedSourceId = parsed.detectedSourceId,
            detectedFormatId = parsed.detectedFormatId,
            processingError = parsed.validationErrors.takeIf { it.isNotEmpty() }?.joinToString("; "),
            processingAttempts = raw.processingAttempts + 1,
            bodyHash = smsHash(raw.phoneId, raw.sender, raw.body)
        )

        if (parsed.isUnknownSender) {
            // No longer a configured bank sender: forget the SMS entirely
            database.withTransaction {
                dao.deleteTransactionByRawSmsId(rawSmsId)
                // Tells other phones to drop it too (a missing row is uploaded as a deletion)
                previous?.let { enqueue(listOf(outboxItem(KIND_TX, it.transactionId))) }
                dao.deleteRawSms(rawSmsId)
            }
            return IngestionResult.UnknownSender(rawSmsId, raw.sender) to affected
        }
        if (newStatus == SmsProcessingStatus.UNKNOWN_FORMAT || newStatus == SmsProcessingStatus.FAILED) {
            database.withTransaction {
                dao.deleteTransactionByRawSmsId(rawSmsId)
                // Tells other phones to drop it too (a missing row is uploaded as a deletion)
                previous?.let { enqueue(listOf(outboxItem(KIND_TX, it.transactionId))) }
                dao.updateRawSms(updatedRaw)
            }
            parsed.detectedSourceId?.let { src ->
                affected += AccountKey(raw.phoneId, SmsParserEngine.accountKeyForBody(src, raw.body, ctx.isBankAccount(src)))
            }
            return IngestionResult.UnknownFormat(rawSmsId, parsed.validationErrors.firstOrNull() ?: "Reprocess failed to match format") to affected
        }

        val direction = resolveDirection(parsed)
        val txId = previous?.transactionId ?: stableIds(raw.phoneId, raw.sender, raw.body, raw.receivedTimestamp).transactionId
        val tx = buildTransaction(txId, raw.phoneId, rawSmsId, raw.receivedTimestamp, parsed, direction, raw.userId, ctx, keep = previous)

        // Another stored SMS already produced this bank reference -> keep this one as a duplicate
        val dupe = parsed.referenceNumber?.takeIf { it.isNotEmpty() }?.let {
            dao.findTransactionByRefScoped(it, raw.phoneId, tx.sourceId, direction.name)
        }?.takeIf { it.rawSmsId != rawSmsId }
        if (dupe != null) {
            database.withTransaction {
                dao.deleteTransactionByRawSmsId(rawSmsId)
                // Tells other phones to drop it too (a missing row is uploaded as a deletion)
                previous?.let { enqueue(listOf(outboxItem(KIND_TX, it.transactionId))) }
                dao.updateRawSms(updatedRaw.copy(processingStatus = SmsProcessingStatus.DUPLICATE, processingError = "Duplicate reference number: ${parsed.referenceNumber}"))
            }
            return IngestionResult.Duplicate(rawSmsId) to affected
        }

        database.withTransaction {
            dao.deleteTransactionByRawSmsId(rawSmsId)
            dao.updateRawSms(updatedRaw)
            dao.insertTransaction(tx)
            enqueue(listOf(outboxItem(KIND_TX, tx.transactionId)))
        }
        affected += AccountKey(tx.phoneId, tx.financialAccountId)
        return IngestionResult.Success(tx) to affected
    }

    /**
     * Parser rules changed (new app version): re-parse every stored bank SMS with the current rules,
     * keeping transaction ids, categories and notes, then rebuild every balance chain.
     */
    suspend fun rebuildFromStoredSmsIfParserChanged(): Boolean {
        val stored = dao.getState(KEY_PARSER_RULES_VERSION)?.toIntOrNull() ?: 0
        if (stored >= PARSER_RULES_VERSION) return false
        writeMutex.withLock {
            val ctx = loadParseContext()
            val raws = dao.getAllRawSmsOnce()
            val existing = dao.getAllTransactionsOnce().filter { !it.isReconciliation }.associateBy { it.rawSmsId }
            val affected = HashSet<AccountKey>(dao.getAllAccountKeys())

            // Parse everything in parallel, then write it all in ONE transaction. The old one-SMS-at-a-time
            // rebuild committed (and refreshed the dashboard) thousands of times on the first launch.
            val parsed = coroutineScope {
                raws.chunked(((raws.size + CPU_COUNT - 1) / CPU_COUNT).coerceAtLeast(1))
                    .map { part ->
                        async(Dispatchers.Default) {
                            part.map { raw ->
                                raw to SmsParserEngine.parseSms(raw.sender, raw.body, ctx.sources, ctx.formats, ctx.identifiers, ctx.internalConfigs, raw.receivedTimestamp)
                            }
                        }
                    }
                    .awaitAll()
                    .flatten()
            }

            val newRaw = ArrayList<RawSmsEntity>(raws.size)
            val newTx = ArrayList<TransactionEntity>(raws.size)
            val removedTxIds = ArrayList<String>()
            val seenRefs = HashSet<String>()
            for ((raw, p) in parsed) {
                val previous = existing[raw.rawSmsId]
                val status = statusFor(p)
                var updated = raw.copy(
                    processingStatus = status,
                    detectedSourceId = p.detectedSourceId,
                    detectedFormatId = p.detectedFormatId,
                    processingError = p.validationErrors.takeIf { it.isNotEmpty() }?.joinToString("; "),
                    processingAttempts = raw.processingAttempts + 1,
                    bodyHash = smsHash(raw.phoneId, raw.sender, raw.body)
                )
                if (status != SmsProcessingStatus.PARSED) {
                    previous?.let { removedTxIds += it.transactionId }
                    if (status != SmsProcessingStatus.UNKNOWN_SENDER) newRaw += updated
                    p.detectedSourceId?.let { src -> affected += AccountKey(raw.phoneId, SmsParserEngine.accountKeyForBody(src, raw.body, ctx.isBankAccount(src))) }
                    continue
                }
                val direction = resolveDirection(p)
                val ref = p.referenceNumber?.takeIf { it.isNotEmpty() }
                if (ref != null && !seenRefs.add("${raw.phoneId}|" + refKey(ref, p.detectedSourceId ?: "unknown", direction.name))) {
                    previous?.let { removedTxIds += it.transactionId }
                    updated = updated.copy(processingStatus = SmsProcessingStatus.DUPLICATE, processingError = "Duplicate reference number: $ref")
                    newRaw += updated
                    continue
                }
                val txId = previous?.transactionId ?: stableIds(raw.phoneId, raw.sender, raw.body, raw.receivedTimestamp).transactionId
                val tx = buildTransaction(txId, raw.phoneId, raw.rawSmsId, raw.receivedTimestamp, p, direction, raw.userId, ctx, keep = previous)
                newRaw += updated
                newTx += tx
                affected += AccountKey(tx.phoneId, tx.financialAccountId)
            }
            val unknownSenderIds = parsed.filter { it.second.isUnknownSender }.map { it.first.rawSmsId }

            database.withTransaction {
                removedTxIds.chunked(500).forEach { dao.deleteTransactionsByIds(it) }
                unknownSenderIds.forEach { dao.deleteRawSms(it) }
                newRaw.chunked(500).forEach { dao.insertRawSmsList(it) }
                newTx.chunked(500).forEach { dao.insertTransactions(it) }
                enqueue((newTx.map { it.transactionId } + removedTxIds).map { outboxItem(KIND_TX, it) })
                dao.putState(AppStateEntity(KEY_PARSER_RULES_VERSION, PARSER_RULES_VERSION.toString()))
                // Old builds skipped SMS they could not match; scan the whole inbox once more.
                dao.putState(AppStateEntity(KEY_LAST_SCAN_TIMESTAMP, "0"))
                dao.insertAuditLog(AuditLogEntity(action = "PARSER_UPGRADE_REBUILD", details = "Re-parsed ${raws.size} stored bank SMS with parser rules v$PARSER_RULES_VERSION"))
            }
            reconcileAccountsLocked(affected, ctx)
        }
        return true
    }

    // ------------------------------------------------------------------
    // Balance reconciliation
    // ------------------------------------------------------------------

    suspend fun reconcileAccounts(keys: Set<AccountKey>): Unit = writeMutex.withLock {
        reconcileAccountsLocked(keys, loadParseContext())
    }

    private suspend fun reconcileAccountsLocked(keys: Set<AccountKey>, ctx: ParseContext) {
        if (keys.isEmpty()) return
        val localId = localDeviceId()
        val unknownRaw = dao.getFailedOrUnknownRawSmsOnce()
        for (key in keys) {
            // Peer devices reconcile their own accounts (they hold the raw SMS); we keep their rows as synced
            if (key.phoneId != localId && dao.getPhoneById(key.phoneId)?.isCurrentDevice != true) continue

            val sourceId = key.financialAccountId.substringBefore(':')
            val txs = dao.getAccountTransactions(key.phoneId, key.financialAccountId).filter { !it.isReconciliation }
            val anchors = unknownRaw.asSequence()
                .filter { it.phoneId == key.phoneId && it.detectedSourceId == sourceId }
                .filter { SmsParserEngine.accountKeyForBody(sourceId, it.body, ctx.isBankAccount(sourceId)) == key.financialAccountId }
                .mapNotNull { raw -> SmsParserEngine.extractBalance(raw.body)?.let { BalanceReconciler.Anchor(raw.rawSmsId, raw.receivedTimestamp, it) } }
                .toList()

            val gaps = BalanceReconciler.findGaps(
                txs.map { BalanceReconciler.Entry(it.transactionId, it.createdTimestamp, it.direction, it.amount, it.fee, it.balanceAfter) },
                anchors
            )
            val existing = dao.getAccountTransactions(key.phoneId, key.financialAccountId).filter { it.isReconciliation }.associateBy { it.transactionId }
            val rows = gaps.map { gap -> reconciliationRow(key, sourceId, gap, existing) }

            val changed = existing.keys != rows.map { it.transactionId }.toSet() ||
                rows.any { r -> existing[r.transactionId]?.let { it.amount != r.amount || it.direction != r.direction || it.balanceAfter != r.balanceAfter } ?: true }
            database.withTransaction {
                dao.deleteReconciliationRows(key.phoneId, key.financialAccountId)
                if (rows.isNotEmpty()) dao.insertTransactions(rows)
                if (changed) enqueue(listOf(outboxItem(KIND_RECON, "${key.phoneId}|${key.financialAccountId}")))
            }
        }
        // Recovered credits are created with the "Recovered from balance" category; file them under the phone's income source
        applyAssignedIncomeSources()
    }

    private fun reconciliationRow(
        key: AccountKey,
        sourceId: String,
        gap: BalanceReconciler.Gap,
        existing: Map<String, TransactionEntity>
    ): TransactionEntity {
        val id = "RECON-" + hashString("${key.phoneId}|${key.financialAccountId}|${gap.beforeEventId}").take(16).uppercase(Locale.ROOT)
        val previous = existing[id]
        val ts = if (gap.unparsedRawSmsId != null) gap.toTimestamp else gap.toTimestamp - 1
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.US)
        val word = if (gap.direction == FlowDirection.CREDIT) "credit" else "debit"
        val window = if (gap.fromTimestamp > 0) "between ${fmt.format(Date(gap.fromTimestamp))} and ${fmt.format(Date(gap.toTimestamp))}" else "before ${fmt.format(Date(gap.toTimestamp))}"
        val description = if (gap.unparsedRawSmsId != null) {
            "Unrecognized SMS: $word derived from balance change"
        } else {
            "Missing $word detected from balance change $window"
        }
        val sameContent = previous != null && previous.amount == gap.amount && previous.direction == gap.direction
        return TransactionEntity(
            transactionId = id,
            userId = "USER-001",
            phoneId = key.phoneId,
            financialAccountId = key.financialAccountId,
            sourceId = sourceId,
            rawSmsId = gap.unparsedRawSmsId ?: id,
            transactionType = if (gap.direction == FlowDirection.CREDIT) TransactionType.INCOME else TransactionType.EXPENSE,
            amount = gap.amount,
            transactionDate = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(ts)),
            transactionTime = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ts)),
            balanceAfter = gap.balanceAfter,
            description = description,
            categoryId = previous?.categoryId ?: RECONCILED_CATEGORY.categoryId,
            parsingConfidence = 1.0f,
            verificationStatus = VerificationStatus.NEEDS_REVIEW,
            parserVersion = PARSER_RULES_VERSION,
            createdTimestamp = ts,
            userNotes = previous?.userNotes,
            direction = gap.direction,
            isReconciliation = true,
            updatedAt = if (sameContent) previous!!.updatedAt else System.currentTimeMillis()
        )
    }

    // ------------------------------------------------------------------
    // Manual cash sources
    // ------------------------------------------------------------------

    // Net-worth ledger (local to this phone)
    suspend fun upsertWealthItem(item: WealthItemEntity) = dao.upsertWealthItem(item.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteWealthItem(itemId: String) {
        val item = dao.getWealthItemById(itemId) ?: return
        dao.upsertWealthItem(item.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
    }
    suspend fun getWealthItemsOnce(): List<WealthItemEntity> = dao.getActiveWealthItemsOnce()

    // One-shot reads for background work (weekly briefing) that runs without the UI
    suspend fun getAllTransactionsOnce(): List<TransactionEntity> = dao.getAllTransactionsOnce()
    suspend fun getActiveManualSourcesOnce(): List<ManualSourceEntity> = dao.getAllManualSourcesOnce().filter { !it.isDeleted }
    suspend fun getSavingsGoalsOnce(): List<SavingsGoalEntity> = dao.getAllSavingsGoalsOnce()

    suspend fun upsertManualSource(source: ManualSourceEntity) {
        database.withTransaction {
            dao.upsertManualSource(source.copy(updatedAt = System.currentTimeMillis()))
            enqueue(listOf(outboxItem(KIND_MANUAL, source.manualSourceId)))
        }
        dao.insertAuditLog(AuditLogEntity(action = "MANUAL_SOURCE_SAVED", details = "${source.name}: ${source.balance} ETB"))
    }

    /** Soft delete so the removal also reaches paired devices. */
    suspend fun deleteManualSource(id: String) {
        val existing = dao.getManualSourceById(id) ?: return
        database.withTransaction {
            dao.upsertManualSource(existing.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
            enqueue(listOf(outboxItem(KIND_MANUAL, id)))
        }
        dao.insertAuditLog(AuditLogEntity(action = "MANUAL_SOURCE_REMOVED", details = existing.name))
    }

    // ------------------------------------------------------------------
    // Cloud sync (outbox of local changes + applying changes from other phones)
    // ------------------------------------------------------------------

    @Volatile
    private var cloudEnabledCache: Boolean? = null

    private val _outboxSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Emits whenever something was queued for upload. */
    val outboxSignal: SharedFlow<Unit> = _outboxSignal.asSharedFlow()
    val pendingUploads: Flow<Int> = dao.outboxCount()

    suspend fun getOutboxCount(): Int = dao.getOutboxCountOnce()
    suspend fun peekOutbox(limit: Int): List<SyncOutboxEntity> = dao.peekOutbox(limit)

    private suspend fun isCloudEnabled(): Boolean =
        cloudEnabledCache ?: (dao.getState(KEY_CLOUD_ENABLED) == "1").also { cloudEnabledCache = it }

    private fun outboxItem(kind: String, refId: String) = SyncOutboxEntity("$kind:$refId", kind, refId, System.currentTimeMillis())

    /** Queues changes for upload; no-op while this phone is not linked to a sync group. */
    private suspend fun enqueue(items: List<SyncOutboxEntity>) {
        if (items.isEmpty() || !isCloudEnabled()) return
        items.chunked(500).forEach { dao.enqueueOutbox(it) }
        _outboxSignal.tryEmit(Unit)
    }

    /**
     * Turning sync on queues local items for the initial upload if not already populated.
     * Subsequent reconnects or app restarts will only upload NEW transactions that arrive.
     */
    suspend fun setCloudSyncEnabled(enabled: Boolean) = writeMutex.withLock {
        dao.putState(AppStateEntity(KEY_CLOUD_ENABLED, if (enabled) "1" else "0"))
        cloudEnabledCache = enabled
        if (!enabled) {
            dao.clearOutbox()
            return@withLock
        }
        val alreadyPopulated = dao.getState("INITIAL_OUTBOX_POPULATED") == "1"
        if (alreadyPopulated) {
            // Outbox already initialized; only new items will be enqueued automatically
            return@withLock
        }
        dao.putState(AppStateEntity("INITIAL_OUTBOX_POPULATED", "1"))
        val localId = localDeviceId()
        val items = ArrayList<SyncOutboxEntity>()
        items += outboxItem(KIND_PHONE, localId)
        dao.getLocalTransactionIds(localId).forEach { items += outboxItem(KIND_TX, it) }
        dao.getAccountIdsForPhone(localId).forEach { items += outboxItem(KIND_RECON, "$localId|$it") }
        dao.getManualSourceIds().forEach { items += outboxItem(KIND_MANUAL, it) }
        dao.getAllCategoriesOnce().filter { it.isCustom }.forEach { items += outboxItem(KIND_CATEGORY, it.categoryId) }
        database.withTransaction { enqueue(items) }
    }

    suspend fun getSyncedDeviceIds(): Set<String> {
        val raw = dao.getState("ACTIVE_SYNCED_DEVICE_IDS") ?: return emptySet()
        return raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    suspend fun setSyncedDeviceId(deviceId: String, sync: Boolean): Set<String> = writeMutex.withLock {
        val current = (dao.getState("ACTIVE_SYNCED_DEVICE_IDS") ?: "").split(",").filter { it.isNotBlank() }.toMutableSet()
        if (sync) current += deviceId else current -= deviceId
        val updated = current.joinToString(",")
        dao.putState(AppStateEntity("ACTIVE_SYNCED_DEVICE_IDS", updated))
        current
    }
    suspend fun ackOutbox(items: List<SyncOutboxEntity>) {
        if (items.isEmpty()) return
        items.chunked(500).forEach { chunk ->
            dao.ackOutboxBatch(chunk.map { it.itemKey })
        }
    }
    suspend fun transactionsByIds(ids: List<String>): List<TransactionEntity> = ids.chunked(500).flatMap { dao.getTransactionsByIds(it) }
    suspend fun manualSourcesByIds(ids: List<String>): List<ManualSourceEntity> = ids.chunked(500).flatMap { dao.getManualSourcesByIds(it) }
    suspend fun reconSet(phoneId: String, accountId: String) = ReconSet(phoneId, accountId, dao.getReconciliationRows(phoneId, accountId))
    suspend fun phoneById(id: String): RegisteredPhoneEntity? = dao.getPhoneById(id)
    suspend fun categoryById(id: String): CategoryEntity? = dao.getAllCategoriesOnce().firstOrNull { it.categoryId == id }

    /**
     * Applies changes from other phones. Newest edit wins per row (updatedAt); this phone's own
     * profile and reconciliations are never overwritten. Nothing here is queued for upload again.
     */
    suspend fun applyRemoteChanges(changes: RemoteChanges): SyncMergeSummary = writeMutex.withLock {
        if (changes.isEmpty) return@withLock SyncMergeSummary()
        val localId = localDeviceId()
        var phones = 0
        var added = 0
        var updated = 0
        var manual = 0

        database.withTransaction {
            val knownPhones = dao.getAllPhonesOnce().associateBy { it.phoneId }
            for (p in changes.phones) {
                if (p.phoneId == localId || p.phoneId == LEGACY_DEVICE_ID) continue
                // A phone never learns the income source another device assigned to it, so its own
                // upload carries none; keep the assignment made here instead of wiping it.
                val assignedSource = p.assignedIncomeSourceId ?: knownPhones[p.phoneId]?.assignedIncomeSourceId
                dao.insertPhone(p.copy(isCurrentDevice = false, assignedIncomeSourceId = assignedSource))
                phones++
            }

            val localCategories = dao.getAllCategoriesOnce().associateBy { it.categoryId }
            changes.categories.filter { it.categoryId !in localCategories }.forEach { dao.insertCategory(it) }
            // Fill in a description written on another phone when this one has none yet
            changes.categories.forEach { remote ->
                val mine = localCategories[remote.categoryId] ?: return@forEach
                if (mine.description.isBlank() && remote.description.isNotBlank()) dao.updateCategory(mine.copy(description = remote.description))
            }

            val incoming = changes.transactions.filter { !it.isReconciliation && it.phoneId != LEGACY_DEVICE_ID }
            val existingPhoneIds = dao.getAllPhonesOnce().map { it.phoneId }.toMutableSet()
            for (t in incoming) {
                if (t.phoneId != localId && t.phoneId != LEGACY_DEVICE_ID && t.phoneId !in existingPhoneIds) {
                    val fallbackPhone = RegisteredPhoneEntity(
                        phoneId = t.phoneId,
                        userId = "USER-001",
                        friendlyName = "Synced Phone (${t.phoneId.takeLast(6)})",
                        modelName = "Synced Device",
                        phoneNumber = "",
                        isCurrentDevice = false
                    )
                    dao.insertPhone(fallbackPhone)
                    existingPhoneIds += t.phoneId
                    phones++
                }
            }
            val local = incoming.map { it.transactionId }.chunked(500).flatMap { dao.getTransactionsByIds(it) }.associateBy { it.transactionId }
            val toWrite = ArrayList<TransactionEntity>()
            for (t in incoming) {
                val mine = local[t.transactionId]
                when {
                    mine == null -> { toWrite += t; added++ }
                    t.updatedAt > mine.updatedAt -> { toWrite += t; updated++ }
                }
            }
            toWrite.chunked(500).forEach { dao.insertTransactions(it) }

            if (changes.deletedTransactions.isNotEmpty()) {
                val present = changes.deletedTransactions.keys.toList().chunked(500).flatMap { dao.getTransactionsByIds(it) }
                val gone = present.filter { it.updatedAt <= (changes.deletedTransactions[it.transactionId] ?: 0L) }.map { it.transactionId }
                gone.chunked(500).forEach { dao.deleteTransactionsByIds(it) }
            }

            for (set in changes.reconSets) {
                if (set.phoneId == localId) continue // we compute our own
                dao.deleteReconciliationRows(set.phoneId, set.accountId)
                if (set.rows.isNotEmpty()) dao.insertTransactions(set.rows.map { it.copy(isReconciliation = true) })
            }

            val localManual = changes.manualSources.map { it.manualSourceId }.chunked(500)
                .flatMap { dao.getManualSourcesByIds(it) }.associateBy { it.manualSourceId }
            for (m in changes.manualSources) {
                val mine = localManual[m.manualSourceId]
                if (mine == null || m.updatedAt > mine.updatedAt) {
                    dao.upsertManualSource(m)
                    manual++
                }
            }

            // Credits synced from other phones arrive without this device's income source assignment
            dao.applyAssignedIncomeSourcesToCredits()
        }
        SyncMergeSummary(phones, added, updated, manual)
    }

    // Sender Identifier Management
    suspend fun insertSenderIdentifier(identifier: SmsSenderIdentifierEntity) {
        dao.insertSenderIdentifier(identifier)
        dao.insertAuditLog(
            AuditLogEntity(
                action = "SENDER_IDENTIFIER_CREATED",
                details = "Added sender identifier '${identifier.senderName ?: identifier.senderNumber}' for ${identifier.sourceId} (Priority ${identifier.priority})"
            )
        )
    }

    suspend fun updateSenderIdentifier(identifier: SmsSenderIdentifierEntity) {
        dao.updateSenderIdentifier(identifier)
        dao.insertAuditLog(
            AuditLogEntity(
                action = "SENDER_IDENTIFIER_UPDATED",
                details = "Updated sender identifier '${identifier.id}' (Priority ${identifier.priority}, Active=${identifier.isActive})"
            )
        )
    }

    suspend fun deleteSenderIdentifier(identifier: SmsSenderIdentifierEntity) {
        dao.deleteSenderIdentifier(identifier)
        dao.insertAuditLog(
            AuditLogEntity(
                action = "SENDER_IDENTIFIER_DELETED",
                details = "Deleted sender identifier '${identifier.id}'"
            )
        )
    }


    suspend fun getActiveSources(): List<FinancialSourceEntity> = dao.getActiveSources()
    suspend fun getActiveSenderIdentifiers(): List<SmsSenderIdentifierEntity> = dao.getActiveSenderIdentifiers()

    // Phone management
    suspend fun registerPhone(phone: RegisteredPhoneEntity) {
        dao.insertPhone(phone)
        enqueue(listOf(outboxItem(KIND_PHONE, phone.phoneId)))
    }

    suspend fun registerRemotePhone(phoneId: String, friendlyName: String, modelName: String) = writeMutex.withLock {
        val localId = localDeviceId()
        if (phoneId == localId || phoneId == LEGACY_DEVICE_ID || phoneId.isBlank()) return@withLock
        val existing = dao.getPhoneById(phoneId)
        if (existing == null) {
            // Remove any stale duplicate remote phone record with matching name or model
            val all = dao.getAllPhonesOnce()
            val duplicate = all.firstOrNull { it.phoneId != localId && (it.friendlyName.equals(friendlyName, ignoreCase = true) || (it.modelName == modelName && modelName != "Android Phone")) }
            if (duplicate != null) {
                database.withTransaction {
                    dao.deletePhone(duplicate)
                    dao.rekeyTransactionsPhone(duplicate.phoneId, phoneId)
                    dao.rekeyRawSmsPhone(duplicate.phoneId, phoneId)
                    dao.rekeyManualSourcesPhone(duplicate.phoneId, phoneId)
                }
            }
            val phone = RegisteredPhoneEntity(
                phoneId = phoneId,
                userId = "USER-001",
                friendlyName = friendlyName,
                modelName = modelName,
                phoneNumber = duplicate?.phoneNumber ?: "",
                isCurrentDevice = false
            )
            database.withTransaction { dao.insertPhone(phone) }
        } else if (existing.friendlyName != friendlyName || existing.modelName != modelName || existing.isCurrentDevice) {
            database.withTransaction {
                dao.updatePhone(existing.copy(friendlyName = friendlyName, modelName = modelName, isCurrentDevice = false))
            }
        }
    }

    suspend fun updatePhone(phone: RegisteredPhoneEntity) {
        dao.updatePhone(phone)
        enqueue(listOf(outboxItem(KIND_PHONE, phone.phoneId)))
    }
    suspend fun deletePhone(phone: RegisteredPhoneEntity) = dao.deletePhone(phone)

    // Transaction updates (manual category editing, notes)
    suspend fun getTransactionById(txId: String): TransactionEntity? = dao.getTransactionById(txId)

    suspend fun updateTransactionCategory(txId: String, newCategoryId: String?, notes: String? = null) {
        val tx = dao.getTransactionById(txId) ?: return
        database.withTransaction {
            dao.updateTransaction(tx.copy(categoryId = newCategoryId, userNotes = notes ?: tx.userNotes, updatedAt = System.currentTimeMillis()))
            enqueue(listOf(outboxItem(KIND_TX, txId)))
        }
        dao.insertAuditLog(
            AuditLogEntity(
                action = "TRANSACTION_CATEGORY_EDITED",
                details = "Updated transaction $txId category to ${newCategoryId ?: "uncategorized"}",
                phoneId = tx.phoneId,
                rawSmsId = tx.rawSmsId
            )
        )
        try {
            val app = runCatching { BirrFlowApplication.instance }.getOrNull()
            if (app != null) {
                CategorizeNotificationHelper.dismissNotificationForTransaction(app, txId)
            }
        } catch (_: Throwable) {}
    }

    // ------------------------------------------------------------------
    // Category learning (auto-file setting + suggestion accuracy)
    // ------------------------------------------------------------------

    @Volatile
    private var autoFileCache: Boolean? = null

    /** Opt-in: file a new debit automatically when the same payee was always filed under one category (3+ times). */
    suspend fun isAutoFileEnabled(): Boolean =
        autoFileCache ?: (dao.getState(KEY_AUTO_FILE) == "1").also { autoFileCache = it }

    suspend fun setAutoFileEnabled(enabled: Boolean) {
        dao.putState(AppStateEntity(KEY_AUTO_FILE, if (enabled) "1" else "0"))
        autoFileCache = enabled
    }

    suspend fun categoryLearner(): CategoryLearner =
        CategoryLearner.train(dao.getCategorizedDebitsOnce(), dao.getAllCategoriesOnce())

    /** Counts how often the top suggestion was the category the user actually picked. */
    suspend fun recordSuggestionOutcome(hit: Boolean) {
        val (hits, total) = suggestionStats()
        dao.putState(AppStateEntity(KEY_SUGGESTION_STATS, "${hits + if (hit) 1 else 0},${total + 1}"))
    }

    suspend fun suggestionStats(): Pair<Int, Int> {
        val parts = dao.getState(KEY_SUGGESTION_STATS)?.split(",")
        val hits = parts?.getOrNull(0)?.toIntOrNull() ?: 0
        val total = parts?.getOrNull(1)?.toIntOrNull() ?: 0
        return hits to total
    }

    // Phone Income Source Assignment
    suspend fun assignPhoneToIncomeSource(phoneId: String, incomeSourceId: String?) {
        database.withTransaction {
            dao.updatePhoneIncomeSource(phoneId, incomeSourceId)
            if (incomeSourceId != null) {
                dao.updateIncomesCategoryForPhone(phoneId, incomeSourceId)
            } else {
                dao.clearIncomesCategoryForPhone(phoneId)
            }
            enqueue(listOf(outboxItem(KIND_PHONE, phoneId)))
        }
    }

    // Income Sources
    suspend fun getAllIncomeSourcesOnce(): List<IncomeSourceEntity> = dao.getAllIncomeSourcesOnce()
    suspend fun insertIncomeSource(source: IncomeSourceEntity) = dao.insertIncomeSource(source)
    suspend fun updateIncomeSource(source: IncomeSourceEntity) = dao.updateIncomeSource(source)
    suspend fun deleteIncomeSource(source: IncomeSourceEntity) {
        database.withTransaction {
            dao.clearIncomesCategoryForPhone(source.sourceId)
            dao.unassignCategoryFromTransactions(source.sourceId)
            dao.deleteIncomeSource(source)
        }
    }

    suspend fun getActiveCategoriesOnce(): List<CategoryEntity> = dao.getAllCategoriesOnce()
    suspend fun getUncategorizedExpensesOnce(limit: Int = 100): List<TransactionEntity> = dao.getUncategorizedExpensesOnce(limit)
    suspend fun getAllPhonesOnce(): List<RegisteredPhoneEntity> = dao.getAllPhonesOnce()

    // Internal Transfer Configs
    suspend fun insertInternalConfig(config: InternalTransferConfigEntity) = dao.insertInternalTransferConfig(config)
    suspend fun updateInternalConfig(config: InternalTransferConfigEntity) = dao.updateInternalTransferConfig(config)
    suspend fun deleteInternalConfig(config: InternalTransferConfigEntity) = dao.deleteInternalTransferConfig(config)

    // Financial Sources & Formats
    suspend fun insertSource(source: FinancialSourceEntity) = dao.insertSource(source)
    suspend fun updateSource(source: FinancialSourceEntity) = dao.updateSource(source)
    suspend fun insertFormat(format: SmsFormatEntity) = dao.insertFormat(format)
    suspend fun updateFormat(format: SmsFormatEntity) = dao.updateFormat(format)

    // Categories
    suspend fun insertCategory(cat: CategoryEntity) {
        dao.insertCategory(cat.copy(isCustom = true))
        enqueue(listOf(outboxItem(KIND_CATEGORY, cat.categoryId)))
        refreshCategorizeNotifications()
    }
    suspend fun updateCategory(cat: CategoryEntity) {
        dao.updateCategory(cat.copy(isCustom = true))
        enqueue(listOf(outboxItem(KIND_CATEGORY, cat.categoryId)))
        refreshCategorizeNotifications()
    }
    suspend fun deleteCategory(cat: CategoryEntity) {
        database.withTransaction {
            dao.unassignCategoryFromTransactions(cat.categoryId)
            dao.deleteCategory(cat)
        }
        refreshCategorizeNotifications()
    }

    /** Re-posts pending "categorize debit" notifications so their buttons match the current categories. */
    private suspend fun refreshCategorizeNotifications() {
        val app = runCatching { BirrFlowApplication.instance }.getOrNull() ?: return
        CategorizeNotificationHelper.syncUncategorizedDebitNotifications(app, this)
    }

    // Savings Goals
    suspend fun insertSavingsGoal(goal: SavingsGoalEntity) = dao.insertSavingsGoal(goal)
    suspend fun updateSavingsGoal(goal: SavingsGoalEntity) = dao.updateSavingsGoal(goal)
    suspend fun deleteSavingsGoal(goal: SavingsGoalEntity) = dao.deleteSavingsGoal(goal)

    // Raw SMS
    suspend fun deleteRawSms(id: String) = dao.deleteRawSms(id)

    /**
     * Seeds initial system configuration (institutions, sender identifiers, categories)
     * without any fake/mocked financial SMS messages or transactions.
     */
    suspend fun seedInitialDataIfEmpty() {
        val existingSources = dao.getActiveSources()
        if (existingSources.isNotEmpty()) return

        // 1. User profile placeholder
        dao.insertUser(UserEntity(userId = "USER-001", name = "My Financial Account", email = ""))

        // 2. Register current physical device dynamically (unique id per install so devices can be paired)
        val deviceModel = android.os.Build.MODEL.ifBlank { "Android Device" }
        val currentPhone = RegisteredPhoneEntity(
            phoneId = localDeviceId(),
            userId = "USER-001",
            friendlyName = "Primary Phone ($deviceModel)",
            modelName = deviceModel,
            phoneNumber = "",
            isCurrentDevice = true
        )
        dao.insertPhone(currentPhone)

        // 3. Default Financial Sources for Ethiopian Financial Ecosystem
        val sources = listOf(
            FinancialSourceEntity("telebirr", "Telebirr", SourceType.MOBILE_MONEY, "Ethio Telecom", true, "telebirr,127,Telebirr", "#059669"),
            FinancialSourceEntity("cbe", "Commercial Bank of Ethiopia", SourceType.BANK, "Commercial Bank of Ethiopia (CBE)", true, "cbe,Commercial Bank of Ethiopia,889", "#7C3AED"),
            FinancialSourceEntity("boa", "Bank of Abyssinia", SourceType.BANK, "Bank of Abyssinia (BOA)", true, "boa,Bank of Abyssinia,BankofAbyssinia", "#D97706"),
            FinancialSourceEntity("cbe_birr", "CBE Birr", SourceType.WALLET, "CBE Birr Mobile Money", true, "cbebirr,cbe birr,CBE Birr", "#2563EB")
        )
        sources.forEach { dao.insertSource(it) }

        // 4. Configured SMS Sender Identifiers (Admin-managed targeting)
        val defaultSenders = listOf(
            // Telebirr
            SmsSenderIdentifierEntity(
                id = "SND-001",
                sourceId = "telebirr",
                senderName = "telebirr",
                identifierType = SenderIdentifierType.NAME,
                matchMethod = MatchMethod.CASE_INSENSITIVE,
                priority = 1,
                description = "Primary Telebirr alphanumeric sender tag"
            ),
            SmsSenderIdentifierEntity(
                id = "SND-002",
                sourceId = "telebirr",
                senderNumber = "127",
                identifierType = SenderIdentifierType.PHONE_NUMBER,
                matchMethod = MatchMethod.EXACT,
                priority = 2,
                description = "Ethio Telecom shortcode 127"
            ),
            SmsSenderIdentifierEntity(
                id = "SND-003",
                sourceId = "telebirr",
                senderName = "TELEBIRR",
                identifierType = SenderIdentifierType.ALIAS,
                matchMethod = MatchMethod.EXACT,
                priority = 3,
                description = "Upper-case Telebirr sender label"
            ),

            // Commercial Bank of Ethiopia (CBE)
            SmsSenderIdentifierEntity(
                id = "SND-004",
                sourceId = "cbe",
                senderName = "CBE",
                identifierType = SenderIdentifierType.NAME,
                matchMethod = MatchMethod.CASE_INSENSITIVE,
                priority = 1,
                description = "CBE standard SMS sender name"
            ),
            SmsSenderIdentifierEntity(
                id = "SND-005",
                sourceId = "cbe",
                senderName = "Commercial Bank",
                identifierType = SenderIdentifierType.ALIAS,
                matchMethod = MatchMethod.CONTAINS,
                priority = 2,
                description = "Commercial Bank descriptor"
            ),
            SmsSenderIdentifierEntity(
                id = "SND-006",
                sourceId = "cbe",
                senderNumber = "889",
                identifierType = SenderIdentifierType.PHONE_NUMBER,
                matchMethod = MatchMethod.EXACT,
                priority = 3,
                description = "Commercial Bank of Ethiopia shortcode"
            ),

            // Bank of Abyssinia (BOA)
            SmsSenderIdentifierEntity(
                id = "SND-007",
                sourceId = "boa",
                senderName = "BOA",
                identifierType = SenderIdentifierType.NAME,
                matchMethod = MatchMethod.CASE_INSENSITIVE,
                priority = 1,
                description = "Bank of Abyssinia standard sender"
            ),
            SmsSenderIdentifierEntity(
                id = "SND-008",
                sourceId = "boa",
                senderName = "Bank of Abyssinia",
                identifierType = SenderIdentifierType.ALIAS,
                matchMethod = MatchMethod.CASE_INSENSITIVE,
                priority = 2,
                description = "Full name sender label for Abyssinia"
            ),

            // CBE Birr
            SmsSenderIdentifierEntity(
                id = "SND-009",
                sourceId = "cbe_birr",
                senderName = "CBE Birr",
                identifierType = SenderIdentifierType.NAME,
                matchMethod = MatchMethod.CASE_INSENSITIVE,
                priority = 1,
                description = "CBE Birr official wallet sender"
            ),
            SmsSenderIdentifierEntity(
                id = "SND-010",
                sourceId = "cbe_birr",
                senderName = "CBEBirr",
                identifierType = SenderIdentifierType.ALIAS,
                matchMethod = MatchMethod.CASE_INSENSITIVE,
                priority = 2,
                description = "CBEBirr compact sender string"
            )
        )
        defaultSenders.forEach { dao.insertSenderIdentifier(it) }

        // Note: Default spending categories are removed. Categories are created manually by the user.
        dao.deleteDefaultCategories()
    }

    private fun refKey(ref: String, sourceId: String, direction: String) = "$ref|$sourceId|$direction"

    private data class StableIds(val rawSmsId: String, val transactionId: String)

    /** Content-derived ids: re-reading the same SMS always yields the same ids (also across devices). */
    private fun stableIds(phoneId: String, sender: String, body: String, timestamp: Long): StableIds {
        val h = hashString("$phoneId|$sender|$body|$timestamp").take(16).uppercase(Locale.ROOT)
        return StableIds("SMS-$h", "TXN-$h")
    }

    private fun smsHash(phoneId: String, sender: String, body: String) = hashString("$phoneId|$sender|$body")

    private fun hashString(input: String): String {
        // Hand-rolled hex: String.format per byte cost ~64 format calls per SMS on the first scan
        return SyncCodec.toHex(MessageDigest.getInstance("SHA-256").digest(input.toByteArray()))
    }

    companion object {
        const val LEGACY_DEVICE_ID = "DEVICE-001"
        const val KEY_DEVICE_ID = "local_device_id"
        const val KEY_LAST_SCAN_TIMESTAMP = "last_inbox_scan_timestamp"
        const val KEY_PARSER_RULES_VERSION = "parser_rules_version"

        /** Bump when parser rules change so stored SMS are re-parsed once on upgrade. */
        const val PARSER_RULES_VERSION = 3

        /** Same body from the same sender within this window is the same SMS (broadcast vs. inbox time). */
        const val DUPLICATE_WINDOW_MS = 10 * 60 * 1000L

        const val KEY_CLOUD_ENABLED = "cloud_sync_enabled"

        const val KIND_TX = "TX"
        const val KIND_RECON = "RECON"
        const val KIND_MANUAL = "MANUAL"
        const val KEY_AUTO_FILE = "AUTO_FILE_CERTAIN_DEBITS"
        const val KEY_SUGGESTION_STATS = "CATEGORY_SUGGESTION_STATS"
        const val KIND_PHONE = "PHONE"
        const val KIND_CATEGORY = "CATEGORY"

        private val CPU_COUNT = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        private const val PARALLEL_THRESHOLD = 64

        val RECONCILED_CATEGORY = CategoryEntity("cat_reconciled", "Recovered from balance", "balance", "#0EA5E9")
    }
}
