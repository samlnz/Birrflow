package com.example.ui.viewmodel

import com.example.data.entity.FlowDirection
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelTimeframeDeltasTest {

    @Test
    fun computeTimeframeDeltas_correctlyCategorizesHourlyDailyWeeklyMonthly() {
        val now = 1_700_000_000_000L // arbitrary fixed time

        // 1. Transaction 10 minutes ago (+1,000 ETB income)
        // Should appear in: Hourly, Daily, Weekly, Monthly
        val tx1 = createTx(
            id = "tx1",
            amount = 1000.0,
            fee = 0.0,
            type = TransactionType.INCOME,
            direction = FlowDirection.CREDIT,
            timestamp = now - 10 * 60 * 1000L
        )

        // 2. Transaction 30 minutes ago (-200 ETB expense, 5 ETB fee)
        // Should appear in: Hourly, Daily, Weekly, Monthly
        val tx2 = createTx(
            id = "tx2",
            amount = 200.0,
            fee = 5.0,
            type = TransactionType.EXPENSE,
            direction = FlowDirection.DEBIT,
            timestamp = now - 30 * 60 * 1000L
        )

        // 3. Transaction 5 hours ago (+500 ETB income)
        // Should appear in: Daily, Weekly, Monthly (NOT Hourly)
        val tx3 = createTx(
            id = "tx3",
            amount = 500.0,
            fee = 0.0,
            type = TransactionType.INCOME,
            direction = FlowDirection.CREDIT,
            timestamp = now - 5 * 3600 * 1000L
        )

        // 4. Transaction 3 days ago (-300 ETB expense)
        // Should appear in: Weekly, Monthly (NOT Hourly, NOT Daily)
        val tx4 = createTx(
            id = "tx4",
            amount = 300.0,
            fee = 0.0,
            type = TransactionType.EXPENSE,
            direction = FlowDirection.DEBIT,
            timestamp = now - 3 * 24 * 3600 * 1000L
        )

        // 5. Transaction 15 days ago (+2,000 ETB deposit)
        // Should appear in: Monthly (NOT Hourly, NOT Daily, NOT Weekly)
        val tx5 = createTx(
            id = "tx5",
            amount = 2000.0,
            fee = 0.0,
            type = TransactionType.DEPOSIT,
            direction = FlowDirection.CREDIT,
            timestamp = now - 15 * 24 * 3600 * 1000L
        )

        val txList = listOf(tx1, tx2, tx3, tx4, tx5)
        val deltas = MainViewModel.computeTimeframeDeltas(txList, now)

        // 1. Hourly: tx1 (+1000) and tx2 (-205) -> net +795
        assertEquals(1000.0, deltas.hourly.inflow, 0.01)
        assertEquals(205.0, deltas.hourly.outflow, 0.01)
        assertEquals(795.0, deltas.hourly.netChange, 0.01)
        assertTrue(deltas.hourly.isPositive)
        assertEquals("Increased", deltas.hourly.statusLabel)
        assertEquals(2, deltas.hourly.transactionCount)

        // 2. Daily: tx1 (+1000), tx2 (-205), tx3 (+500) -> in: 1500, out: 205, net: +1295
        assertEquals(1500.0, deltas.daily.inflow, 0.01)
        assertEquals(205.0, deltas.daily.outflow, 0.01)
        assertEquals(1295.0, deltas.daily.netChange, 0.01)
        assertTrue(deltas.daily.isPositive)
        assertEquals(3, deltas.daily.transactionCount)

        // 3. Weekly: tx1, tx2, tx3, tx4 -> in: 1500, out: 205 + 300 = 505, net: +995
        assertEquals(1500.0, deltas.weekly.inflow, 0.01)
        assertEquals(505.0, deltas.weekly.outflow, 0.01)
        assertEquals(995.0, deltas.weekly.netChange, 0.01)
        assertEquals(4, deltas.weekly.transactionCount)

        // 4. Monthly: all 5 -> in: 1500 + 2000 = 3500, out: 505, net: +2995
        assertEquals(3500.0, deltas.monthly.inflow, 0.01)
        assertEquals(505.0, deltas.monthly.outflow, 0.01)
        assertEquals(2995.0, deltas.monthly.netChange, 0.01)
        assertEquals(5, deltas.monthly.transactionCount)
    }

    @Test
    fun computeTimeframeDeltas_internalTransfersDoNotDistortNetExceptFees() {
        val now = 1_700_000_000_000L

        val internalTx = createTx(
            id = "internal1",
            amount = 5000.0,
            fee = 12.0,
            type = TransactionType.INTERNAL_TRANSFER,
            direction = FlowDirection.DEBIT,
            timestamp = now - 5 * 60 * 1000L
        )

        val deltas = MainViewModel.computeTimeframeDeltas(listOf(internalTx), now)

        // The 5000 ETB transfer is internal, so cash assets only decreased by the 12 ETB fee
        assertEquals(0.0, deltas.hourly.inflow, 0.01)
        assertEquals(12.0, deltas.hourly.outflow, 0.01)
        assertEquals(-12.0, deltas.hourly.netChange, 0.01)
        assertTrue(deltas.hourly.isNegative)
        assertEquals("Decreased", deltas.hourly.statusLabel)
    }

    @Test
    fun computeTimeframeDeltas_sumsAcrossAllDevices() {
        val now = 1_700_000_000_000L
        // Device 1 tx (+500 ETB 15 min ago)
        val dev1Tx = createTx("dev1-tx", 500.0, 0.0, TransactionType.INCOME, FlowDirection.CREDIT, now - 15 * 60 * 1000L).copy(phoneId = "phone-A")
        // Device 2 tx (+300 ETB 20 min ago)
        val dev2Tx = createTx("dev2-tx", 300.0, 0.0, TransactionType.INCOME, FlowDirection.CREDIT, now - 20 * 60 * 1000L).copy(phoneId = "phone-B")
        // Device 3 tx (-100 ETB 10 min ago)
        val dev3Tx = createTx("dev3-tx", 100.0, 0.0, TransactionType.EXPENSE, FlowDirection.DEBIT, now - 10 * 60 * 1000L).copy(phoneId = "phone-C")

        val deltas = MainViewModel.computeTimeframeDeltas(listOf(dev1Tx, dev2Tx, dev3Tx), nowMillis = now)
        assertEquals(3, deltas.hourly.transactionCount)
        assertEquals(800.0, deltas.hourly.inflow, 0.01)
        assertEquals(100.0, deltas.hourly.outflow, 0.01)
        assertEquals(700.0, deltas.hourly.netChange, 0.01)
    }

    private fun createTx(
        id: String,
        amount: Double,
        fee: Double,
        type: TransactionType,
        direction: FlowDirection,
        timestamp: Long
    ): TransactionEntity {
        val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(java.util.Date(timestamp))
        return TransactionEntity(
            transactionId = id,
            phoneId = "phone1",
            financialAccountId = "acc1",
            sourceId = "cbe",
            rawSmsId = "raw-$id",
            transactionType = type,
            amount = amount,
            fee = fee,
            transactionDate = dateStr,
            transactionTime = "12:00",
            direction = direction,
            createdTimestamp = timestamp
        )
    }
}
