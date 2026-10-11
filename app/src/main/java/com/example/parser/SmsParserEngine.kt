package com.example.parser

import com.example.data.entity.FinancialSourceEntity
import com.example.data.entity.FlowDirection
import com.example.data.entity.InternalTransferConfigEntity
import com.example.data.entity.MatchMethod
import com.example.data.entity.SmsFormatEntity
import com.example.data.entity.SmsSenderIdentifierEntity
import com.example.data.entity.SourceType
import com.example.data.entity.TransactionType
import com.example.data.entity.VerificationStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

object SmsParserEngine {

    // Compiled once and reused: building ~50 Regex objects per SMS dominated first-scan parsing time
    private val regexCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()

    private fun rx(pattern: String): Regex = regexCache[pattern] ?: Regex(pattern).also { regexCache[pattern] = it }

    // SimpleDateFormat is not thread-safe; one instance per parsing thread (ThreadLocal.withInitial needs API 26)
    private val dayFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    }
    private val minuteFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("HH:mm", Locale.US)
    }

    private fun cleanDouble(raw: String?): Double? {
        if (raw == null) return null
        val cleaned = raw.replace(",", "").replace("ETB", "", ignoreCase = true).replace("Br.", "", ignoreCase = true).replace("Br", "", ignoreCase = true).trim().trimEnd('.')
        return cleaned.toDoubleOrNull()
    }

    /**
     * Checks if a single string matches a target value using the specified MatchMethod.
     */
    fun matchesRule(input: String, target: String, method: MatchMethod): Boolean {
        val trimmedInput = input.trim()
        val trimmedTarget = target.trim()
        if (trimmedInput.isEmpty() || trimmedTarget.isEmpty()) return false

        // Numeric identifiers (short codes like "127" or "889") are always compared as whole numbers.
        // A CONTAINS/REGEX rule on "127" must never match a personal number such as +251912712345.
        if (isNumericAddress(trimmedTarget)) {
            return isNumericAddress(trimmedInput) && normalizeNumber(trimmedInput) == normalizeNumber(trimmedTarget)
        }

        return when (method) {
            MatchMethod.EXACT -> trimmedInput == trimmedTarget
            MatchMethod.CASE_INSENSITIVE -> trimmedInput.equals(trimmedTarget, ignoreCase = true)
            MatchMethod.CONTAINS -> trimmedInput.contains(trimmedTarget, ignoreCase = true)
            MatchMethod.REGEX -> {
                try {
                    rx("(?i)" + trimmedTarget).containsMatchIn(trimmedInput)
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    fun isNumericAddress(value: String): Boolean {
        val v = value.trim()
        return v.isNotEmpty() && v.all { it.isDigit() || it == '+' || it == ' ' || it == '-' } && v.any { it.isDigit() }
    }

    fun normalizeNumber(value: String): String = value.filter { it.isDigit() }

    /**
     * Matches the incoming SMS sender (name or number) against administrator-configured
     * SMS Sender Identifiers, respecting priority order.
     */
    fun matchSenderIdentifier(
        sender: String,
        senderNumber: String? = null,
        sources: List<FinancialSourceEntity>,
        identifiers: List<SmsSenderIdentifierEntity>
    ): SourceDetectionResult? {
        val sourceMap = sources.associateBy { it.sourceId }
        val activeIdentifiers = identifiers.filter { it.isActive }.sortedBy { it.priority }

        for (ident in activeIdentifiers) {
            var isMatch = false

            // 1. Check against sender name
            if (!ident.senderName.isNullOrBlank()) {
                if (matchesRule(sender, ident.senderName, ident.matchMethod)) {
                    isMatch = true
                }
            }

            // 2. Check against sender number
            if (!isMatch && !ident.senderNumber.isNullOrBlank()) {
                if (matchesRule(sender, ident.senderNumber, ident.matchMethod)) {
                    isMatch = true
                }
                if (!isMatch && senderNumber != null && matchesRule(senderNumber, ident.senderNumber, ident.matchMethod)) {
                    isMatch = true
                }
            }

            if (isMatch) {
                val source = sourceMap[ident.sourceId]
                val sourceName = source?.name ?: ident.sourceId.uppercase()
                return SourceDetectionResult(
                    sourceId = ident.sourceId,
                    sourceName = sourceName,
                    confidence = 1.0f,
                    matchedIdentifierId = ident.id
                )
            }
        }

        // Fallback: legacy comma-separated senderIdentifiers on FinancialSourceEntity.
        // Whole-value matches only: substring matching let personal numbers containing "127"/"889" through.
        for (source in sources) {
            val legacyIds = source.senderIdentifiers
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            for (id in legacyIds) {
                if (matchesRule(sender, id, MatchMethod.CASE_INSENSITIVE) ||
                    (senderNumber != null && matchesRule(senderNumber, id, MatchMethod.CASE_INSENSITIVE))
                ) {
                    return SourceDetectionResult(source.sourceId, source.name, 0.95f)
                }
            }
        }

        return null
    }

    /**
     * Admin tool: test a sender name / number against configured identifiers
     */
    fun testSender(
        senderName: String,
        senderNumber: String?,
        sources: List<FinancialSourceEntity>,
        identifiers: List<SmsSenderIdentifierEntity>
    ): SenderTestResult {
        val sourceMap = sources.associateBy { it.sourceId }
        val activeIdentifiers = identifiers.filter { it.isActive }.sortedBy { it.priority }

        for (ident in activeIdentifiers) {
            var matchedOn = ""
            if (!ident.senderName.isNullOrBlank() && matchesRule(senderName, ident.senderName, ident.matchMethod)) {
                matchedOn = "Sender Name '${ident.senderName}' (${ident.matchMethod})"
            } else if (!ident.senderNumber.isNullOrBlank() && (matchesRule(senderName, ident.senderNumber, ident.matchMethod) ||
                        (senderNumber != null && matchesRule(senderNumber, ident.senderNumber, ident.matchMethod)))) {
                matchedOn = "Sender Number '${ident.senderNumber}' (${ident.matchMethod})"
            }

            if (matchedOn.isNotEmpty()) {
                val source = sourceMap[ident.sourceId]
                return SenderTestResult(
                    matchedInstitutionId = ident.sourceId,
                    matchedInstitutionName = source?.name ?: ident.sourceId,
                    matchedIdentifier = ident,
                    isApplicable = true,
                    reason = "Matched via $matchedOn with Priority ${ident.priority}"
                )
            }
        }

        return SenderTestResult(
            matchedInstitutionId = null,
            matchedInstitutionName = null,
            matchedIdentifier = null,
            isApplicable = false,
            reason = "No configured financial institution matches this sender."
        )
    }

    /**
     * Complete SMS Parser Pipeline:
     * 1. Match Sender against admin identifiers
     * 2. If no match -> UNKNOWN_SENDER (never fabricate a transaction)
     * 3. If matched -> Match SMS format
     * 4. If no format match -> UNKNOWN_FORMAT
     * 5. Parse, validate, check internal transfer rules
     */
    fun parseSms(
        sender: String,
        body: String,
        sources: List<FinancialSourceEntity>,
        formats: List<SmsFormatEntity>,
        identifiers: List<SmsSenderIdentifierEntity> = emptyList(),
        internalConfigs: List<InternalTransferConfigEntity> = emptyList(),
        receivedTimestamp: Long = System.currentTimeMillis()
    ): ParsedSmsResult {
        // Step 1: Sender Identification
        val detected = matchSenderIdentifier(sender, null, sources, identifiers)
        if (detected == null) {
            return ParsedSmsResult(
                detectedSourceId = null,
                detectedSourceName = null,
                detectedFormatId = null,
                formatName = null,
                transactionType = TransactionType.UNKNOWN,
                amount = null,
                currency = "ETB",
                transactionDate = null,
                transactionTime = null,
                sender = sender,
                recipient = null,
                merchant = null,
                accountIdentifier = null,
                referenceNumber = null,
                balanceAfter = null,
                fee = 0.0,
                description = "Sender '$sender' is not a configured financial institution",
                confidence = 0.0f,
                verificationStatus = VerificationStatus.INVALID,
                validationErrors = listOf("No configured financial institution matches sender '$sender'"),
                parserVersion = 1,
                isUnknownSender = true,
                matchedSenderIdentifierId = null
            )
        }

        val sourceId = detected.sourceId
        val sourceName = detected.sourceName

        // Step 2: Custom dynamic formats in DB for this institution
        val candidateFormats = formats.filter { it.sourceId == sourceId && it.isActive }
        for (format in candidateFormats) {
            val result = tryParseWithFormat(body, format, sourceName, internalConfigs, detected.matchedIdentifierId, receivedTimestamp)
            if (result != null) {
                return result
            }
        }

        // Step 3: Built-in production rules for this institution
        val isBankAccount = sources.firstOrNull { it.sourceId == sourceId }?.type == SourceType.BANK
        val builtinResult = parseWithBuiltinRules(sender, body, sourceId, sourceName, internalConfigs, detected.matchedIdentifierId, receivedTimestamp, isBankAccount)
        if (builtinResult != null) {
            return builtinResult
        }

        // Format is unknown for this known sender
        return ParsedSmsResult(
            detectedSourceId = sourceId,
            detectedSourceName = sourceName,
            detectedFormatId = null,
            formatName = null,
            transactionType = TransactionType.UNKNOWN,
            amount = null,
            currency = "ETB",
            transactionDate = null,
            transactionTime = null,
            sender = sender,
            recipient = null,
            merchant = null,
            accountIdentifier = null,
            referenceNumber = null,
            balanceAfter = null,
            fee = 0.0,
            description = "Unrecognized financial format from $sourceName",
            confidence = 0.40f,
            verificationStatus = VerificationStatus.INVALID,
            validationErrors = listOf("SMS from $sourceName ($sender) did not match any recognized financial SMS format"),
            parserVersion = 1,
            isUnknownSender = false,
            matchedSenderIdentifierId = detected.matchedIdentifierId
        )
    }

    private fun tryParseWithFormat(
        body: String,
        format: SmsFormatEntity,
        sourceName: String,
        internalConfigs: List<InternalTransferConfigEntity>,
        matchedIdentifierId: String?,
        receivedTimestamp: Long
    ): ParsedSmsResult? {
        try {
            val keywords = format.keywords.split(",").map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
            val cleanBody = body.lowercase(Locale.ROOT)
            for (kw in keywords) {
                if (!cleanBody.contains(kw)) {
                    return null
                }
            }

            val pattern = Pattern.compile(format.regexPattern, Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
            val matcher = pattern.matcher(body)
            if (!matcher.find()) {
                return null
            }

            val amountStr = if (format.amountGroupIndex > 0 && matcher.groupCount() >= format.amountGroupIndex) {
                matcher.group(format.amountGroupIndex)
            } else null
            val amount = cleanDouble(amountStr)

            val party = if (format.partyGroupIndex > 0 && matcher.groupCount() >= format.partyGroupIndex) {
                matcher.group(format.partyGroupIndex)?.trim()
            } else null

            val dateStr = if (format.dateGroupIndex > 0 && matcher.groupCount() >= format.dateGroupIndex) {
                matcher.group(format.dateGroupIndex)?.trim()
            } else null

            val refNumber = if (format.refGroupIndex > 0 && matcher.groupCount() >= format.refGroupIndex) {
                matcher.group(format.refGroupIndex)?.trim()
            } else null

            val balanceStr = if (format.balanceGroupIndex > 0 && matcher.groupCount() >= format.balanceGroupIndex) {
                matcher.group(format.balanceGroupIndex)
            } else null
            val balance = cleanDouble(balanceStr)

            val msgDate = Date(receivedTimestamp)
            val defaultDate = dayFormat.get()!!.format(msgDate)
            val defaultTime = minuteFormat.get()!!.format(msgDate)

            val parsedDate = dateStr?.let { extractDate(it) } ?: defaultDate
            val parsedTime = dateStr?.let { extractTime(it) } ?: defaultTime

            var txType = format.defaultTransactionType
            val isInternal = isInternalTransfer(party, internalConfigs)
            if (isInternal) {
                txType = TransactionType.INTERNAL_TRANSFER
            }

            val errors = mutableListOf<String>()
            if (amount == null || amount <= 0) errors.add("Missing or invalid transaction amount")
            if (refNumber.isNullOrEmpty()) errors.add("Missing transaction reference number")

            val status = when {
                errors.isEmpty() -> VerificationStatus.VALID
                amount != null && amount > 0 -> VerificationStatus.WARNING
                else -> VerificationStatus.NEEDS_REVIEW
            }

            val confidence = if (errors.isEmpty()) 0.98f else 0.75f

            return ParsedSmsResult(
                detectedSourceId = format.sourceId,
                detectedSourceName = sourceName,
                detectedFormatId = format.formatId,
                formatName = format.formatName,
                transactionType = txType,
                amount = amount,
                currency = "ETB",
                transactionDate = parsedDate,
                transactionTime = parsedTime,
                sender = if (txType == TransactionType.INCOME) party else null,
                recipient = if (txType == TransactionType.EXPENSE || txType == TransactionType.INTERNAL_TRANSFER) party else null,
                merchant = if (txType == TransactionType.EXPENSE && party != null && !party.matches(rx("^[0-9+]+$"))) party else null,
                accountIdentifier = null,
                referenceNumber = refNumber,
                balanceAfter = balance,
                fee = 0.0,
                description = "${format.formatName} - ${party ?: sourceName}",
                confidence = confidence,
                verificationStatus = status,
                validationErrors = errors,
                parserVersion = format.version,
                isUnknownSender = false,
                matchedSenderIdentifierId = matchedIdentifierId,
                direction = when (format.defaultTransactionType) {
                    TransactionType.INCOME, TransactionType.DEPOSIT -> FlowDirection.CREDIT
                    TransactionType.EXPENSE, TransactionType.WITHDRAWAL, TransactionType.FEE, TransactionType.TRANSFER -> FlowDirection.DEBIT
                    else -> detectDirection(body)
                },
                accountKey = format.sourceId
            )
        } catch (_: Exception) {
            return null
        }
    }

    private const val AMT = "([\\d,]+(?:\\.\\d{1,2})?)"
    private const val CUR = "(?:ETB|Birr|Br\\.?|ብር)"

    // Party text ends before " on <date>", a "(" phone mask, ", Your ..." or the end of a sentence.
    // "." inside names such as "P.L.C" or "St.George" is allowed (the old [^.]+ pattern dropped those SMS).
    private const val PARTY_END = "(?=\\s+on\\s+\\d|\\s*,\\s*(?:your|txn|on)\\b|\\.\\s|\\.$|\\s*$)"

    private val rxMoneyPrefix = Regex("(?i)$CUR\\s*$AMT")
    private val rxMoneySuffix = Regex("(?i)(?<![\\w.])$AMT\\s*(?:ETB|Birr|Br\\b|Br\\.|ብር)")
    private val rxOwnAccountMask = Regex("(?i)your\\s+account\\s+(\\d{1,4}\\*+\\d{2,6})")
    private val rxAnyAccountMask = Regex("(?<![\\d*])(\\d{1,4}\\*+\\d{2,6})(?![\\d*])")

    private val creditKeywords = listOf(
        "you have received", "you received", "has been credited", "was credited", "credited with",
        "you have deposited", "deposited to your", "cash in", "has been refunded", "has been reversed",
        "ተቀብለዋል", "ገቢ ተደርጓል", "ገቢ ሆኗል", "ተመላሽ"
    )
    private val debitKeywords = listOf(
        "you have transferred", "you have sent", "you sent", "you have paid", "you paid",
        "has been debited", "was debited", "debited with", "you have withdrawn", "cash out", "cashed out",
        "you have bought", "you have purchased", "you have recharged",
        "አስተላልፈዋል", "ከፍለዋል", "ልከዋል", "ወጪ ተደርጓል", "ገዝተዋል", "ወጪ ሆኗል"
    )

    /** Stable key of the individual account behind an SMS: wallets are one per source, bank accounts use the unmasked tail. */
    fun accountKeyFor(sourceId: String, accountMask: String?): String {
        val tail = accountMask?.substringAfterLast('*')?.filter { it.isDigit() }.orEmpty()
        return if (tail.length >= 2) "$sourceId:$tail" else sourceId
    }

    /** Account mask (e.g. "1*****0032") of the account holder's own account, preferring "your account ..." wording. */
    fun extractOwnAccountMask(body: String): String? {
        rxOwnAccountMask.find(body)?.let { return it.groupValues[1] }
        return rxAnyAccountMask.find(body)?.groupValues?.get(1)
    }

    /** Account key for any SMS of a source; wallets (Telebirr, CBE Birr...) never use masks because masks there are counterparties. */
    fun accountKeyForBody(sourceId: String, body: String, isBankAccount: Boolean): String =
        if (isBankAccount) accountKeyFor(sourceId, extractOwnAccountMask(body)) else sourceId

    /**
     * Returns the transaction amount mention, skipping money that belongs to the balance, fee, VAT or totals.
     */
    fun extractPrimaryAmount(body: String): Double? {
        val mentions = (rxMoneyPrefix.findAll(body) + rxMoneySuffix.findAll(body))
            .sortedBy { it.range.first }
            .toList()
        for (m in mentions) {
            val before = body.substring(maxOf(0, m.range.first - 45), m.range.first).lowercase(Locale.ROOT)
            val isNotTransactionAmount = listOf("balance", "bal:", "bal.", "ቀሪ", "fee", "vat", "charge", "total of", "commission")
                .any { before.contains(it) }
            if (isNotTransactionAmount) continue
            val value = cleanDouble(m.groupValues[1])
            if (value != null && value > 0) return value
        }
        return null
    }

    /** Credit/debit direction from wording (English and Amharic). The earliest keyword wins. */
    fun detectDirection(body: String): FlowDirection {
        val lower = body.lowercase(Locale.ROOT)
        val credit = creditKeywords.mapNotNull { k -> lower.indexOf(k).takeIf { it >= 0 } }.minOrNull()
        val debit = debitKeywords.mapNotNull { k -> lower.indexOf(k).takeIf { it >= 0 } }.minOrNull()
        return when {
            credit == null && debit == null -> FlowDirection.UNKNOWN
            credit == null -> FlowDirection.DEBIT
            debit == null -> FlowDirection.CREDIT
            credit < debit -> FlowDirection.CREDIT
            else -> FlowDirection.DEBIT
        }
    }

    private fun cleanParty(raw: String?): String? {
        if (raw == null) return null
        var p = raw.trim().trimEnd('.', ',')
        // "goods purchased from 515151 - Ride Technologies" -> "515151 - Ride Technologies"
        val fromIdx = p.lowercase(Locale.ROOT).lastIndexOf(" from ")
        if (p.lowercase(Locale.ROOT).startsWith("goods") && fromIdx >= 0) p = p.substring(fromIdx + 6).trim()
        return p.ifBlank { null }
    }

    private fun parseWithBuiltinRules(
        sender: String,
        body: String,
        sourceId: String,
        sourceName: String,
        internalConfigs: List<InternalTransferConfigEntity>,
        matchedIdentifierId: String?,
        receivedTimestamp: Long,
        isBankAccount: Boolean = false
    ): ParsedSmsResult? {
        val msgDate = Date(receivedTimestamp)
        val defaultDate = dayFormat.get()!!.format(msgDate)
        val defaultTime = minuteFormat.get()!!.format(msgDate)

        val userName = extractUserName(body)
        val extractedRef = extractReferenceNumber(body)
        val extractedBal = extractBalance(body)
        val extractedD = extractDate(body) ?: defaultDate
        val extractedT = extractTime(body) ?: defaultTime

        // Shared builder so every rule fills the same fields (direction + account key included)
        fun build(
            formatId: String,
            formatName: String,
            baseType: TransactionType,
            direction: FlowDirection,
            amount: Double?,
            party: String?,
            accountLabel: String,
            accountKey: String,
            description: String,
            fee: Double = 0.0,
            dateRaw: String? = null,
            confidence: Float = 0.99f,
            internalCandidates: List<String?> = listOf(party),
            merchant: String? = null
        ): ParsedSmsResult {
            val isInternal = internalCandidates.any { isInternalTransfer(it, internalConfigs) }
            val txType = if (isInternal) TransactionType.INTERNAL_TRANSFER else baseType
            val errors = mutableListOf<String>()
            if (amount == null || amount <= 0) errors.add("Missing or invalid transaction amount")
            val status = when {
                errors.isNotEmpty() -> VerificationStatus.NEEDS_REVIEW
                confidence < 0.9f -> VerificationStatus.WARNING
                else -> VerificationStatus.VALID
            }
            return ParsedSmsResult(
                detectedSourceId = sourceId,
                detectedSourceName = sourceName,
                detectedFormatId = formatId,
                formatName = formatName,
                transactionType = txType,
                amount = amount,
                currency = "ETB",
                transactionDate = dateRaw?.let { extractDate(it) } ?: extractedD,
                transactionTime = dateRaw?.let { extractTime(it) } ?: extractedT,
                sender = if (direction == FlowDirection.CREDIT) party else userName,
                recipient = if (direction == FlowDirection.DEBIT) party else userName,
                merchant = merchant,
                accountIdentifier = accountLabel,
                referenceNumber = extractedRef,
                balanceAfter = extractedBal,
                fee = fee,
                description = description,
                confidence = confidence,
                verificationStatus = status,
                validationErrors = errors,
                parserVersion = 3,
                isUnknownSender = false,
                matchedSenderIdentifierId = matchedIdentifierId,
                direction = direction,
                accountKey = accountKey
            )
        }

        // ==========================================
        // 1. TELEBIRR PARSER
        // ==========================================
        if (sourceId == "telebirr") {
            val wallet = "Telebirr Wallet"
            val key = "telebirr"
            val rxFee = rx("(?i)service fee is\\s+(?:ETB\\s*)?$AMT")
            val rxVat = rx("(?i)VAT on the service fee is\\s+(?:ETB\\s*)?$AMT")
            val totalFee = (cleanDouble(rxFee.find(body)?.groupValues?.get(1)) ?: 0.0) +
                (cleanDouble(rxVat.find(body)?.groupValues?.get(1)) ?: 0.0)
            val dateInBody = rx("\\bon\\s+(\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}(?:\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)?)").find(body)?.groupValues?.get(1)

            // 1A. Telebirr -> bank account transfer
            // "You have transferred ETB 500.00 successfully from your telebirr account 2519.. to Commercial Bank of Ethiopia account number 1000.. on 25/09/2026 ..."
            rx("(?i)transferred\\s+(?:ETB\\s*)?$AMT\\s*(?:ETB|Birr)?\\s+successfully\\s+from\\s+your\\s+telebirr\\s+account.*?\\bto\\s+(.+?)$PARTY_END")
                .find(body)?.let { m ->
                    val party = cleanParty(m.groupValues[2])
                    return build("telebirr_bank_transfer_builtin", "Telebirr to Bank Transfer", TransactionType.EXPENSE, FlowDirection.DEBIT,
                        cleanDouble(m.groupValues[1]), party, wallet, key, "Transferred via Telebirr to ${party ?: "bank account"}", totalFee, dateInBody)
                }

            // 1B. Telebirr Transfer (Debit / Sent)
            // "You have transferred ETB 1,000.00 to kaleb haile (2519****1879) on 25/09/2026 20:08:48. ..."
            rx("(?i)You have (?:transferred|sent)\\s+(?:ETB\\s*)?$AMT\\s*(?:ETB|Birr)?\\s+to\\s+(.+?)$PARTY_END")
                .find(body)?.let { m ->
                    val party = cleanParty(m.groupValues[2])
                    return build("telebirr_transfer_builtin", "Telebirr Transfer", TransactionType.EXPENSE, FlowDirection.DEBIT,
                        cleanDouble(m.groupValues[1]), party, wallet, key, "Transferred via Telebirr to $party", totalFee, dateInBody)
                }

            // 1C. Telebirr Received (Credit / Income)
            // "You have received ETB 50.00 from Ibrahim Ahmed(2519****2326) on 25/09/2026 20:16:00. ..."
            rx("(?i)You have received\\s+(?:ETB\\s*)?$AMT\\s*(?:ETB|Birr)?\\s+from\\s+(.+?)$PARTY_END")
                .find(body)?.let { m ->
                    val party = cleanParty(m.groupValues[2])
                    return build("telebirr_recv_builtin", "Telebirr Received", TransactionType.INCOME, FlowDirection.CREDIT,
                        cleanDouble(m.groupValues[1]), party, wallet, key, "Received money via Telebirr from $party", 0.0, dateInBody)
                }

            // 1D. Telebirr Merchant / Bill / Package Payment
            // "You have paid ETB 1,250.00 to Kaldi's Coffee on 24/09/2026 14:15. ..."
            // "You have paid ETB 150.00 for goods purchased from 515151 - Ride Technologies on 20/09/2026 09:12:33. ..."
            rx("(?i)You have paid\\s+(?:ETB\\s*)?$AMT\\s*(?:ETB|Birr)?\\s+(?:to|for)\\s+(.+?)$PARTY_END")
                .find(body)?.let { m ->
                    val party = cleanParty(m.groupValues[2])
                    return build("telebirr_merchant_builtin", "Telebirr Merchant Payment", TransactionType.EXPENSE, FlowDirection.DEBIT,
                        cleanDouble(m.groupValues[1]), party, wallet, key, "Payment via Telebirr to $party", totalFee, dateInBody, merchant = party)
                }

            // 1E. Airtime / package purchase
            rx("(?i)You have (?:recharged|bought|purchased)\\s+(?:an?\\s+)?(?:airtime\\s+(?:of\\s+)?|package\\s+(?:of\\s+)?)?(?:ETB\\s*)?$AMT")
                .find(body)?.let { m ->
                    return build("telebirr_airtime_builtin", "Telebirr Airtime / Package", TransactionType.EXPENSE, FlowDirection.DEBIT,
                        cleanDouble(m.groupValues[1]), "Airtime / Package", wallet, key, "Airtime / package purchase via Telebirr", totalFee, dateInBody,
                        merchant = "Ethio telecom")
                }

            // 1F. Cash out / withdrawal at agent
            rx("(?i)(?:You have (?:withdrawn|cashed out)|cash\\s?out(?:\\s+of)?)\\s+(?:ETB\\s*)?$AMT")
                .find(body)?.let { m ->
                    return build("telebirr_cashout_builtin", "Telebirr Cash Out", TransactionType.WITHDRAWAL, FlowDirection.DEBIT,
                        cleanDouble(m.groupValues[1]), null, wallet, key, "Telebirr cash out", totalFee, dateInBody)
                }

            // 1G. Deposit / cash in at agent
            rx("(?i)(?:You have deposited|cash\\s?in(?:\\s+of)?)\\s+(?:ETB\\s*)?$AMT")
                .find(body)?.let { m ->
                    return build("telebirr_cashin_builtin", "Telebirr Cash In", TransactionType.DEPOSIT, FlowDirection.CREDIT,
                        cleanDouble(m.groupValues[1]), null, wallet, key, "Telebirr cash in", 0.0, dateInBody)
                }
        }

        // ==========================================
        // 2. CBE BIRR (CBEBIRR) PARSER
        // ==========================================
        if (sourceId == "cbe_birr") {
            val wallet = "CBE Birr Wallet"
            val key = "cbe_birr"
            // 2A. "Dear NAHOM, you have sent 2,500.00Br. to dagim tadele on 25/09/26 10:30,Txn ID DIP900AA003. Your CBE Birr account balance is 37.32Br."
            rx("(?i)you have sent\\s+$AMT\\s*(?:Br\\.|Br|ETB)\\s+to\\s+([^,]+?)\\s+on\\s+([\\d/\\-\\s:]+?)(?:,|\\.|Txn)")
                .find(body)?.let { m ->
                    val party = m.groupValues[2].trim()
                    return build("cbebirr_sent_builtin", "CBE Birr Sent", TransactionType.EXPENSE, FlowDirection.DEBIT,
                        cleanDouble(m.groupValues[1]), party, wallet, key, "Sent via CBE Birr to $party", 0.0, m.groupValues[3])
                }

            // 2C. "Cash In completed. You have received ETB 500 from agent on ..."
            rx("(?i)Cash In completed\\.\\s*You have received ETB\\s+$AMT\\s+from\\s+(.+?)\\s+on\\s+([\\d/\\-\\s:]+?)\\.")
                .find(body)?.let { m ->
                    val party = m.groupValues[2].trim()
                    return build("cbebirr_cashin_builtin", "CBE Birr Cash In", TransactionType.INCOME, FlowDirection.CREDIT,
                        cleanDouble(m.groupValues[1]), party, wallet, key, "CBE Birr Cash In from $party", 0.0, m.groupValues[3], confidence = 0.98f)
                }

            // 2B. "Dear NAHOM, you received 50.00Br. from 251900000526 - Liya Abera Tola on 23/09/26 17:34,Txn ID DIN900AA004."
            rx("(?i)you (?:have )?received\\s+(?:ETB\\s*)?$AMT\\s*(?:Br\\.|Br|ETB)?\\s+from\\s+([^,]+?)\\s+on\\s+([\\d/\\-\\s:]+?)(?:,|\\.|Txn)")
                .find(body)?.let { m ->
                    val party = m.groupValues[2].trim()
                    return build("cbebirr_recv_builtin", "CBE Birr Received", TransactionType.INCOME, FlowDirection.CREDIT,
                        cleanDouble(m.groupValues[1]), party, wallet, key, "Received via CBE Birr from $party", 0.0, m.groupValues[3])
                }
        }

        // ==========================================
        // 3. BANK OF ABYSSINIA (BOA) PARSER
        // ==========================================
        if (sourceId == "boa") {
            // 3A. "Dear Nahom, your account 1*83 was debited with ETB 30,018.00. Available Balance: ETB 402,381.24. ..."
            rx("(?i)(?:your account|BOA Account)\\s+([\\d*]+)\\s+(?:was|has been)\\s+debited with\\s+(?:ETB\\s*)?$AMT")
                .find(body)?.let { m ->
                    val acct = m.groupValues[1].trim()
                    return build("boa_debit_builtin", "BOA Account Debit", TransactionType.EXPENSE, FlowDirection.DEBIT,
                        cleanDouble(m.groupValues[2]), null, "BOA $acct", accountKeyFor("boa", acct), "BOA Debit from account $acct")
                }

            // 3B. "Dear Nahom, your account 1*83 was credited with ETB 20,000.00 by  Dagimtadeleworku . Available Balance: ..."
            rx("(?i)(?:your account|BOA Account)\\s+([\\d*]+)\\s+(?:was|has been)\\s+credited with\\s+(?:ETB\\s*)?$AMT(?:\\s+(?:by|from)\\s+([^.]+?))?\\s*(?:\\.|Available)")
                .find(body)?.let { m ->
                    val acct = m.groupValues[1].trim()
                    val party = m.groupValues.getOrNull(3)?.trim()?.ifBlank { null }
                    return build("boa_credit_builtin", "BOA Account Credit", TransactionType.INCOME, FlowDirection.CREDIT,
                        cleanDouble(m.groupValues[2]), party, "BOA $acct", accountKeyFor("boa", acct),
                        "BOA Credit to account $acct${if (party != null) " from $party" else ""}",
                        internalCandidates = listOf(party))
                }
        }

        // ==========================================
        // 4. COMMERCIAL BANK OF ETHIOPIA (CBE) PARSER
        // ==========================================
        if (sourceId == "cbe") {
            // 4A. "Dear Nahom your Account 1*****0032 has been debited with ETB23,626.00 .Including Service charge and VAT(15%) with a total of ETB 23754.05. Your Current Balance is ETB 10,047.96. ..."
            rx("(?i)(?:Account|your Account)\\s+([\\d*]+)\\s+has been debited with\\s+(?:ETB\\s*)?$AMT")
                .find(body)?.let { m ->
                    val acct = m.groupValues[1].trim()
                    val baseAmount = cleanDouble(m.groupValues[2]) ?: 0.0
                    // Do not calculate VAT; read the "with a total of ETB x" figure. The amount stays the base
                    // debit and the difference is the fee, so amount + fee equals exactly what left the account.
                    val totalAmount = cleanDouble(rx("(?i)total of\\s+(?:ETB\\s*)?$AMT").find(body)?.groupValues?.get(1)) ?: baseAmount
                    val fee = if (totalAmount > baseAmount) totalAmount - baseAmount else 0.0
                    return build("cbe_debit_builtin", "CBE Account Debit", TransactionType.EXPENSE, FlowDirection.DEBIT,
                        baseAmount, null, "CBE $acct", accountKeyFor("cbe", acct), "CBE Debit from account $acct", fee)
                }

            // 4B. "Dear Nahom Tadele Worku You have received ETB 4,000.00 from account 1**2152 (Yonas Tadesse Girma) to your account 1**0032. ..."
            rx("(?i)You have received\\s+(?:ETB\\s*)?$AMT\\s*(?:ETB)?\\s+from\\s+(.+?)\\s+to your account\\s+([\\d*]+)")
                .find(body)?.let { m ->
                    var party = m.groupValues[2].trim()
                    val toAcct = m.groupValues[3].trim()
                    rx("(?i)account\\s+[\\d*]+\\s*\\(([^)]+)\\)").find(party)?.let { party = it.groupValues[1].trim() }
                    return build("cbe_recv_builtin", "CBE Received", TransactionType.INCOME, FlowDirection.CREDIT,
                        cleanDouble(m.groupValues[1]), party, "CBE $toAcct", accountKeyFor("cbe", toAcct), "CBE Received from $party",
                        internalCandidates = listOf(party))
                }

            // 4C. "Account 1000******123 has been Credited with ETB 50,000.00 on 23/09/2026. Ref: FT2626800020. ..."
            rx("(?i)(?:Account|your Account)\\s+([\\d*]+)\\s+has been Credited with\\s+(?:ETB\\s*)?$AMT")
                .find(body)?.let { m ->
                    val acct = m.groupValues[1].trim()
                    return build("cbe_credit_builtin", "CBE Account Credit", TransactionType.INCOME, FlowDirection.CREDIT,
                        cleanDouble(m.groupValues[2]), null, "CBE $acct", accountKeyFor("cbe", acct), "CBE Account Credited ($acct)",
                        confidence = 0.98f)
                }
        }

        // ==========================================
        // 5. GENERIC FALLBACK (all institutions, English + Amharic)
        // ==========================================
        // Only a confirmed transaction is accepted: a direction word, an amount, and a new balance or a
        // transaction reference. Promotions ("pay ETB 100 and get cashback") carry neither and are skipped.
        val direction = detectDirection(body)
        val amount = extractPrimaryAmount(body)
        if (direction != FlowDirection.UNKNOWN && amount != null && (extractedBal != null || extractedRef != null)) {
            val party = rx("(?i)\\b(?:to|from|by)\\s+(.+?)$PARTY_END").find(body)?.groupValues?.get(1)?.let { cleanParty(it) }
                ?.takeIf { it.length <= 60 }
            val lower = body.lowercase(Locale.ROOT)
            val baseType = when {
                direction == FlowDirection.CREDIT && (lower.contains("deposit") || lower.contains("cash in")) -> TransactionType.DEPOSIT
                direction == FlowDirection.CREDIT -> TransactionType.INCOME
                lower.contains("withdraw") || lower.contains("cash out") -> TransactionType.WITHDRAWAL
                else -> TransactionType.EXPENSE
            }
            val accountMask = if (isBankAccount) extractOwnAccountMask(body) else null
            val label = if (accountMask != null) "$sourceName $accountMask" else sourceName
            return build(
                "${sourceId}_generic_builtin", "$sourceName ${if (direction == FlowDirection.CREDIT) "Credit" else "Debit"} (generic)",
                baseType, direction, amount, party, label, accountKeyFor(sourceId, accountMask),
                "${if (direction == FlowDirection.CREDIT) "Credit" else "Debit"} via $sourceName${party?.let { " — $it" } ?: ""}",
                confidence = 0.8f
            )
        }

        return null
    }

    /**
     * True when the counterparty is one of the user's own accounts (configured by the user).
     *
     * A candidate matches when the full configured name appears in the counterparty text, or when every
     * word of the configured name appears in it. Single shared words are not enough: in Ethiopian naming a
     * sibling or parent shares a name with the account holder, and treating "Tadele Abebe" as the user's
     * own "Nahom Tadele" account silently removed real income/expenses from the totals.
     */
    fun isInternalTransfer(
        partyText: String?,
        internalConfigs: List<InternalTransferConfigEntity>
    ): Boolean {
        if (partyText.isNullOrBlank()) return false
        val cleanParty = partyText.lowercase(Locale.ROOT).replace(rx("[^\\p{L}0-9]"), "")
        if (cleanParty.isEmpty()) return false
        val partyWords = partyText.lowercase(Locale.ROOT)
            .split(rx("[^\\p{L}0-9]+"))
            .filter { it.isNotEmpty() }
            .toSet()

        for (config in internalConfigs) {
            if (!config.isActive) continue

            val candidateNames = mutableListOf<String>()
            if (config.identityName.isNotBlank()) candidateNames.add(config.identityName)
            if (config.identifierValue.isNotBlank()) candidateNames.add(config.identifierValue)
            if (config.aliases.isNotBlank()) {
                candidateNames.addAll(config.aliases.split(",").map { it.trim() }.filter { it.isNotEmpty() })
            }

            for (candidate in candidateNames) {
                // Account numbers / phone numbers: compare the visible digits at the end (masks hide the middle)
                if (candidate.matches(rx("^[0-9+*\\s-]+$"))) {
                    val candDigits = candidate.filter { it.isDigit() }
                    val partyTail = partyText.substringAfterLast('*').filter { it.isDigit() }
                    if (candDigits.length >= 4 && partyTail.length >= 2 && candDigits.endsWith(partyTail) &&
                        partyText.contains('*')
                    ) return true
                    if (candDigits.length >= 6 && cleanParty.contains(candDigits)) return true
                    continue
                }

                val cleanCandidate = candidate.lowercase(Locale.ROOT).replace(rx("[^\\p{L}0-9]"), "")
                if (cleanCandidate.length < 3) continue

                // 1. Full configured name inside the counterparty text ("Dagimtadeleworku" contains "dagimtadele")
                if (cleanParty.contains(cleanCandidate)) return true

                // 2. Every word of the configured name present as a word of the counterparty
                val candidateWords = candidate.lowercase(Locale.ROOT)
                    .split(rx("[^\\p{L}0-9]+"))
                    .filter { it.length >= 2 }
                if (candidateWords.size >= 2 && candidateWords.all { it in partyWords }) return true
            }
        }
        return false
    }

    /**
     * Robust reference ID extraction covering Telebirr, CBE, CBE Birr, and BOA formats
     */
    fun extractReferenceNumber(body: String): String? {
        // 1. "Your transaction number is DIP900AA01" / "Your telebirr transaction number is CI12AB34CD"
        val mTxNumber = rx("(?i)transaction (?:number|id) is\\s*:?\\s*([A-Za-z0-9]+)").find(body)
        if (mTxNumber != null) return mTxNumber.groupValues[1].trim()

        // 2. "Txn ID DIP900AA003" or "Txn ID: DIP900AA003" or "Txn Id: CB891234"
        val mTxnId = rx("(?i)Txn\\s*ID:?\\s*([A-Za-z0-9]+)").find(body)
        if (mTxnId != null) return mTxnId.groupValues[1].trim()

        // 3. "trx=FT2623208AAA11083" or "TID=DIP900AA003"
        val mTrx = rx("(?i)(?:trx|TID)=([A-Za-z0-9]+)").find(body)
        if (mTrx != null) return mTrx.groupValues[1].trim()

        // 4. "id=FT26268AAAAA48220032"
        val mId = rx("(?i)\\?id=([A-Za-z0-9]+)").find(body)
        if (mId != null) return mId.groupValues[1].trim()

        // 5. "/receipt/DIP900AA01" or "receipt/(v2-[A-Za-z0-9\\-_]+)" or "/(v2-[A-Za-z0-9\\-_]+)"
        val mReceipt = rx("(?i)/receipt/([A-Za-z0-9]+)").find(body)
        if (mReceipt != null) return mReceipt.groupValues[1].trim()

        val mV2 = rx("(v2-[A-Za-z0-9\\-_]+)").find(body)
        if (mV2 != null) return mV2.groupValues[1].trim()

        // 6. "Ref: FT2626800020"
        val mRef = rx("(?i)Ref:\\s*([A-Za-z0-9]+)").find(body)
        if (mRef != null) return mRef.groupValues[1].trim()

        // 7. General FT / DIP / DIN transaction code
        val mCode = rx("\\b(FT[0-9A-Z]{8,20}|DIP[0-9A-Z]{6,14}|DIN[0-9A-Z]{6,14})\\b").find(body)
        if (mCode != null) return mCode.groupValues[1].trim()

        return null
    }

    /**
     * Robust balance extractor across all Ethiopian banks and wallets
     */
    fun extractBalance(body: String): Double? {
        // 1. "Your current E-Money Account balance is ETB 9,011.72"
        val mEMoney = rx("(?i)current E-Money Account\\s*balance is\\s*(?:ETB\\s*)?([\\d,]+(?:\\.\\d{1,2})?)").find(body)
        if (mEMoney != null) return cleanDouble(mEMoney.groupValues[1])

        // 2. "Your CBE Birr account balance is 37.32Br"
        val mCbeBirrBal = rx("(?i)CBE Birr account balance is\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:Br\\.|Br|ETB)?").find(body)
        if (mCbeBirrBal != null) return cleanDouble(mCbeBirrBal.groupValues[1])

        // 3. "Available Balance: ETB 402,381.24"
        val mAvail = rx("(?i)Available Balance:\\s*(?:ETB\\s*)?([\\d,]+(?:\\.\\d{1,2})?)").find(body)
        if (mAvail != null) return cleanDouble(mAvail.groupValues[1])

        // 4. "Your Current Balance is ETB 10,047.96" or "Your current balance is ETB34,002.01"
        val mCurBal = rx("(?i)current balance is\\s*(?:ETB\\s*)?([\\d,]+(?:\\.\\d{1,2})?)").find(body)
        if (mCurBal != null) return cleanDouble(mCurBal.groupValues[1])

        // 5. "Current Bal: ETB 823,000.80"
        val mBalShort = rx("(?i)Current Bal:\\s*(?:ETB\\s*)?([\\d,]+(?:\\.\\d{1,2})?)").find(body)
        if (mBalShort != null) return cleanDouble(mBalShort.groupValues[1])

        // 6. Amharic "ቀሪ ሂሳብዎ ብር 9,120.72 ነው"
        val mAmharic = rx("ቀሪ\\s*ሂሳብ\\S*\\s*(?:ብር|ETB)?\\s*([\\d,]+(?:\\.\\d{1,2})?)").find(body)
        if (mAmharic != null) return cleanDouble(mAmharic.groupValues[1])

        // 7. Generic "balance is ETB 1,000.00" / "balance: ETB 1,000.00"
        val mGen = rx("(?i)balance\\s*(?:is|:)?\\s*(?:ETB\\s*)?([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:ETB|Br\\.|Br)?").find(body)
        if (mGen != null) return cleanDouble(mGen.groupValues[1])

        return null
    }

    /**
     * Extracts user name from "Dear Hana", "Dear NAHOM,", "Dear Nahom Tadele Worku"
     */
    fun extractUserName(body: String): String? {
        val m = rx("(?i)Dear\\s+([A-Za-z\\s]+?)(?:,|\nyou|\nYou|\\s+your|\\s+Your|\\s+you|\\s+You)").find(body)
        return m?.groupValues?.get(1)?.trim()?.ifBlank { null }
    }

    fun extractDate(raw: String): String? {
        // 1. Try ISO yyyy-MM-dd or yyyy/MM/dd first
        val isoMatch = rx("(\\d{4}[/\\-\\.]\\d{1,2}[/\\-\\.]\\d{1,2})").find(raw)
        if (isoMatch != null) {
            val str = isoMatch.groupValues[1]
            val parts = str.split('/', '-', '.')
            if (parts.size == 3) {
                val y = parts[0].toIntOrNull() ?: 2026
                val m = parts[1].toIntOrNull() ?: 1
                val d = parts[2].toIntOrNull() ?: 1
                return String.format(Locale.US, "%02d/%02d/%04d", d, m, y)
            }
        }
        // 2. Try dd/MM/yyyy or dd-MM-yyyy or dd.MM.yyyy
        val dmyMatch = rx("(\\d{1,2}[/\\-\\.]\\d{1,2}[/\\-\\.]\\d{2,4})").find(raw)
        if (dmyMatch != null) {
            val str = dmyMatch.groupValues[1]
            val parts = str.split('/', '-', '.')
            if (parts.size == 3) {
                val d = parts[0].toIntOrNull() ?: 1
                val m = parts[1].toIntOrNull() ?: 1
                var y = parts[2].toIntOrNull() ?: 2026
                if (y < 100) y += 2000
                return String.format(Locale.US, "%02d/%02d/%04d", d, m, y)
            }
        }
        // 3. Try named month e.g. "25 Sep 2026" or "Sep 25, 2026"
        val namedMonthMatch = rx("(?i)(\\d{1,2})\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+(\\d{2,4})").find(raw)
        if (namedMonthMatch != null) {
            val d = namedMonthMatch.groupValues[1].toIntOrNull() ?: 1
            val mStr = namedMonthMatch.groupValues[2].lowercase(Locale.ROOT)
            var y = namedMonthMatch.groupValues[3].toIntOrNull() ?: 2026
            if (y < 100) y += 2000
            val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
            val m = months.indexOf(mStr) + 1
            if (m in 1..12) {
                return String.format(Locale.US, "%02d/%02d/%04d", d, m, y)
            }
        }
        return null
    }

    fun extractTime(raw: String): String? {
        val timeMatch = rx("(\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*(?i)[ap]m)?)").find(raw)
        if (timeMatch != null) {
            val str = timeMatch.groupValues[1].trim()
            val isPm = str.endsWith("pm", ignoreCase = true)
            val isAm = str.endsWith("am", ignoreCase = true)
            if (isPm || isAm) {
                val clean = str.substring(0, str.length - 2).trim()
                val parts = clean.split(':')
                if (parts.isNotEmpty()) {
                    var h = parts[0].toIntOrNull() ?: 0
                    if (isPm && h < 12) h += 12
                    if (isAm && h == 12) h = 0
                    val min = if (parts.size > 1) parts[1] else "00"
                    val sec = if (parts.size > 2) parts[2] else "00"
                    return String.format(Locale.US, "%02d:%s:%s", h, min, sec)
                }
            }
            val parts = str.split(':')
            if (parts.size == 2) {
                val h = parts[0].toIntOrNull() ?: 0
                val m = parts[1].toIntOrNull() ?: 0
                return String.format(Locale.US, "%02d:%02d:00", h, m)
            }
            if (parts.size >= 3) {
                val h = parts[0].toIntOrNull() ?: 0
                val m = parts[1].toIntOrNull() ?: 0
                val s = parts[2].toIntOrNull() ?: 0
                return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
            }
            return str
        }
        return null
    }

    /**
     * Converts the SMS date (e.g. "dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy") and time ("HH:mm:ss", "hh:mm a")
     * into exact epoch milliseconds.
     * Returns null if no valid date string is supplied.
     */
    fun parseTimestampFromDateTime(dateStr: String?, timeStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val cleanDate = dateStr.trim()
        val cleanTime = if (timeStr.isNullOrBlank()) "00:00:00" else timeStr.trim()
        val combined = "$cleanDate $cleanTime"

        val patterns = listOf(
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy hh:mm:ss a",
            "dd/MM/yyyy hh:mm a",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd hh:mm:ss a",
            "yyyy-MM-dd hh:mm a",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm",
            "dd-MM-yyyy hh:mm a",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "dd/MM/yy HH:mm:ss",
            "dd/MM/yy HH:mm",
            "dd/MM/yy hh:mm a",
            "dd MMM yyyy HH:mm:ss",
            "dd MMM yyyy HH:mm",
            "dd-MMM-yyyy HH:mm:ss",
            "dd-MMM-yyyy HH:mm"
        )

        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.isLenient = true
                val date = sdf.parse(combined)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }

        // Try date alone if combined with time fails
        val dateOnlyPatterns = listOf(
            "dd/MM/yyyy",
            "yyyy-MM-dd",
            "dd-MM-yyyy",
            "yyyy/MM/dd",
            "dd/MM/yy",
            "dd MMM yyyy",
            "dd-MMM-yyyy"
        )
        for (pattern in dateOnlyPatterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.isLenient = true
                val date = sdf.parse(cleanDate)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }

        return null
    }
}
