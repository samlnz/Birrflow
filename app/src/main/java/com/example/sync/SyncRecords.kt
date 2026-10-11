package com.example.sync

import com.example.data.entity.CategoryEntity
import com.example.data.entity.ManualSourceEntity
import com.example.data.entity.RegisteredPhoneEntity
import com.example.data.entity.SyncOutboxEntity
import com.example.data.entity.TransactionEntity
import com.example.data.repository.FinancialRepository
import com.example.data.repository.RemoteChanges
import org.json.JSONObject

/** One encrypted cloud document: id, sending device, ciphertext. */
class SealedRecord(
    val docId: String,
    val deviceId: String,
    val payload: ByteArray
)

/**
 * Turns outbox entries into encrypted records and incoming records back into [RemoteChanges].
 * Transport-agnostic (no Firebase types), so it is unit-tested on the JVM.
 */
object SyncRecords {

    suspend fun seal(
        items: List<SyncOutboxEntity>,
        repository: FinancialRepository,
        key: ByteArray,
        deviceId: String,
        now: Long = System.currentTimeMillis()
    ): List<SealedRecord> {
        val byKind = items.groupBy { it.kind }
        val txById = repository.transactionsByIds(byKind[FinancialRepository.KIND_TX].orEmpty().map { it.refId }).associateBy { it.transactionId }
        val manualById = repository.manualSourcesByIds(byKind[FinancialRepository.KIND_MANUAL].orEmpty().map { it.refId }).associateBy { it.manualSourceId }

        val out = ArrayList<SealedRecord>(items.size)
        for (item in items) {
            val payload = JSONObject().put("k", item.kind).put("id", item.refId)
            when (item.kind) {
                FinancialRepository.KIND_TX -> {
                    val tx = txById[item.refId]
                    // A transaction that no longer exists is sent as a deletion
                    if (tx == null) payload.put("del", true).put("u", now)
                    else payload.put("u", tx.updatedAt).put("data", SyncCodec.txToJson(tx))
                }
                FinancialRepository.KIND_RECON -> {
                    val phoneId = item.refId.substringBefore('|')
                    val accountId = item.refId.substringAfter('|')
                    payload.put("data", SyncCodec.reconToJson(repository.reconSet(phoneId, accountId)))
                }
                FinancialRepository.KIND_MANUAL -> manualById[item.refId]?.let { payload.put("u", it.updatedAt).put("data", SyncCodec.manualToJson(it)) }
                FinancialRepository.KIND_PHONE -> repository.phoneById(item.refId)?.let { payload.put("data", SyncCodec.phoneToJson(it)) }
                FinancialRepository.KIND_CATEGORY -> repository.categoryById(item.refId)?.let { payload.put("data", SyncCodec.categoryToJson(it)) }
            }
            if (!payload.has("data") && !payload.has("del")) continue // row vanished; nothing to send
            val docId = SyncCodec.documentId(key, item.kind, item.refId)
            out += SealedRecord(docId, deviceId, SyncCodec.seal(payload.toString().toByteArray(Charsets.UTF_8), key, docId))
        }
        return out
    }

    /** Decrypts records from other phones; records from [localDeviceId] and undecryptable ones are skipped. */
    fun open(records: List<SealedRecord>, key: ByteArray, localDeviceId: String, onError: (String, Exception) -> Unit = { _, _ -> }): RemoteChanges {
        val phones = ArrayList<RegisteredPhoneEntity>()
        val txs = ArrayList<TransactionEntity>()
        val deleted = HashMap<String, Long>()
        val manual = ArrayList<ManualSourceEntity>()
        val categories = ArrayList<CategoryEntity>()
        val recon = ArrayList<ReconSet>()

        for (r in records) {
            if (r.deviceId == localDeviceId) continue
            try {
                val json = JSONObject(String(SyncCodec.open(r.payload, key, r.docId), Charsets.UTF_8))
                val data = json.optJSONObject("data")
                when (json.getString("k")) {
                    FinancialRepository.KIND_TX ->
                        if (json.optBoolean("del", false)) deleted[json.getString("id")] = json.optLong("u")
                        else if (data != null) txs += SyncCodec.txFromJson(data)
                    FinancialRepository.KIND_RECON -> if (data != null) recon += SyncCodec.reconFromJson(data)
                    FinancialRepository.KIND_MANUAL -> if (data != null) manual += SyncCodec.manualFromJson(data)
                    FinancialRepository.KIND_PHONE -> if (data != null) phones += SyncCodec.phoneFromJson(data)
                    FinancialRepository.KIND_CATEGORY -> if (data != null) categories += SyncCodec.categoryFromJson(data)
                }
            } catch (e: Exception) {
                onError(r.docId, e)
            }
        }
        return RemoteChanges(phones, txs, deleted, manual, categories, recon)
    }
}
