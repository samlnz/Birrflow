package com.example.ui.viewmodel

import com.example.data.entity.CategoryEntity
import com.example.data.entity.FlowDirection
import com.example.data.entity.IncomeSourceEntity
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DashboardChartsTest {

    private val now: Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 11, 15, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val day = 86_400_000L

    private fun tx(
        id: String,
        amount: Double,
        ts: Long,
        type: TransactionType,
        direction: FlowDirection,
        categoryId: String? = null,
        fee: Double = 0.0
    ) = TransactionEntity(
        transactionId = id,
        phoneId = "P1",
        financialAccountId = "telebirr",
        sourceId = "telebirr",
        rawSmsId = id,
        transactionType = type,
        amount = amount,
        fee = fee,
        transactionDate = "",
        transactionTime = "",
        categoryId = categoryId,
        createdTimestamp = ts,
        direction = direction
    )

    @Test
    fun bucketsDailyFlowsAndRunningNet() {
        val food = CategoryEntity("cat_food", "Food", "restaurant", "#F59E0B")
        val shop = IncomeSourceEntity("inc_shop", "Retail Shop", "#10B981")
        val txs = listOf(
            tx("in-today", 1_000.0, now, TransactionType.INCOME, FlowDirection.CREDIT, "inc_shop"),
            tx("out-today", 200.0, now - 3_600_000L, TransactionType.EXPENSE, FlowDirection.DEBIT, "cat_food", fee = 5.0),
            tx("out-2d", 300.0, now - 2 * day, TransactionType.EXPENSE, FlowDirection.DEBIT),
            tx("internal", 5_000.0, now - day, TransactionType.INTERNAL_TRANSFER, FlowDirection.DEBIT, fee = 10.0),
            tx("too-old", 9_999.0, now - 30 * day, TransactionType.INCOME, FlowDirection.CREDIT)
        )

        val data = MainViewModel.computeDashboardCharts(txs, listOf(food), listOf(shop), ChartRange.DAYS_7, now)

        assertEquals(7, data.buckets.size)
        val today = data.buckets.last()
        assertEquals(1_000.0, today.inflow, 0.001)
        assertEquals(205.0, today.outflow, 0.001)
        assertEquals(10.0, data.buckets[5].outflow, 0.001) // internal transfer counts only its fee
        assertEquals(300.0, data.buckets[4].outflow, 0.001)
        assertEquals(1_000.0, data.totalInflow, 0.001)
        assertEquals(1_000.0 - 515.0, data.cumulativeNet.last(), 0.001)

        assertEquals(listOf("Retail Shop"), data.incomeBySource.map { it.name })
        assertTrue(data.spending.any { it.isUncategorized && it.totalAmount == 300.0 })
        assertTrue(data.spending.any { it.category.categoryId == "cat_food" && it.totalAmount == 205.0 })
    }

    @Test
    fun monthlyRangeHasOneBucketPerMonth() {
        val data = MainViewModel.computeDashboardCharts(emptyList(), emptyList(), emptyList(), ChartRange.MONTHS_6, now)
        assertEquals(listOf("May", "Jun", "Jul", "Aug", "Sep", "Oct"), data.buckets.map { it.label })
        assertTrue(!data.hasData)
    }
}
