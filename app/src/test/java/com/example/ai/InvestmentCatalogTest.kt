package com.example.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentCatalogTest {

    private val market = MarketSnapshot(
        listOf(
            MarketRate(MarketIndicators.INFLATION, "Inflation", 13.9, "%", "June 2026", "ESS"),
            MarketRate(MarketIndicators.TBILL_AVG, "T-bill", 9.2, "%", "08/07/2026", "MoF"),
            MarketRate(MarketIndicators.TERM_DEPOSIT_1Y, "Term deposit", 12.0, "%", "Oct 2026", "you", origin = MarketOrigin.USER),
            MarketRate(MarketIndicators.INTEREST_TAX, "Tax", 5.0, "%", "2026", "you", origin = MarketOrigin.USER)
        )
    )

    @Test
    fun realReturnSubtractsTaxAndInflation() {
        // 12% taxed at 5% = 11.4% net; (1.114 / 1.139) - 1 = -2.19%
        assertEquals(-2.19, InvestmentCatalog.realReturnPct(12.0, 5.0, 0.0, 13.9), 0.01)
        assertEquals(-12.2, InvestmentCatalog.realReturnPct(0.0, 0.0, 0.0, 13.9), 0.05)
    }

    @Test
    fun cashLosesValueAndTbillIsRankedByRealReturn() {
        val ranked = InvestmentCatalog.rank(market, 100_000.0)
        val cash = ranked.first { it.option.id == "cash" }
        val tbill = ranked.first { it.option.id == "tbill" }
        assertTrue(cash.realPct!! < 0)
        assertEquals(9.2, tbill.nominalPct!!, 0.0)
        assertTrue(tbill.realYearlyEtb!! < 0)
        // Business without numbers is unknown and not suitable
        val business = ranked.first { it.option.id == "own_business" }
        assertNull(business.realPct)
        assertFalse(business.eligible)
    }

    @Test
    fun respectsInterestFreeRiskAndBusinessNumbers() {
        val prefs = RankingPreferences(interestFree = true, maxRisk = 2, businessMarginPct = 15.0, businessTurnsPerYear = 6.0)
        val ranked = InvestmentCatalog.rank(market, 100_000.0, prefs)
        assertFalse(ranked.first { it.option.id == "term_deposit" }.eligible)
        assertFalse(ranked.first { it.option.id == "esx_shares" }.eligible)
        assertTrue(ranked.first { it.option.id == "mudarabah" }.eligible)
        val top = ranked.first()
        assertEquals("own_business", top.option.id)
        assertEquals(90.0, top.nominalPct!!, 0.0)
    }
}
