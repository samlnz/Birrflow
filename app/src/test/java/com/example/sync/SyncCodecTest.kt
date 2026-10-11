package com.example.sync

import com.example.data.entity.FlowDirection
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransactionType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric provides the real org.json implementation (plain JVM unit tests only get Android stubs)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncCodecTest {

    private val tx = TransactionEntity(
        "TXN-1", phoneId = "DEV-A", financialAccountId = "cbe:0032", sourceId = "cbe", rawSmsId = "SMS-1",
        transactionType = TransactionType.EXPENSE, amount = 10.5, transactionDate = "01/01/2026", transactionTime = "10:00",
        balanceAfter = null, direction = FlowDirection.DEBIT, updatedAt = 5
    )

    @Test
    fun linkCodeDerivationsAreStableAndIndependent() {
        val code = SyncCodec.newLinkCode()
        assertTrue(SyncCodec.isValidLinkCode(code))
        assertTrue(SyncCodec.isValidLinkCode(code.lowercase().replace("-", " ")))
        assertEquals(SyncCodec.groupIdFor(code), SyncCodec.groupIdFor(code.lowercase()))
        assertEquals(64, SyncCodec.joinProofFor(code).length)
        // The proof stored on the server must not be the encryption key
        assertNotEquals(SyncCodec.joinProofFor(code), SyncCodec.toHex(SyncCodec.groupKeyFor(code)))
        assertFalse(SyncCodec.isValidLinkCode("ABCD-EFGH"))
    }

    @Test
    fun sealedRecordRoundTripAndTamperDetection() {
        val key = SyncCodec.groupKeyFor("ABCD-EFGH-JKLM-NPQR")
        val docId = SyncCodec.documentId(key, "TX", tx.transactionId)
        val plain = SyncCodec.txToJson(tx).toString().toByteArray()
        val sealed = SyncCodec.seal(plain, key, docId)

        val back = SyncCodec.txFromJson(JSONObject(String(SyncCodec.open(sealed, key, docId))))
        assertEquals(tx, back)

        // Wrong code, or ciphertext moved to another document -> rejected
        val otherKey = SyncCodec.groupKeyFor("QRST-UVWX-YZ23-4567")
        assertThrows(SyncDecryptException::class.java) { SyncCodec.open(sealed, otherKey, docId) }
        assertThrows(SyncDecryptException::class.java) { SyncCodec.open(sealed, key, "another-doc") }
    }

    @Test
    fun reconSetRoundTrip() {
        val set = ReconSet("DEV-A", "cbe:0032", listOf(tx.copy(transactionId = "RECON-1", isReconciliation = true)))
        assertEquals(set, SyncCodec.reconFromJson(SyncCodec.reconToJson(set)))
    }
}
