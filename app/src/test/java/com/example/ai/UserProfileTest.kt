package com.example.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric provides the real org.json implementation (plain JVM unit tests only get Android stubs)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UserProfileTest {

    @Test
    fun roundTripsAndAppliesAgentEdits() {
        val profile = UserProfile(name = "Sara", interestFree = true, riskLevel = RiskLevel.BALANCED, dependents = 2, hasBusiness = true, businessMarginPct = 18.0)
        val restored = UserProfileService.fromJson(UserProfileService.toJson(profile))
        assertEquals(profile, restored)

        val edited = UserProfileService.withField(restored, "stockTurnsPerYear", "6")!!
        assertEquals(6.0, edited.stockTurnsPerYear!!, 0.0)
        assertNull(UserProfileService.withField(restored, "riskLevel", "AGGRESSIVE"))
        assertNull(UserProfileService.withField(restored, "dependents", "many"))
    }

    @Test
    fun profileDrivesRankingPreferences() {
        val prefs = UserProfile(interestFree = true, riskLevel = RiskLevel.CONSERVATIVE, maxLockMonths = 3)
            .rankingPreferences(hasDebt = false, debtRatePct = null)
        assertTrue(prefs.interestFree)
        assertEquals(1, prefs.maxRisk)
        assertEquals(90, prefs.maxLockDays)
        assertEquals(RiskLevel.GROWTH, RiskQuiz.levelFor(10))
    }

    @Test
    fun promptStatesInterestFreeAndMissingFields() {
        val text = UserProfileService.toPromptSection(UserProfile(interestFree = true, language = "Amharic"))
        assertTrue(text.contains("INTEREST-FREE ONLY"))
        assertTrue(text.contains("Answer in Amharic"))
        assertTrue(text.contains("Not known yet"))
    }
}
