package com.example.ai

import com.example.data.entity.CategoryEntity
import com.example.data.entity.FlowDirection
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorIntelligenceTest {

    private val now = 1_760_000_000_000L
    private val day = 24L * 3600 * 1000

    private fun debit(id: String, amount: Double, daysAgo: Int, merchant: String?, categoryId: String? = null) = TransactionEntity(
        transactionId = id,
        phoneId = "P1",
        financialAccountId = "cbe",
        sourceId = "cbe",
        rawSmsId = id,
        transactionType = TransactionType.EXPENSE,
        amount = amount,
        transactionDate = "",
        transactionTime = "",
        merchant = merchant,
        categoryId = categoryId,
        createdTimestamp = now - daysAgo * day,
        direction = FlowDirection.DEBIT
    )

    private fun credit(id: String, amount: Double, daysAgo: Int) = TransactionEntity(
        transactionId = id,
        phoneId = "P1",
        financialAccountId = "cbe",
        sourceId = "cbe",
        rawSmsId = id,
        transactionType = TransactionType.INCOME,
        amount = amount,
        transactionDate = "",
        transactionTime = "",
        sender = "Employer",
        createdTimestamp = now - daysAgo * day,
        direction = FlowDirection.CREDIT
    )

    @Test
    fun detectsMonthlyRentAsRecurring() {
        val rent = CategoryEntity("cat_rent", "Rent", "home", "#EC4899", description = "House rent paid to landlord")
        val txs = listOf(
            debit("r1", 8_000.0, 90, "Landlord Abebe", "cat_rent"),
            debit("r2", 8_000.0, 60, "Landlord Abebe", "cat_rent"),
            debit("r3", 8_100.0, 30, "Landlord Abebe", "cat_rent"),
            debit("c1", 120.0, 50, "Kaldi's Coffee"),
            debit("c2", 95.0, 3, "Kaldi's Coffee")
        )
        val recurring = AdvisorIntelligence.detectRecurring(txs, mapOf("cat_rent" to "Rent"), now)
        assertEquals(1, recurring.size)
        val r = recurring.single()
        assertEquals("Landlord Abebe", r.counterparty)
        assertEquals(30, r.cadenceDays)
        assertEquals("Rent", r.categoryName)
        assertTrue(r.nextExpectedMillis > now - day)
        assertTrue(rent.description.isNotBlank())
    }

    @Test
    fun forecastWarnsWhenCashTrendsToZero() {
        // Spends 1,000 a day for 30 days with no income and 20,000 left
        val txs = (1..30).map { debit("d$it", 1_000.0, it, "Shop $it") }
        val ctx = AdvisorIntelligence.analyze(txs, emptyList(), emptyList(), emptyList(), AdvisorCapitalPlan(), 20_000.0, now)
        val forecast = ctx.forecast
        assertEquals(-1_000.0, forecast.averageDailyNet, 1.0)
        assertNotNull(forecast.daysUntilEmpty)
        assertEquals(20, forecast.daysUntilEmpty)
        assertTrue(ctx.alerts.first().severity == AlertSeverity.CRITICAL)
        assertEquals(30, ctx.uncategorizedCount)
    }

    @Test
    fun flagsUnusuallyLargeDebit() {
        val normal = (1..20).map { debit("n$it", 200.0 + it, it * 3, "Market") }
        val big = debit("big", 25_000.0, 2, "Car Dealer")
        val anomalies = AdvisorIntelligence.detectAnomalies(normal + big, now)
        assertEquals("Car Dealer", anomalies.single().label)
    }

    @Test
    fun promptSectionCarriesFacts() {
        val txs = listOf(credit("s1", 30_000.0, 40), credit("s2", 30_000.0, 10), debit("d1", 5_000.0, 5, "Landlord"))
        val ctx = AdvisorIntelligence.analyze(txs, emptyList(), emptyList(), emptyList(), AdvisorCapitalPlan(), 50_000.0, now)
        val text = AdvisorIntelligence.toPromptSection(ctx)
        assertTrue(text.contains("Monthly history"))
        assertTrue(text.contains("Cash forecast"))
        assertTrue(text.contains("Uncategorized debits: 1"))
    }
}
