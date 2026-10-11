package com.example.service

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.example.data.entity.FinancialSourceEntity
import com.example.data.entity.MatchMethod
import com.example.data.entity.SmsSenderIdentifierEntity
import com.example.data.repository.BatchIngestionSummary
import com.example.data.repository.FinancialRepository
import com.example.data.repository.IncomingSms
import com.example.data.repository.PreparedSms
import com.example.parser.SmsParserEngine
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class InboxScanSummary(
    val totalSmsRead: Int = 0,
    val processedCount: Int = 0,
    val newTransactions: Int = 0,
    val duplicatesSkipped: Int = 0,
    val unknownSenders: Int = 0,
    val unknownFormats: Int = 0,
    val errorMessage: String? = null,
    val isIncremental: Boolean = false,
    val durationMs: Long = 0
)

data class ScanProgress(
    val isRunning: Boolean = false,
    val isFullScan: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val newTransactions: Int = 0
)

/** SQL "WHERE" clause restricting the SMS provider to configured bank senders. */
data class SenderSelection(val clause: String, val args: List<String>)

class SmsInboxReader(
    private val context: Context,
    private val repository: FinancialRepository
) {

    /**
     * Reads bank SMS from the inbox.
     *
     * - Only configured bank senders are requested from the SMS provider (SQL filter on the address),
     *   so bodies of personal messages, OTPs and promotions are never read by the app.
     * - Incremental: only SMS newer than the last scan (minus a small overlap) are read; the first run
     *   (or a forced full rescan) reads everything.
     * - SMS are parsed on all CPU cores and written in large chunks (one DB transaction each), newest
     *   first, so the dashboard fills with recent activity almost immediately.
     */
    suspend fun scanInbox(
        phoneId: String,
        fullRescan: Boolean = false,
        onProgress: (ScanProgress) -> Unit = {}
    ): InboxScanSummary = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val ctx = repository.loadParseContext()
        val lastScan = if (fullRescan) 0L else repository.getState(FinancialRepository.KEY_LAST_SCAN_TIMESTAMP)?.toLongOrNull() ?: 0L
        val isIncremental = lastScan > 0L
        val since = if (isIncremental) (lastScan - OVERLAP_MS).coerceAtLeast(0L) else 0L

        var summary = BatchIngestionSummary()
        var totalRead = 0
        var maxDate = lastScan
        var errorMsg: String? = null

        try {
            val selection = buildSelection(ctx.identifiers, ctx.sources, regexAddresses(ctx.identifiers, since))
            if (selection == null) {
                return@withContext InboxScanSummary(errorMessage = "No bank senders configured", isIncremental = isIncremental)
            }
            val where = "(${selection.clause}) AND ${Telephony.Sms.DATE} > ?"
            val args = (selection.args + since.toString()).toTypedArray()

            val dedupe = repository.loadDedupeIndex(phoneId)

            // 1. Read the matching rows in one pass (cheap: no parsing, no DB writes yet).
            //    Newest first, so today's transactions reach the dashboard within the first chunk.
            val messages = ArrayList<IncomingSms>()
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                where,
                args,
                "${Telephony.Sms.DATE} DESC"
            )?.use { c ->
                val addressIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                messages.ensureCapacity(c.count)
                while (c.moveToNext()) {
                    val address = c.getString(addressIdx)
                    val body = c.getString(bodyIdx)
                    val date = c.getLong(dateIdx)
                    if (date > maxDate) maxDate = date
                    if (address.isNullOrBlank() || body.isNullOrBlank()) continue
                    // Defense in depth: the SQL filter is broad for CONTAINS rules; re-check with the matcher
                    if (SmsParserEngine.matchSenderIdentifier(address, address, ctx.sources, ctx.identifiers) == null) continue
                    messages += IncomingSms(address, body, date)
                }
            } ?: Log.w(TAG, "ContentResolver returned null cursor for SMS inbox")

            totalRead = messages.size
            val total = messages.size
            val chunks = if (messages.size <= FIRST_CHUNK) listOf(messages)
            else listOf(messages.subList(0, FIRST_CHUNK)) + messages.subList(FIRST_CHUNK, messages.size).chunked(CHUNK_SIZE)
            onProgress(ScanProgress(isRunning = true, isFullScan = !isIncremental, scanned = 0, total = total))

            // 2. Pipeline: parse the next chunk on all CPU cores while the current one is written
            coroutineScope {
                var done = 0
                var next: Deferred<List<PreparedSms>>? =
                    chunks.firstOrNull()?.let { first -> async(Dispatchers.Default) { repository.prepare(phoneId, first, ctx) } }
                for (i in chunks.indices) {
                    val prepared = next!!.await()
                    next = chunks.getOrNull(i + 1)?.let { c -> async(Dispatchers.Default) { repository.prepare(phoneId, c, ctx) } }
                    summary += repository.ingestBatch(phoneId, prepared, ctx, dedupe)
                    done += chunks[i].size
                    onProgress(ScanProgress(true, !isIncremental, done, total, summary.newTransactions))
                }
            }
            repository.reconcileAccounts(summary.affectedAccounts)
            if (maxDate > lastScan) repository.putState(FinancialRepository.KEY_LAST_SCAN_TIMESTAMP, maxDate.toString())
        } catch (e: SecurityException) {
            errorMsg = "Permission denied: READ_SMS permission is required to scan inbox."
            Log.e(TAG, "SecurityException: ${e.message}", e)
        } catch (e: Exception) {
            errorMsg = "Failed reading inbox: ${e.message}"
            Log.e(TAG, "Exception scanning inbox: ${e.message}", e)
        }

        InboxScanSummary(
            totalSmsRead = totalRead,
            processedCount = summary.processed,
            newTransactions = summary.newTransactions,
            duplicatesSkipped = summary.duplicates,
            unknownSenders = 0,
            unknownFormats = summary.unknownFormats,
            errorMessage = errorMsg,
            isIncremental = isIncremental,
            durationMs = System.currentTimeMillis() - started
        )
    }

    /**
     * Fast Instant Startup Scan:
     * Reads the latest bank/transactional SMS (up to 60 newest messages).
     * Populates latest bank balances and recent activity immediately without blocking.
     * Note: Does NOT advance KEY_LAST_SCAN_TIMESTAMP so deep/historical scans never miss older transactions.
     */
    suspend fun quickStartupScan(
        phoneId: String,
        limit: Int = 60
    ): InboxScanSummary = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val ctx = repository.loadParseContext()
        var summary = BatchIngestionSummary()
        var totalRead = 0
        var maxDate = 0L
        var errorMsg: String? = null

        try {
            val selection = buildSelection(ctx.identifiers, ctx.sources, emptyList())
            if (selection == null) {
                return@withContext InboxScanSummary(errorMessage = "No bank senders configured", isIncremental = true)
            }
            val where = selection.clause
            val args = selection.args.toTypedArray()
            val dedupe = repository.loadDedupeIndex(phoneId)

            val messages = ArrayList<IncomingSms>()
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                where,
                args,
                "${Telephony.Sms.DATE} DESC LIMIT $limit"
            )?.use { c ->
                val addressIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                while (c.moveToNext()) {
                    val address = c.getString(addressIdx)
                    val body = c.getString(bodyIdx)
                    val date = c.getLong(dateIdx)
                    if (date > maxDate) maxDate = date
                    if (address.isNullOrBlank() || body.isNullOrBlank()) continue
                    if (SmsParserEngine.matchSenderIdentifier(address, address, ctx.sources, ctx.identifiers) == null) continue
                    messages += IncomingSms(address, body, date)
                }
            }

            totalRead = messages.size
            if (messages.isNotEmpty()) {
                val prepared = repository.prepare(phoneId, messages, ctx)
                summary = repository.ingestBatch(phoneId, prepared, ctx, dedupe)
                repository.reconcileAccounts(summary.affectedAccounts)
            }
        } catch (e: SecurityException) {
            errorMsg = "Permission denied: READ_SMS permission is required."
            Log.e(TAG, "SecurityException in quickStartupScan: ${e.message}", e)
        } catch (e: Exception) {
            errorMsg = "Failed reading latest SMS: ${e.message}"
            Log.e(TAG, "Exception in quickStartupScan: ${e.message}", e)
        }

        InboxScanSummary(
            totalSmsRead = totalRead,
            processedCount = summary.processed,
            newTransactions = summary.newTransactions,
            duplicatesSkipped = summary.duplicates,
            unknownSenders = 0,
            unknownFormats = summary.unknownFormats,
            errorMessage = errorMsg,
            isIncremental = true,
            durationMs = System.currentTimeMillis() - started
        )
    }

    /**
     * REGEX identifiers cannot be expressed in SQL. For those only the sender address column is read
     * (never the body) and the matching addresses are added to the exact-match list.
     */
    private fun regexAddresses(identifiers: List<SmsSenderIdentifierEntity>, since: Long): List<String> {
        val regexRules = identifiers.filter { it.isActive && it.matchMethod == MatchMethod.REGEX }
        if (regexRules.isEmpty()) return emptyList()
        val found = HashSet<String>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
            null
        )?.use { c ->
            val idx = c.getColumnIndex(Telephony.Sms.ADDRESS)
            while (c.moveToNext()) {
                val address = c.getString(idx) ?: continue
                if (address in found) continue
                val hit = regexRules.any { r ->
                    (!r.senderName.isNullOrBlank() && SmsParserEngine.matchesRule(address, r.senderName, MatchMethod.REGEX)) ||
                        (!r.senderNumber.isNullOrBlank() && SmsParserEngine.matchesRule(address, r.senderNumber, MatchMethod.REGEX))
                }
                if (hit) found += address
            }
        }
        return found.toList()
    }

    companion object {
        private const val TAG = "SmsInboxReader"
        private const val FIRST_CHUNK = 200
        private const val CHUNK_SIZE = 1500
        /** Re-read a short window before the last scan in case the provider stored an SMS late. */
        private const val OVERLAP_MS = 10 * 60 * 1000L

        fun buildSelection(
            identifiers: List<SmsSenderIdentifierEntity>,
            sources: List<FinancialSourceEntity>,
            extraExactAddresses: List<String> = emptyList()
        ): SenderSelection? {
            // (clause, argument) pairs; a set so repeated identifiers do not bloat the query
            val terms = LinkedHashSet<Pair<String, String>>()
            val address = Telephony.Sms.ADDRESS

            fun exact(value: String) {
                terms += "$address = ? COLLATE NOCASE" to value.trim().lowercase()
            }

            fun rule(value: String?, method: MatchMethod) {
                if (value.isNullOrBlank()) return
                when {
                    SmsParserEngine.isNumericAddress(value) -> exact(value)
                    method == MatchMethod.CONTAINS -> terms += "$address LIKE ?" to "%${value.trim().lowercase()}%"
                    method == MatchMethod.REGEX -> Unit // handled by the address-only pre-pass
                    else -> exact(value)
                }
            }

            identifiers.filter { it.isActive }.forEach {
                rule(it.senderName, it.matchMethod)
                rule(it.senderNumber, it.matchMethod)
            }
            sources.filter { it.isActive }.forEach { src ->
                src.senderIdentifiers.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { exact(it) }
            }
            extraExactAddresses.forEach { exact(it) }

            if (terms.isEmpty()) return null
            return SenderSelection(terms.joinToString(" OR ") { it.first }, terms.map { it.second })
        }
    }
}
