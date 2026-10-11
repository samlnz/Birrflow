package com.example.ai

import com.example.data.entity.WealthItemEntity
import com.example.data.entity.WealthKind
import org.junit.Assert.assertEquals
import org.junit.Test

class NetWorthTest {

    private val market = MarketSnapshot(listOf(MarketRate(MarketIndicators.GOLD_GRAM, "Gold", 12_000.0, "ETB per gram", "Oct 2026", "dealer")))

    @Test
    fun valuesGoldSharesAndDebts() {
        val items = listOf(
            WealthItemEntity("g", WealthKind.GOLD, "Gold", quantity = 10.0),
            WealthItemEntity("s", WealthKind.SHARES, "Awash shares", quantity = 50.0, unitPriceEtb = 1_000.0),
            WealthItemEntity("e", WealthKind.EQUB, "Office equb", quantity = 20_000.0),
            WealthItemEntity("l", WealthKind.LOAN, "Bank loan", quantity = 100_000.0, ratePct = 18.0),
            WealthItemEntity("x", WealthKind.PROPERTY, "Old plot", quantity = 1_000_000.0, isDeleted = true)
        )
        val summary = NetWorthCalculator.summarize(50_000.0, items, market)
        assertEquals(120_000.0 + 50_000.0 + 20_000.0, summary.otherAssetsTotal, 0.001)
        assertEquals(100_000.0, summary.liabilitiesTotal, 0.001)
        assertEquals(50_000.0 + 190_000.0 - 100_000.0, summary.netWorth, 0.001)
        assertEquals(18.0, NetWorthCalculator.highestDebtRatePct(items)!!, 0.0)
    }
}
