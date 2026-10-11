package com.example.ai

import com.example.data.entity.FlowDirection
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransactionType
import com.example.ui.viewmodel.CategorySpend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FinancialSnapshotServiceTest {

    @Test
    fun computeSnapshot_accuratelyCalculatesBalancesAndRunway() {
        val now = 1_700_000_000_000L // Fixed time
        val bankBalance = 80_000.0
        val manualBalance = 20_000.0

        val cal = Calendar.getInstance().apply { timeInMillis = now }

        val tx1 = createTx(
            id = "tx1",
            amount = 30_000.0,
            type = TransactionType.INCOME,
            direction = FlowDirection.CREDIT,
            timestamp = now - (2 * 3600 * 1000L) // Today
        )

        val tx2 = createTx(
            id = "tx2",
            amount = 10_000.0,
            fee = 20.0,
            type = TransactionType.EXPENSE,
            direction = FlowDirection.DEBIT,
            timestamp = now - (4 * 3600 * 1000L) // Today
        )

        val snapshot = FinancialSnapshotService.computeSnapshot(
            bankBalance = bankBalance,
            manualBalance = manualBalance,
            transactions = listOf(tx1, tx2),
            categorySpends = emptyList(),
            activeSourcesCount = 3,
            activePhonesCount = 2,
            nowMillis = now
        )

        assertEquals(100_000.0, snapshot.totalRecordedBalance, 0.001)
        assertEquals(30_000.0, snapshot.currentMonthIncome, 0.001)
        assertEquals(10_020.0, snapshot.currentMonthExpense, 0.001)
        assertEquals(19_980.0, snapshot.netCashFlow, 0.001)
        assertEquals(30_000.0, snapshot.inflowsToday, 0.001)
        assertEquals(10_020.0, snapshot.outflowsToday, 0.001)
        assertEquals(3, snapshot.activeSourcesCount)
        assertEquals(2, snapshot.activePhonesCount)
        assertTrue(snapshot.runwayMonths > 0)
        assertTrue(snapshot.emergencyReserveRequired >= 15_000.0)
    }

    @Test
    fun geminiConfig_masksKeysSafelyWithoutExposingSecrets() {
        val unconfigured = GeminiConfig(apiKey = "")
        assertEquals("No API key saved", unconfigured.maskedKey)

        val shortKey = GeminiConfig(apiKey = "12345")
        assertEquals("••••••••", shortKey.maskedKey)

        val standardKey = GeminiConfig(apiKey = "AIzaSyDk7284920485901234")
        assertEquals("••••••••••••1234", standardKey.maskedKey)
        assertFalse(standardKey.maskedKey.contains("AIzaSy"))
    }

    @Test
    fun supportedGeminiModels_returnsValidModernModels() {
        val models = SupportedGeminiModels.ALL
        assertTrue(models.any { it.id == SupportedGeminiModels.GEMINI_3_5_FLASH })
        assertTrue(models.any { it.id == SupportedGeminiModels.GEMINI_3_1_PRO })
        assertTrue(models.any { it.id == SupportedGeminiModels.GEMINI_3_1_FLASH_LITE })

        // Prohibited deprecated models check
        assertFalse(models.any { it.id.contains("1.5") })
        assertFalse(models.any { it.id.contains("2.0") })

        val found = SupportedGeminiModels.find(SupportedGeminiModels.GEMINI_3_5_FLASH)
        assertEquals("Gemini 3.5 Flash (Recommended)", found.displayName)
    }

    private fun createTx(
        id: String,
        amount: Double,
        fee: Double = 0.0,
        type: TransactionType,
        direction: FlowDirection,
        timestamp: Long
    ): TransactionEntity {
        return TransactionEntity(
            transactionId = id,
            phoneId = "phone-1",
            rawSmsId = "raw-1",
            financialAccountId = "acc-1",
            sourceId = "telebirr",
            transactionDate = "15/11/2023",
            transactionTime = "12:00:00",
            transactionType = type,
            amount = amount,
            fee = fee,
            currency = "ETB",
            direction = direction,
            createdTimestamp = timestamp
        )
    }
}
