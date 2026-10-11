package com.example.sync

import com.example.data.entity.CategoryEntity
import com.example.data.entity.FlowDirection
import com.example.data.entity.ManualSourceEntity
import com.example.data.entity.ManualSourceKind
import com.example.data.entity.RegisteredPhoneEntity
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransactionType
import com.example.data.entity.VerificationStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SyncDecryptException(message: String) : Exception(message)

/** Reconciliation rows of one account, replaced as a whole on the other devices. */
data class ReconSet(
    val phoneId: String,
    val accountId: String,
    val rows: List<TransactionEntity>
)

/**
 * Encoding and end-to-end encryption for cloud sync.
 *
 * All phones of one person share a secret link code. From it we derive:
 *  - the group id (a hash; it names the cloud folder but cannot be reversed into the code),
 *  - a join proof the server checks before letting a phone in (it cannot derive the key from it),
 *  - the AES-256 key that encrypts every record. The server only ever stores ciphertext,
 *    so bank data is unreadable to anyone without the code. Raw SMS texts are never uploaded.
 */
object SyncCodec {

    private const val IV_LEN = 12
    private const val PBKDF2_ITERATIONS = 120_000
    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O/1/I confusion
    private const val LINK_CODE_LEN = 16 // 16 chars from 32 symbols = 80 bits
    private const val PIN_ALPHABET = "0123456789"
    private const val PIN_LEN = 6

    // ---------------------------------------------------------------- link code

    fun newLinkCode(random: SecureRandom = SecureRandom()): String {
        // Generates 6-digit sync link code formatted as "XXX-XXX" (e.g. "839-214")
        val d1 = random.nextInt(900) + 100
        val d2 = random.nextInt(900) + 100
        return "$d1-$d2"
    }

    fun projectKeyFor(projectId: String): ByteArray {
        val clean = projectId.trim().lowercase(java.util.Locale.ROOT).ifEmpty { "default_birrflow_mesh" }
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(clean.toByteArray(Charsets.UTF_8))
    }
    fun new16CharLinkCode(random: SecureRandom = SecureRandom()): String =
        (0 until LINK_CODE_LEN).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }
            .chunked(4).joinToString("-") { it.joinToString("") }

    fun normalizeCode(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }

    fun isValidLinkCode(code: String): Boolean {
        val n = normalizeCode(code)
        return (n.length == 6 && n.all { it.isDigit() }) || (n.length == 16 && n.all { it in CODE_ALPHABET })
    }

    fun formatCode(code: String): String {
        val n = normalizeCode(code)
        return if (n.length == 6) {
            "${n.take(3)}-${n.takeLast(3)}"
        } else {
            n.chunked(4).joinToString("-")
        }
    }

    fun groupIdFor(code: String): String =
        toHex(MessageDigest.getInstance("SHA-256").digest("birrflow-group|${normalizeCode(code)}".toByteArray())).take(32)

    fun joinProofFor(code: String): String =
        toHex(deriveKey(code, "birrflow-join|${groupIdFor(code)}".toByteArray()))

    fun groupKeyFor(code: String): ByteArray =
        deriveKey(code, "birrflow-key|${groupIdFor(code)}".toByteArray())

    /** Opaque, stable document id for a record (keyed hash, so ids reveal nothing about accounts). */
    fun documentId(key: ByteArray, kind: String, refId: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return toHex(mac.doFinal("$kind|$refId".toByteArray(Charsets.UTF_8))).take(40)
    }

    // ---------------------------------------------------------------- encryption

    /** AES-256-GCM; [aad] (the document id) binds the ciphertext to its document. */
    fun seal(plain: ByteArray, key: ByteArray, aad: String, random: SecureRandom = SecureRandom()): ByteArray {
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return iv + cipher.doFinal(gzip(plain))
    }

    fun open(sealed: ByteArray, key: ByteArray, aad: String): ByteArray {
        if (sealed.size < IV_LEN + 16) throw SyncDecryptException("Damaged sync record")
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed.copyOfRange(0, IV_LEN)))
            cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
            gunzip(cipher.doFinal(sealed, IV_LEN, sealed.size - IV_LEN))
        } catch (e: Exception) {
            throw SyncDecryptException("Sync record could not be decrypted (different link code?)")
        }
    }

    /** PBKDF2-HMAC-SHA256 implemented on Mac so it also works on Android 7 (API 24/25). */
    internal fun deriveKey(code: String, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(normalizeCode(code).toByteArray(Charsets.UTF_8), "HmacSHA256"))
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1)) // block index 1 -> 32 bytes, exactly an AES-256 key
        var u = mac.doFinal()
        val out = u.copyOf()
        for (i in 1 until iterations) {
            u = mac.doFinal(u)
            for (j in out.indices) out[j] = (out[j].toInt() xor u[j].toInt()).toByte()
        }
        return out
    }

    private val HEX = "0123456789abcdef".toCharArray()

    fun toHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0F]
        }
        return String(out)
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(bytes) }
        return bos.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }

    // ---------------------------------------------------------------- JSON

    fun reconToJson(r: ReconSet): JSONObject = JSONObject().apply {
        put("phoneId", r.phoneId)
        put("accountId", r.accountId)
        put("rows", JSONArray().apply { r.rows.forEach { put(txToJson(it)) } })
    }

    fun reconFromJson(o: JSONObject) = ReconSet(
        phoneId = o.getString("phoneId"),
        accountId = o.getString("accountId"),
        rows = o.optJSONArray("rows").objects().map { txFromJson(it) }
    )

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)
    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (isNull(key) || !has(key)) null else getDouble(key)
    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    fun phoneToJson(p: RegisteredPhoneEntity) = JSONObject().apply {
        put("phoneId", p.phoneId)
        put("userId", p.userId)
        put("friendlyName", p.friendlyName)
        put("modelName", p.modelName)
        put("phoneNumber", p.phoneNumber)
        put("registeredAt", p.registeredAt)
        put("assignedIncomeSourceId", p.assignedIncomeSourceId)
    }

    fun phoneFromJson(o: JSONObject) = RegisteredPhoneEntity(
        phoneId = o.getString("phoneId"),
        userId = o.optString("userId", "USER-001"),
        friendlyName = o.optString("friendlyName", o.getString("phoneId")),
        modelName = o.optString("modelName", ""),
        phoneNumber = o.optString("phoneNumber", ""),
        isCurrentDevice = false,
        registeredAt = o.optLong("registeredAt", 0L),
        assignedIncomeSourceId = o.optString("assignedIncomeSourceId").takeIf { !it.isNullOrBlank() }
    )

    fun txToJson(t: TransactionEntity) = JSONObject().apply {
        put("id", t.transactionId)
        put("userId", t.userId)
        put("phoneId", t.phoneId)
        put("accountId", t.financialAccountId)
        put("sourceId", t.sourceId)
        put("rawSmsId", t.rawSmsId)
        put("type", t.transactionType.name)
        put("amount", t.amount)
        put("currency", t.currency)
        put("date", t.transactionDate)
        put("time", t.transactionTime)
        putNullable("sender", t.sender)
        putNullable("recipient", t.recipient)
        putNullable("merchant", t.merchant)
        putNullable("ref", t.referenceNumber)
        putNullable("balanceAfter", t.balanceAfter)
        put("fee", t.fee)
        put("description", t.description)
        putNullable("categoryId", t.categoryId)
        putNullable("subcategory", t.subcategory)
        put("confidence", t.parsingConfidence.toDouble())
        put("verification", t.verificationStatus.name)
        put("parserVersion", t.parserVersion)
        put("createdTimestamp", t.createdTimestamp)
        putNullable("notes", t.userNotes)
        put("direction", t.direction.name)
        put("isReconciliation", t.isReconciliation)
        put("updatedAt", t.updatedAt)
    }

    fun txFromJson(o: JSONObject) = TransactionEntity(
        transactionId = o.getString("id"),
        userId = o.optString("userId", "USER-001"),
        phoneId = o.getString("phoneId"),
        financialAccountId = o.optString("accountId", o.getString("sourceId")),
        sourceId = o.getString("sourceId"),
        rawSmsId = o.optString("rawSmsId", ""),
        transactionType = enumOr(o.optString("type"), TransactionType.UNKNOWN),
        amount = o.getDouble("amount"),
        currency = o.optString("currency", "ETB"),
        transactionDate = o.optString("date", ""),
        transactionTime = o.optString("time", ""),
        sender = o.optStringOrNull("sender"),
        recipient = o.optStringOrNull("recipient"),
        merchant = o.optStringOrNull("merchant"),
        referenceNumber = o.optStringOrNull("ref"),
        balanceAfter = o.optDoubleOrNull("balanceAfter"),
        fee = o.optDouble("fee", 0.0),
        description = o.optString("description", ""),
        categoryId = o.optStringOrNull("categoryId"),
        subcategory = o.optStringOrNull("subcategory"),
        parsingConfidence = o.optDouble("confidence", 1.0).toFloat(),
        verificationStatus = enumOr(o.optString("verification"), VerificationStatus.VALID),
        parserVersion = o.optInt("parserVersion", 1),
        createdTimestamp = o.optLong("createdTimestamp"),
        userNotes = o.optStringOrNull("notes"),
        direction = enumOr(o.optString("direction"), FlowDirection.UNKNOWN),
        isReconciliation = o.optBoolean("isReconciliation", false),
        updatedAt = o.optLong("updatedAt", 0L)
    )

    fun manualToJson(m: ManualSourceEntity) = JSONObject().apply {
        put("id", m.manualSourceId)
        put("name", m.name)
        put("kind", m.kind.name)
        put("balance", m.balance)
        putNullable("phoneId", m.phoneId)
        put("note", m.note)
        put("colorHex", m.colorHex)
        put("isDeleted", m.isDeleted)
        put("createdAt", m.createdAt)
        put("updatedAt", m.updatedAt)
    }

    fun manualFromJson(o: JSONObject) = ManualSourceEntity(
        manualSourceId = o.getString("id"),
        name = o.optString("name", "Cash"),
        kind = enumOr(o.optString("kind"), ManualSourceKind.OTHER),
        balance = o.optDouble("balance", 0.0),
        phoneId = o.optStringOrNull("phoneId"),
        note = o.optString("note", ""),
        colorHex = o.optString("colorHex", "#F59E0B"),
        isDeleted = o.optBoolean("isDeleted", false),
        createdAt = o.optLong("createdAt", 0L),
        updatedAt = o.optLong("updatedAt", 0L)
    )

    fun categoryToJson(c: CategoryEntity) = JSONObject().apply {
        put("id", c.categoryId)
        put("name", c.name)
        put("icon", c.iconName)
        put("color", c.colorHex)
        put("isCustom", c.isCustom)
        put("isActive", c.isActive)
        put("description", c.description)
    }

    fun categoryFromJson(o: JSONObject) = CategoryEntity(
        categoryId = o.getString("id"),
        name = o.optString("name", "Category"),
        iconName = o.optString("icon", "category"),
        colorHex = o.optString("color", "#64748B"),
        isCustom = o.optBoolean("isCustom", true),
        isActive = o.optBoolean("isActive", true),
        description = o.optString("description", "")
    )

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        try {
            if (name.isNullOrEmpty()) default else enumValueOf<T>(name)
        } catch (_: Exception) {
            default
        }
}
