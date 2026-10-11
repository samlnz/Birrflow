package com.example.ai

import com.example.data.entity.CategoryEntity
import com.example.data.entity.FlowDirection
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionTrackerTest {

    private val day = 24L * 3600 * 1000
    private val start = 1_760_000_000_000L
    private val food = CategoryEntity("cat_food", "Food", "restaurant", "#F59E0B")

    private fun debit(id: String, amount: Double, ts: Long, categoryId: String? = "cat_food", merchant: String? = null) = TransactionEntity(
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
        createdTimestamp = ts,
        direction = FlowDirection.DEBIT
    )

    @Test
    fun measuresSpendingCutAgainstBaseline() {
        // 9,000 of food in the 90 days before the plan = 3,000/month; 1,000 in the 30 days after
        val before = (1..9).map { debit("b$it", 1_000.0, start - it * 9 * day) }
        val after = listOf(debit("a1", 1_000.0, start + 5 * day))
        val plan = AdvisorCapitalPlan(
            milestones = listOf(
                PlanMilestone("m1", "Cut food spending", "", targetBenefitEtb = 1_500.0, deadlineMillis = start + 60 * day, trackCategory = "Food", startedAt = start)
            )
        )
        val evaluated = DecisionTracker.evaluate(plan, before + after, listOf(food), start + 30 * day).milestones.single()
        assertEquals(3_000.0, evaluated.baselineMonthlyEtb!!, 0.001)
        assertEquals(2_000.0, evaluated.autoOutcomeEtb!!, 0.001)
        assertEquals(MilestoneStatus.COMPLETED, evaluated.status)

        val record = DecisionTracker.trackRecord(plan.copy(milestones = listOf(evaluated)))
        assertEquals(1, record.completed)
        assertEquals(2_000.0, record.measuredGainEtb, 0.001)
    }

    @Test
    fun detectsActionByKeyword() {
        val plan = AdvisorCapitalPlan(
            milestones = listOf(PlanMilestone("m2", "Open term deposit at Awash", "", 1_000.0, start + 30 * day, trackKeyword = "Awash", startedAt = start))
        )
        val txs = listOf(debit("t1", 20_000.0, start + 2 * day, null, merchant = "Awash Bank Term Deposit"))
        val m = DecisionTracker.evaluate(plan, txs, listOf(food), start + 3 * day).milestones.single()
        assertEquals(MilestoneStatus.IN_PROGRESS, m.status)
        assertEquals(20_000.0, m.autoOutcomeEtb!!, 0.001)
    }

    @Test
    fun briefingReportsNetWorthChangeAndBestOption() {
        val market = MarketSnapshot(listOf(MarketRate(MarketIndicators.INFLATION, "Inflation", 13.9, "%", "June 2026", "ESS")))
        val ranked = InvestmentCatalog.rank(market, 50_000.0, RankingPreferences(businessMarginPct = 10.0, businessTurnsPerYear = 4.0))
        val briefing = WeeklyBriefingBuilder.build(
            nowMillis = start + 7 * day,
            netWorth = 110_000.0,
            previous = NetWorthPoint(start, 100_000.0),
            weekInflow = 20_000.0,
            weekOutflow = 10_000.0,
            deep = AdvisorDeepContext(),
            ranked = ranked,
            market = market
        )
        assertEquals(10_000.0, briefing.netWorthChange!!, 0.001)
        assertTrue(briefing.title.startsWith("Net worth up"))
        assertTrue(briefing.lines.any { it.startsWith("Best place for idle cash: Working capital") })
    }
}
