package com.example.ai

import com.example.data.entity.CategoryEntity
import com.example.data.entity.FlowDirection
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransactionType
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric provides the real org.json implementation (plain JVM unit tests only get Android stubs)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdvisorAgentTest {

    private val now = 1_760_000_000_000L
    private val day = 24L * 3600 * 1000

    private fun tx(id: String, amount: Double, daysAgo: Int, direction: FlowDirection, merchant: String? = null, categoryId: String? = null) =
        TransactionEntity(
            transactionId = id,
            phoneId = "P1",
            financialAccountId = "telebirr",
            sourceId = "telebirr",
            rawSmsId = id,
            transactionType = if (direction == FlowDirection.CREDIT) TransactionType.INCOME else TransactionType.EXPENSE,
            amount = amount,
            transactionDate = "",
            transactionTime = "",
            merchant = merchant,
            categoryId = categoryId,
            createdTimestamp = now - daysAgo * day,
            direction = direction
        )

    private class RecordingActions : AdvisorActions {
        val categorized = mutableListOf<Pair<List<String>, String>>()
        override suspend fun addMilestone(
            title: String,
            description: String,
            targetBenefitEtb: Double,
            deadlineMillis: Long,
            trackCategory: String?,
            trackKeyword: String?
        ) = "M-1"
        override suspend fun updateMilestoneStatus(milestoneId: String, status: MilestoneStatus) = Unit
        override suspend fun createSavingsGoal(name: String, targetAmount: Double, targetDate: String, notes: String) = Unit
        override suspend fun categorizeTransactions(transactionIds: List<String>, categoryId: String): Int {
            categorized += transactionIds to categoryId
            return transactionIds.size
        }
        override suspend fun updateProfileField(field: String, value: String) = field in UserProfileService.AGENT_EDITABLE_FIELDS
        val remembered = mutableListOf<String>()
        override suspend fun remember(text: String, kind: MemoryKind): String {
            remembered += text
            return "mem${remembered.size}"
        }
        override suspend fun forget(memoryId: String) = memoryId == "mem1"
        val recorded = mutableListOf<com.example.data.entity.WealthItemEntity>()
        override suspend fun recordWealthItem(item: com.example.data.entity.WealthItemEntity): String {
            recorded += item
            return item.itemId
        }
    }

    private val food = CategoryEntity("cat_food", "Food", "restaurant", "#F59E0B", description = "Groceries and eating out")
    private val txs = listOf(
        tx("t1", 150.0, 1, FlowDirection.DEBIT, "Kaldi's Coffee"),
        tx("t2", 900.0, 2, FlowDirection.DEBIT, "Shoa Supermarket", "cat_food"),
        tx("t3", 5_000.0, 3, FlowDirection.CREDIT),
        tx("t4", 300.0, 60, FlowDirection.DEBIT, "Old Shop")
    )

    private fun executor(actions: AdvisorActions = RecordingActions()) = AdvisorToolExecutor(
        AdvisorDataView(txs, listOf(food), emptyList(), mapOf("P1" to "My phone"), mapOf("telebirr" to "Telebirr"), AdvisorCapitalPlan(), AdvisorDeepContext(), now),
        actions
    )

    @Test
    fun conversationStartsWithUserAndAlternates() {
        val history = listOf(
            AdvisorChatMessage("1", isUser = false, message = "Welcome"),
            AdvisorChatMessage("2", isUser = true, message = "Hi"),
            AdvisorChatMessage("3", isUser = false, message = "⚠️ Could not generate response"),
            AdvisorChatMessage("4", isUser = true, message = "Again")
        )
        val turns = AdvisorAgent.conversationTurns(history, "Now?")
        assertEquals(listOf("user"), turns.map { it.first })
        assertEquals("Hi\n\nAgain\n\nNow?", turns.single().second)
    }

    @Test
    fun findsUncategorizedDebitsInWindow() = runBlocking {
        val out = executor().execute("get_transactions", JSONObject().put("days_back", 30).put("only_uncategorized", true))
        val rows = out.result.getJSONArray("transactions")
        assertEquals(1, rows.length())
        assertEquals("t1", rows.getJSONObject(0).getString("id"))
        assertTrue(out.summary.contains("1 found"))
    }

    @Test
    fun categorizesOnlyKnownDebitsIntoExistingCategory() = runBlocking {
        val actions = RecordingActions()
        val out = executor(actions).execute(
            "set_transaction_category",
            JSONObject().put("transaction_ids", JSONArray(listOf("t1", "t3", "nope"))).put("category", "food")
        )
        assertEquals(listOf(listOf("t1") to "cat_food"), actions.categorized)
        assertEquals(1, out.result.getInt("categorized"))

        val missing = executor(actions).execute("set_transaction_category", JSONObject().put("transaction_ids", JSONArray(listOf("t1"))).put("category", "Travel"))
        assertTrue(missing.result.has("error"))
    }

    @Test
    fun spendingBreakdownIncludesUserDescriptions() = runBlocking {
        val out = executor().execute("get_spending_breakdown", JSONObject().put("days_back", 30))
        val rows = out.result.getJSONArray("categories")
        val foodRow = (0 until rows.length()).map { rows.getJSONObject(it) }.first { it.getString("category") == "Food" }
        assertEquals("Groceries and eating out", foodRow.getString("user_description"))
        assertEquals(1_050.0, out.result.getDouble("total_spent_etb"), 0.001)
    }

    @Test
    fun recordsGoldInTheLedger() = runBlocking {
        val actions = RecordingActions()
        val out = executor(actions).execute(
            "record_wealth_item",
            JSONObject().put("kind", "gold").put("name", "Wedding gold").put("quantity", 20.0)
        )
        assertTrue(out.result.getBoolean("ok"))
        assertEquals(com.example.data.entity.WealthKind.GOLD, actions.recorded.single().kind)
        assertEquals(20.0, actions.recorded.single().quantity, 0.0)
    }

    @Test
    fun declaresAllTools() {
        val names = AdvisorAgent.functionDeclarations().let { arr -> (0 until arr.length()).map { arr.getJSONObject(it).getString("name") } }
        assertTrue(names.containsAll(listOf("get_transactions", "set_transaction_category", "add_capital_plan_milestone", "forecast_balance")))
    }
}
