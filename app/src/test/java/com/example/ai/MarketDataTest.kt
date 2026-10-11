package com.example.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric provides the real org.json implementation (plain JVM unit tests only get Android stubs)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MarketDataTest {

    @Test
    fun parsesWebAnswerAndDropsImplausibleValues() {
        val text = """
            Here you go:
            ```json
            {"indicators":[
              {"key":"policy_rate","value":16.0,"as_of":"13/07/2026","source":"NBE","url":"https://nbe.gov.et"},
              {"key":"inflation_yoy","value":null,"as_of":"","source":""},
              {"key":"usd_official","value":5,"as_of":"today","source":"x"},
              {"key":"made_up","value":3,"as_of":"today","source":"x"},
              {"key":"gold_24k_gram","value":15000,"as_of":"Oct 2026","source":"Addis market"}
            ]}
            ```
        """.trimIndent()
        val rates = MarketDataService.parseWebIndicators(text, 1_000L)
        assertEquals(listOf("policy_rate", "gold_24k_gram"), rates.map { it.key })
        assertEquals(MarketOrigin.WEB, rates.first().origin)
    }

    @Test
    fun userValueReplacesStoredFigure() {
        val seeded = MarketSnapshot(MarketIndicators.SEED)
        val updated = MarketDataService.withUserValue(seeded, MarketIndicators.TERM_DEPOSIT_1Y, 11.5, "10/10/2026", "CBE quote")
        assertEquals(11.5, updated.value(MarketIndicators.TERM_DEPOSIT_1Y)!!, 0.0)
        assertEquals(MarketOrigin.USER, updated.get(MarketIndicators.TERM_DEPOSIT_1Y)!!.origin)
        assertTrue(updated.missing().none { it.key == MarketIndicators.TERM_DEPOSIT_1Y })
    }

    @Test
    fun promptMarksStaleFiguresAndListsUnknowns() {
        val snapshot = MarketSnapshot(MarketIndicators.SEED)
        val text = MarketDataService.toPromptSection(snapshot, nowMillis = 1_783_501_200_000L + 90L * 24 * 3600 * 1000)
        assertTrue(text.contains("T-bill average yield"))
        assertTrue(text.contains("STALE"))
        assertTrue(text.contains("Unknown right now"))
        assertTrue(text.contains("beats 13.9% inflation"))
    }
}
