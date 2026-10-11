package com.example.ai

import com.example.data.entity.CategoryEntity
import com.example.data.entity.IncomeSourceEntity
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransactionType
import com.example.data.entity.WealthItemEntity
import com.example.data.entity.WealthKind
import com.example.ui.components.formatCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Result of one tool call: JSON for the model and a one-line note shown under the chat answer. */
data class ToolOutcome(val result: JSONObject, val summary: String)

/** Changes the agent may make, only when the user asks for them in chat. */
interface AdvisorActions {
    suspend fun addMilestone(
        title: String,
        description: String,
        targetBenefitEtb: Double,
        deadlineMillis: Long,
        trackCategory: String?,
        trackKeyword: String?
    ): String
    suspend fun updateMilestoneStatus(milestoneId: String, status: MilestoneStatus)
    suspend fun createSavingsGoal(name: String, targetAmount: Double, targetDate: String, notes: String)
    suspend fun categorizeTransactions(transactionIds: List<String>, categoryId: String): Int
    suspend fun recordWealthItem(item: WealthItemEntity): String
    suspend fun updateProfileField(field: String, value: String): Boolean
    suspend fun remember(text: String, kind: MemoryKind): String
    suspend fun forget(memoryId: String): Boolean
}

/** Read-only view of the user's data taken when a chat message is sent. */
data class AdvisorDataView(
    val transactions: List<TransactionEntity>,
    val categories: List<CategoryEntity>,
    val incomeSources: List<IncomeSourceEntity>,
    val phoneNames: Map<String, String>,
    val bankNames: Map<String, String>,
    val plan: AdvisorCapitalPlan,
    val deep: AdvisorDeepContext,
    val nowMillis: Long = System.currentTimeMillis(),
    val market: MarketSnapshot = MarketSnapshot(),
    val deployableCash: Double = 0.0,
    val rankingPreferences: RankingPreferences = RankingPreferences(),
    val netWorth: NetWorthSummary = NetWorthSummary(),
    val profile: UserProfile = UserProfile(),
    val memory: AgentMemoryState = AgentMemoryState()
)

data class AgentReply(
    val text: String,
    val toolActions: List<String>,
    val sources: List<String>,
    val promptTokens: Int,
    val completionTokens: Int
)

/**
 * Tool-using advisor agent on the Gemini API: the model can query the user's transactions, trends and
 * plan, and (when asked) add milestones, create goals or categorize debits, over several rounds before
 * answering. In live-web mode it answers with Google Search grounding instead of the app tools.
 */
object AdvisorAgent {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val MAX_TOOL_ROUNDS = 6
    private const val DAY_MS = 24L * 3600 * 1000

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    const val AGENT_RULES = """
AGENT TOOLS & BEHAVIOUR:
• You can call tools to look up the user's real data. Prefer calling a tool over guessing; quote exact figures from tool results.
• Read-only tools: get_transactions, get_spending_breakdown, get_cash_flow_trend, get_recurring_payments, forecast_balance, get_capital_plan, get_market_rates, rank_investment_options, get_net_worth, suggest_categories, get_user_profile, recall_memory.
• Before recommending any deposit, T-bill, share, gold or FX move, call get_market_rates and quote figures with their as-of date.
• Action tools change the user's records: add_capital_plan_milestone, update_milestone_status, create_savings_goal, set_transaction_category, record_wealth_item.
  Call these ONLY when the user explicitly asks you to add, create, update, record or categorize something. Never as a side effect.
• When the user mentions an asset or debt the ledger lacks (equb, gold, shares, a loan, money lent out, stock, property), offer to record it with record_wealth_item.
• Learning tools you may use on your own: remember_memory / forget_memory (your notes about the user: durable facts, preferences, goals, constraints, lessons) and update_user_profile (save something the user just stated about themselves: work, family, goals, business margin, horizon...).
• When categorizing, call suggest_categories first (it learned from the user's own past choices), then use the user's category descriptions to decide; skip debits you are unsure about and say so.
• After using tools, answer in clear sections with concrete ETB amounts, then end with 2-3 short follow-up suggestions as bullets.
"""

    suspend fun chat(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<AdvisorChatMessage>,
        userQuery: String,
        tools: AdvisorToolExecutor,
        useWebSearch: Boolean
    ): AgentReply = withContext(Dispatchers.IO) {
        val contents = JSONArray()
        conversationTurns(history, userQuery).forEach { (role, text) -> contents.put(textContent(role, text)) }

        val actions = ArrayList<String>()
        var promptTokens = 0
        var completionTokens = 0

        for (round in 0..MAX_TOOL_ROUNDS) {
            val finalRound = round == MAX_TOOL_ROUNDS
            val body = JSONObject().apply {
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
                put("contents", contents)
                if (useWebSearch) {
                    put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                } else {
                    put("tools", JSONArray().put(JSONObject().put("functionDeclarations", functionDeclarations())))
                    if (finalRound) {
                        // Out of tool rounds: force a written answer from what was gathered
                        put("toolConfig", JSONObject().put("functionCallingConfig", JSONObject().put("mode", "NONE")))
                    }
                }
                put("generationConfig", JSONObject().put("temperature", 0.5).put("maxOutputTokens", 4096))
            }
            val json = post(apiKey, model, body)
            json.optJSONObject("usageMetadata")?.let {
                promptTokens += it.optInt("promptTokenCount", 0)
                completionTokens += it.optInt("candidatesTokenCount", 0)
            }
            val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
                ?: throw Exception("Gemini returned no answer.")
            val content = candidate.optJSONObject("content") ?: JSONObject().put("parts", JSONArray())
            val parts = content.optJSONArray("parts") ?: JSONArray()
            val calls = (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optJSONObject("functionCall") }

            if (calls.isEmpty() || useWebSearch) {
                val text = buildString {
                    for (i in 0 until parts.length()) {
                        val p = parts.optJSONObject(i) ?: continue
                        if (p.optBoolean("thought", false)) continue
                        append(p.optString("text", ""))
                    }
                }.trim()
                if (text.isEmpty()) {
                    val reason = candidate.optString("finishReason", "")
                    throw Exception(if (reason.isNotBlank()) "Gemini stopped without an answer ($reason)." else "Gemini returned an empty answer.")
                }
                return@withContext AgentReply(text, actions, groundingSources(candidate), promptTokens, completionTokens)
            }

            // Echo the model turn back unchanged (keeps thought signatures), then answer every call
            if (!content.has("role")) content.put("role", "model")
            contents.put(content)
            val responses = JSONArray()
            for (call in calls) {
                val name = call.optString("name")
                val args = call.optJSONObject("args") ?: JSONObject()
                val outcome = try {
                    tools.execute(name, args)
                } catch (e: Exception) {
                    ToolOutcome(JSONObject().put("error", e.message ?: "Tool failed"), "⚠️ $name failed: ${e.message}")
                }
                actions += outcome.summary
                responses.put(
                    JSONObject().put(
                        "functionResponse",
                        JSONObject().put("name", name).put("response", outcome.result)
                    )
                )
            }
            contents.put(JSONObject().put("role", "user").put("parts", responses))
        }
        throw Exception("The advisor could not finish its answer.")
    }

    /**
     * Earlier chat as alternating user/model turns that start with the user (what the API expects):
     * error bubbles are dropped and back-to-back turns of the same role are merged.
     */
    fun conversationTurns(history: List<AdvisorChatMessage>, userQuery: String): List<Pair<String, String>> {
        val turns = ArrayList<Pair<String, String>>()
        val recent = history.filter { it.message.isNotBlank() && !it.message.startsWith("⚠️") }.takeLast(12)
        for (m in recent.map { (if (it.isUser) "user" else "model") to it.message } + ("user" to userQuery)) {
            if (turns.isEmpty() && m.first == "model") continue
            val last = turns.lastOrNull()
            if (last != null && last.first == m.first) {
                turns[turns.lastIndex] = last.first to (last.second + "\n\n" + m.second)
            } else {
                turns += m
            }
        }
        return turns
    }

    private fun textContent(role: String, text: String) =
        JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", text)))

    /** POST with two retries on rate limits and transient server errors. */
    internal suspend fun post(apiKey: String, model: String, body: JSONObject): JSONObject {
        val endpoint = "$BASE_URL/$model:generateContent?key=$apiKey"
        var lastError: Exception? = null
        for (attempt in 0..2) {
            if (attempt > 0) delay(1_500L * attempt * attempt)
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string() ?: ""
                    if (response.isSuccessful) return JSONObject(text)
                    val message = GeminiClientService.describeError(response.code, text)
                    if (response.code != 429 && response.code < 500) throw NonRetryable(message)
                    lastError = Exception(message)
                }
            } catch (e: NonRetryable) {
                throw Exception(e.message)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: Exception("Gemini request failed.")
    }

    private class NonRetryable(message: String) : Exception(message)

    private fun groundingSources(candidate: JSONObject): List<String> {
        val chunks = candidate.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks") ?: return emptyList()
        return (0 until chunks.length()).mapNotNull { i ->
            val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: return@mapNotNull null
            val uri = web.optString("uri", "")
            if (uri.isBlank()) null else "${web.optString("title", "Source")} — $uri"
        }.distinct().take(6)
    }

    // ------------------------------------------------------------------
    // Tool declarations (OpenAPI subset understood by Gemini function calling)
    // ------------------------------------------------------------------

    private fun prop(type: String, description: String, options: List<String>? = null): JSONObject =
        JSONObject().put("type", type).put("description", description).apply {
            if (options != null) put("enum", JSONArray(options))
        }

    private fun fn(name: String, description: String, properties: Map<String, JSONObject> = emptyMap(), required: List<String> = emptyList()): JSONObject =
        JSONObject().put("name", name).put("description", description).apply {
            if (properties.isNotEmpty()) {
                val props = JSONObject()
                properties.forEach { (k, v) -> props.put(k, v) }
                put("parameters", JSONObject().put("type", "OBJECT").put("properties", props).apply {
                    if (required.isNotEmpty()) put("required", JSONArray(required))
                })
            }
        }

    fun functionDeclarations(): JSONArray = JSONArray().apply {
        put(fn(
            "get_transactions",
            "Search the user's bank and mobile-money transactions. Returns matching rows (newest first) with id, date, amount, direction, counterparty, category, bank and phone.",
            mapOf(
                "days_back" to prop("INTEGER", "How many days back to search (default 30, max 365)."),
                "direction" to prop("STRING", "Money in, money out, or both.", listOf("in", "out", "any")),
                "category" to prop("STRING", "Only this expense category or income source name."),
                "search" to prop("STRING", "Text to find in counterparty, merchant or description."),
                "min_amount" to prop("NUMBER", "Only amounts at or above this (ETB)."),
                "only_uncategorized" to prop("BOOLEAN", "Only debits that have no expense category yet."),
                "limit" to prop("INTEGER", "Maximum rows to return (default 25, max 60).")
            )
        ))
        put(fn(
            "get_spending_breakdown",
            "Spending per expense category (with the user's description of each category) and the uncategorized total for a period.",
            mapOf("days_back" to prop("INTEGER", "Period length in days (default 30)."))
        ))
        put(fn(
            "get_cash_flow_trend",
            "Money in, money out and net per day or per month.",
            mapOf(
                "granularity" to prop("STRING", "Bucket size.", listOf("day", "month")),
                "periods" to prop("INTEGER", "Number of days (max 90) or months (max 12).")
            ),
            listOf("granularity")
        ))
        put(fn("get_recurring_payments", "Detected recurring payments (subscriptions, rent, equb, salaries paid) with cadence, average amount and next expected date."))
        put(fn(
            "forecast_balance",
            "Projects the total balance forward at the recent daily net trend.",
            mapOf("days" to prop("INTEGER", "Days ahead to project (default 90, max 365)."))
        ))
        put(fn("get_capital_plan", "The user's capital growth plan: target, progress and every milestone with status and deadline."))
        put(fn(
            "rank_investment_options",
            "Ranks Ethiopian options (savings, wallet savings, term deposit, interest-free mudarabah, T-bills, paying down debt, own business, gold, ESX shares, equb) by REAL return after inflation, interest tax and costs for an amount, marking which ones are unsuitable for this user and why.",
            mapOf("amount_etb" to prop("NUMBER", "Amount to place (default: the user's deployable cash above the emergency reserve)."))
        ))
        put(fn(
            "suggest_categories",
            "For uncategorized debits, the on-device learner's top category guesses with confidence and reason (learned from how the user filed earlier debits and from category descriptions).",
            mapOf(
                "days_back" to prop("INTEGER", "Only debits from the last N days (default 60)."),
                "limit" to prop("INTEGER", "Maximum debits (default 30, max 60).")
            )
        ))
        put(fn("get_user_profile", "Everything the user wrote about themselves: household, work, income stability, business numbers, risk level, interest-free preference, horizon, lock-up limit, goals, obligations."))
        put(fn(
            "update_user_profile",
            "ACTION: saves one fact the user just told you into their About Me profile.",
            mapOf(
                "field" to prop("STRING", "Profile field.", UserProfileService.AGENT_EDITABLE_FIELDS),
                "value" to prop("STRING", "New value (numbers as plain digits; true/false for yes/no fields).")
            ),
            listOf("field", "value")
        ))
        put(fn(
            "remember_memory",
            "Saves a durable note about the user for future conversations (fact, preference, goal, constraint or lesson). Use for things worth knowing next time.",
            mapOf(
                "text" to prop("STRING", "One self-contained sentence."),
                "kind" to prop("STRING", "Type of note.", MemoryKind.values().map { it.name })
            ),
            listOf("text")
        ))
        put(fn(
            "forget_memory",
            "Deletes a note that is wrong or outdated (id shown in AGENT MEMORY or from recall_memory).",
            mapOf("memory_id" to prop("STRING", "Id of the note.")),
            listOf("memory_id")
        ))
        put(fn(
            "recall_memory",
            "Searches all saved notes about the user (the system instruction only shows the most relevant ones).",
            mapOf("query" to prop("STRING", "Words to look for."))
        ))
        put(fn("get_net_worth", "The user's full net worth: bank/wallet cash plus every asset and debt in the net-worth ledger (equb, gold, shares, deposits, receivables, stock, property, loans, payables)."))
        put(fn(
            "record_wealth_item",
            "ACTION: adds an asset or debt to the user's net-worth ledger. Only when the user tells you about it and agrees to record it.",
            mapOf(
                "kind" to prop("STRING", "Type of item.", WealthKind.values().map { it.name }),
                "name" to prop("STRING", "Short name, e.g. 'Office equb' or 'Loan from Abay Bank'."),
                "quantity" to prop("NUMBER", "ETB amount; grams for GOLD; number of shares for SHARES."),
                "unit_price_etb" to prop("NUMBER", "Price per share (SHARES) or per gram (GOLD, optional)."),
                "rate_pct" to prop("NUMBER", "Yearly interest/profit rate for loans, receivables or deposits."),
                "counterparty" to prop("STRING", "Bank, person or group involved."),
                "due_date" to prop("STRING", "dd/MM/yyyy: equb payout, loan due date or deposit maturity."),
                "notes" to prop("STRING", "Anything else worth remembering.")
            ),
            listOf("kind", "name", "quantity")
        ))
        put(fn("get_market_rates", "Latest stored Ethiopian market figures (NBE policy rate, inflation, T-bill yields, deposit rates, interest tax, USD rate, gold, fuel) with as-of dates, sources and age in days, plus which ones are unknown."))
        put(fn(
            "add_capital_plan_milestone",
            "ACTION: adds a milestone to the user's capital plan. Only when the user asks to add it.",
            mapOf(
                "title" to prop("STRING", "Short actionable title."),
                "description" to prop("STRING", "What to do and why, with amounts."),
                "target_benefit_etb" to prop("NUMBER", "Expected benefit in ETB (monthly saving for spending cuts)."),
                "deadline_days" to prop("INTEGER", "Days from today until the deadline."),
                "track_category" to prop("STRING", "For spending cuts: the exact expense category to bring down; the app then measures the saving automatically."),
                "track_keyword" to prop("STRING", "For actions like 'open a deposit at Awash': a payee or keyword whose payments prove it was done (e.g. 'Awash').")
            ),
            listOf("title", "description")
        ))
        put(fn(
            "update_milestone_status",
            "ACTION: changes the status of an existing capital plan milestone. Only when the user asks.",
            mapOf(
                "milestone_title" to prop("STRING", "Title (or part of it) of the milestone."),
                "status" to prop("STRING", "New status.", listOf("PENDING", "IN_PROGRESS", "COMPLETED", "POSTPONED"))
            ),
            listOf("milestone_title", "status")
        ))
        put(fn(
            "create_savings_goal",
            "ACTION: creates a savings goal. Only when the user asks.",
            mapOf(
                "name" to prop("STRING", "Goal name."),
                "target_amount" to prop("NUMBER", "Target amount in ETB."),
                "target_date" to prop("STRING", "Target date as dd/MM/yyyy."),
                "notes" to prop("STRING", "Optional notes.")
            ),
            listOf("name", "target_amount", "target_date")
        ))
        put(fn(
            "set_transaction_category",
            "ACTION: files debits under one of the user's expense categories. Only when the user asks to categorize.",
            mapOf(
                "transaction_ids" to JSONObject()
                    .put("type", "ARRAY")
                    .put("description", "Transaction ids from get_transactions.")
                    .put("items", JSONObject().put("type", "STRING")),
                "category" to prop("STRING", "Exact name of an existing expense category.")
            ),
            listOf("transaction_ids", "category")
        ))
    }
}

/** Runs the agent's tool calls against a snapshot of the user's data. */
class AdvisorToolExecutor(
    private val data: AdvisorDataView,
    private val actions: AdvisorActions
) {
    private val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
    private val categoryNames = data.categories.associate { it.categoryId to it.name }
    private val incomeNames = data.incomeSources.associate { it.sourceId to it.name }

    private fun categoryLabel(tx: TransactionEntity): String =
        tx.categoryId?.let { categoryNames[it] ?: incomeNames[it] } ?: "Uncategorized"

    suspend fun execute(name: String, args: JSONObject): ToolOutcome = when (name) {
        "get_transactions" -> getTransactions(args)
        "get_spending_breakdown" -> spendingBreakdown(args)
        "get_cash_flow_trend" -> cashFlowTrend(args)
        "get_recurring_payments" -> recurringPayments()
        "forecast_balance" -> forecastBalance(args)
        "get_capital_plan" -> capitalPlan()
        "get_market_rates" -> marketRates()
        "get_net_worth" -> netWorth()
        "remember_memory" -> rememberMemory(args)
        "forget_memory" -> forgetMemory(args)
        "recall_memory" -> recallMemory(args)
        "get_user_profile" -> ToolOutcome(UserProfileService.toJson(data.profile), "👤 Read your profile (${data.profile.completeness}% complete)")
        "update_user_profile" -> updateProfile(args)
        "suggest_categories" -> suggestCategories(args)
        "record_wealth_item" -> recordWealthItem(args)
        "rank_investment_options" -> rankOptions(args)
        "add_capital_plan_milestone" -> addMilestone(args)
        "update_milestone_status" -> updateMilestone(args)
        "create_savings_goal" -> createGoal(args)
        "set_transaction_category" -> setCategory(args)
        else -> ToolOutcome(JSONObject().put("error", "Unknown tool $name"), "⚠️ Unknown tool $name")
    }

    private fun getTransactions(args: JSONObject): ToolOutcome {
        val days = args.optInt("days_back", 30).coerceIn(1, 365)
        val direction = args.optString("direction", "any")
        val category = args.optString("category", "").trim()
        val search = args.optString("search", "").trim()
        val minAmount = args.optDouble("min_amount", 0.0).takeIf { !it.isNaN() } ?: 0.0
        val onlyUncategorized = args.optBoolean("only_uncategorized", false)
        val limit = args.optInt("limit", 25).coerceIn(1, 60)
        val since = data.nowMillis - days * 24L * 3600 * 1000

        val matches = data.transactions.asSequence()
            .filter { it.createdTimestamp >= since }
            .filter {
                val (credit, debit) = AdvisorIntelligence.flowOf(it)
                when (direction) {
                    "in" -> credit > 0
                    "out" -> debit > 0
                    else -> credit > 0 || debit > 0
                }
            }
            .filter { category.isBlank() || categoryLabel(it).equals(category, ignoreCase = true) }
            .filter { !onlyUncategorized || (AdvisorIntelligence.flowOf(it).second > 0 && it.transactionType != TransactionType.INTERNAL_TRANSFER && categoryLabel(it) == "Uncategorized") }
            .filter { it.amount >= minAmount }
            .filter {
                search.isBlank() || listOfNotNull(it.merchant, it.recipient, it.sender, it.description)
                    .any { field -> field.contains(search, ignoreCase = true) }
            }
            .sortedByDescending { it.createdTimestamp }
            .toList()

        val rows = JSONArray()
        matches.take(limit).forEach { tx ->
            val (credit, _) = AdvisorIntelligence.flowOf(tx)
            rows.put(JSONObject().apply {
                put("id", tx.transactionId)
                put("date", dateFmt.format(Date(tx.createdTimestamp)))
                put("amount_etb", tx.amount)
                put("fee_etb", tx.fee)
                put("direction", if (credit > 0) "in" else "out")
                put("type", tx.transactionType.name)
                put("counterparty", AdvisorIntelligence.counterpartyOf(tx) ?: "")
                put("description", tx.description.take(120))
                put("category", categoryLabel(tx))
                put("bank", data.bankNames[tx.sourceId] ?: tx.sourceId)
                put("phone", data.phoneNames[tx.phoneId] ?: tx.phoneId)
                tx.userNotes?.takeIf { it.isNotBlank() }?.let { put("user_note", it) }
            })
        }
        val result = JSONObject()
            .put("matching_count", matches.size)
            .put("matching_total_etb", matches.sumOf { it.amount })
            .put("returned", rows.length())
            .put("transactions", rows)
        val what = buildString {
            append("Searched transactions (last $days days")
            if (search.isNotBlank()) append(", \"$search\"")
            if (category.isNotBlank()) append(", $category")
            if (onlyUncategorized) append(", uncategorized")
            append(") · ${matches.size} found")
        }
        return ToolOutcome(result, "🔎 $what")
    }

    private fun spendingBreakdown(args: JSONObject): ToolOutcome {
        val days = args.optInt("days_back", 30).coerceIn(1, 365)
        val since = data.nowMillis - days * 24L * 3600 * 1000
        val debits = data.transactions.filter {
            it.createdTimestamp >= since && it.transactionType != TransactionType.INTERNAL_TRANSFER && AdvisorIntelligence.flowOf(it).second > 0
        }
        val total = debits.sumOf { it.amount + it.fee }
        val descriptions = data.categories.associate { it.name to it.description }
        val rows = JSONArray()
        debits.groupBy { tx -> tx.categoryId?.let { categoryNames[it] } ?: "Uncategorized" }
            .map { (name, list) -> Triple(name, list.sumOf { it.amount + it.fee }, list.size) }
            .sortedByDescending { it.second }
            .forEach { (name, amount, count) ->
                rows.put(JSONObject().apply {
                    put("category", name)
                    put("amount_etb", amount)
                    put("transactions", count)
                    put("share_pct", if (total > 0) (amount / total * 100).toInt() else 0)
                    descriptions[name]?.takeIf { it.isNotBlank() }?.let { put("user_description", it) }
                })
            }
        return ToolOutcome(
            JSONObject().put("days", days).put("total_spent_etb", total).put("categories", rows),
            "📊 Spending breakdown (last $days days) · ${formatCurrency(total)}"
        )
    }

    private fun cashFlowTrend(args: JSONObject): ToolOutcome {
        val monthly = args.optString("granularity", "month") == "month"
        val rows = JSONArray()
        if (monthly) {
            val months = args.optInt("periods", 6).coerceIn(1, 12)
            AdvisorIntelligence.monthlyHistory(data.transactions, data.nowMillis, months).forEach {
                rows.put(JSONObject().put("period", it.label).put("in_etb", it.income).put("out_etb", it.expense).put("net_etb", it.net))
            }
        } else {
            val days = args.optInt("periods", 14).coerceIn(1, 90)
            val cal = Calendar.getInstance().apply {
                timeInMillis = data.nowMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -(days - 1))
            }
            val fmt = SimpleDateFormat("EEE dd/MM", Locale.US)
            repeat(days) {
                val start = cal.timeInMillis
                val label = fmt.format(cal.time)
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val end = cal.timeInMillis
                val inDay = data.transactions.filter { it.createdTimestamp in start until end }
                val credit = inDay.sumOf { AdvisorIntelligence.flowOf(it).first }
                val debit = inDay.sumOf { AdvisorIntelligence.flowOf(it).second }
                rows.put(JSONObject().put("period", label).put("in_etb", credit).put("out_etb", debit).put("net_etb", credit - debit))
            }
        }
        return ToolOutcome(
            JSONObject().put("granularity", if (monthly) "month" else "day").put("rows", rows),
            "📈 Cash flow trend (${rows.length()} ${if (monthly) "months" else "days"})"
        )
    }

    private fun recurringPayments(): ToolOutcome {
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val rows = JSONArray()
        data.deep.recurringPayments.forEach { r ->
            rows.put(JSONObject().apply {
                put("counterparty", r.counterparty)
                put("average_amount_etb", r.averageAmount)
                put("every_days", r.cadenceDays)
                put("times_seen", r.occurrences)
                put("last_paid", fmt.format(Date(r.lastPaidMillis)))
                put("next_expected", fmt.format(Date(r.nextExpectedMillis)))
                r.categoryName?.let { put("category", it) }
            })
        }
        val monthly = data.deep.recurringPayments.sumOf { it.averageAmount * 30.0 / it.cadenceDays.coerceAtLeast(1) }
        return ToolOutcome(
            JSONObject().put("estimated_monthly_total_etb", monthly).put("payments", rows),
            "🔁 Recurring payments · ${rows.length()} found"
        )
    }

    private fun forecastBalance(args: JSONObject): ToolOutcome {
        val days = args.optInt("days", 90).coerceIn(1, 365)
        val f = data.deep.forecast
        val projected = f.currentBalance + f.averageDailyNet * days
        val result = JSONObject()
            .put("current_balance_etb", f.currentBalance)
            .put("average_daily_net_etb", f.averageDailyNet)
            .put("based_on_days", f.basedOnDays)
            .put("days_ahead", days)
            .put("projected_balance_etb", projected)
        f.daysUntilEmpty?.let { result.put("days_until_zero", it) }
        return ToolOutcome(result, "🔮 Forecast for $days days · ${formatCurrency(projected)}")
    }

    private fun capitalPlan(): ToolOutcome {
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val rows = JSONArray()
        data.plan.milestones.forEach { m ->
            rows.put(JSONObject().apply {
                put("id", m.id)
                put("title", m.title)
                put("description", m.description.take(200))
                put("status", m.status.name)
                put("target_benefit_etb", m.targetBenefitEtb)
                put("deadline", fmt.format(Date(m.deadlineMillis)))
                m.verifiedOutcomeEtb?.let { put("verified_outcome_etb", it) }
            })
        }
        return ToolOutcome(
            JSONObject()
                .put("baseline_cash_etb", data.plan.baselineCash)
                .put("target_cash_etb", data.plan.targetCash)
                .put("target_date", fmt.format(Date(data.plan.targetDateMillis)))
                .put("progress_pct", (data.plan.progressPercentage * 100).toInt())
                .put("milestones", rows),
            "🗺 Read capital plan · ${rows.length()} milestones"
        )
    }

    private fun rankOptions(args: JSONObject): ToolOutcome {
        val requested = args.optDouble("amount_etb", Double.NaN)
        val amount = if (requested.isNaN() || requested <= 0) data.deployableCash else requested
        val ranked = InvestmentCatalog.rank(data.market, amount, data.rankingPreferences)
        val rows = JSONArray()
        ranked.forEach { r ->
            rows.put(JSONObject().apply {
                put("option", r.option.name)
                r.nominalPct?.let { put("nominal_pct", it) }
                r.realPct?.let { put("real_pct_after_inflation", it) }
                r.realYearlyEtb?.let { put("real_gain_etb_per_year", it) }
                put("rate_basis", r.rateNote)
                put("liquidity", r.option.liquidity.label)
                put("risk_0_to_3", r.option.risk)
                put("suitable", r.eligible)
                if (r.reasons.isNotEmpty()) put("notes", JSONArray(r.reasons))
                put("how_to", r.option.howTo)
                if (r.option.caution.isNotBlank()) put("caution", r.option.caution)
            })
        }
        return ToolOutcome(
            JSONObject().put("amount_etb", amount).put("options", rows),
            "⚖️ Ranked ${rows.length()} options by real return for ${formatCurrency(amount)}"
        )
    }

    private fun suggestCategories(args: JSONObject): ToolOutcome {
        val days = args.optInt("days_back", 60).coerceIn(1, 365)
        val limit = args.optInt("limit", 30).coerceIn(1, 60)
        val since = data.nowMillis - days * 24L * 3600 * 1000
        val learner = CategoryLearner.train(data.transactions, data.categories)
        val pending = data.transactions
            .filter { it.createdTimestamp >= since && it.transactionType != TransactionType.INTERNAL_TRANSFER && CategoryLearner.isDebit(it) && categoryLabel(it) == "Uncategorized" }
            .sortedByDescending { it.createdTimestamp }
        val rows = JSONArray()
        pending.take(limit).forEach { tx ->
            val guesses = JSONArray()
            learner.suggest(tx).forEach { s ->
                guesses.put(JSONObject().put("category", s.categoryName).put("confidence", (s.confidence * 100).toInt()).put("reason", s.reason))
            }
            rows.put(JSONObject().apply {
                put("id", tx.transactionId)
                put("date", dateFmt.format(Date(tx.createdTimestamp)))
                put("amount_etb", tx.amount)
                put("counterparty", AdvisorIntelligence.counterpartyOf(tx) ?: "")
                put("description", tx.description.take(100))
                put("suggestions", guesses)
            })
        }
        return ToolOutcome(
            JSONObject().put("uncategorized_count", pending.size).put("learned_from", learner.trainedOn).put("debits", rows),
            "🏷 Suggested categories for ${rows.length()} uncategorized debits"
        )
    }

    private suspend fun rememberMemory(args: JSONObject): ToolOutcome {
        val text = args.optString("text", "").trim()
        if (text.length < 5) return ToolOutcome(JSONObject().put("error", "text too short"), "⚠️ Nothing to remember")
        val kind = try { MemoryKind.valueOf(args.optString("kind", "FACT").trim().uppercase(Locale.ROOT)) } catch (_: Exception) { MemoryKind.FACT }
        val id = actions.remember(text, kind)
        return ToolOutcome(JSONObject().put("ok", true).put("memory_id", id), "🧠 Remembered: ${text.take(70)}")
    }

    private suspend fun forgetMemory(args: JSONObject): ToolOutcome {
        val id = args.optString("memory_id", "").trim()
        val ok = id.isNotBlank() && actions.forget(id)
        return ToolOutcome(JSONObject().put("ok", ok), if (ok) "🧠 Forgot an outdated note" else "⚠️ No note with id $id")
    }

    private fun recallMemory(args: JSONObject): ToolOutcome {
        val found = AgentMemoryService.search(data.memory, args.optString("query", ""))
        val rows = JSONArray()
        found.forEach { rows.put(JSONObject().put("id", it.id).put("kind", it.kind.name).put("text", it.text)) }
        return ToolOutcome(JSONObject().put("memories", rows), "🧠 Recalled ${rows.length()} notes")
    }

    private suspend fun updateProfile(args: JSONObject): ToolOutcome {
        val field = args.optString("field", "").trim()
        val value = args.optString("value", "").trim()
        val ok = value.isNotBlank() && actions.updateProfileField(field, value)
        return if (ok) {
            ToolOutcome(JSONObject().put("ok", true), "✅ Saved to your profile: $field = ${value.take(60)}")
        } else {
            ToolOutcome(JSONObject().put("error", "field not editable or value not valid"), "⚠️ Could not save $field to your profile")
        }
    }

    private fun netWorth(): ToolOutcome {
        val nw = data.netWorth
        fun lines(list: List<NetWorthLine>) = JSONArray().apply {
            list.forEach { put(JSONObject().put("name", it.name).put("kind", it.kind?.name ?: "").put("value_etb", it.valueEtb).put("detail", it.detail)) }
        }
        val result = JSONObject()
            .put("cash_bank_wallet_etb", nw.cashAndAccounts)
            .put("assets", lines(nw.assets))
            .put("debts", lines(nw.liabilities))
            .put("total_assets_etb", nw.totalAssets)
            .put("total_debts_etb", nw.liabilitiesTotal)
            .put("net_worth_etb", nw.netWorth)
        return ToolOutcome(result, "🏦 Read net worth · ${formatCurrency(nw.netWorth)}")
    }

    private suspend fun recordWealthItem(args: JSONObject): ToolOutcome {
        val kind = try {
            WealthKind.valueOf(args.optString("kind", "").trim().uppercase(Locale.ROOT))
        } catch (_: Exception) {
            return ToolOutcome(JSONObject().put("error", "unknown kind"), "⚠️ Unknown asset/debt kind")
        }
        val quantity = args.optDouble("quantity", Double.NaN)
        if (quantity.isNaN() || quantity < 0) return ToolOutcome(JSONObject().put("error", "quantity must be a positive number"), "⚠️ Missing amount")
        fun num(key: String) = args.optDouble(key, 0.0).takeIf { !it.isNaN() } ?: 0.0
        val item = WealthItemEntity(
            itemId = "W-" + java.util.UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT),
            kind = kind,
            name = args.optString("name", "").trim().ifBlank { kind.label },
            quantity = quantity,
            unitPriceEtb = num("unit_price_etb"),
            ratePct = num("rate_pct"),
            counterparty = args.optString("counterparty", "").trim(),
            dueDate = args.optString("due_date", "").trim(),
            notes = args.optString("notes", "").trim()
        )
        val id = actions.recordWealthItem(item)
        return ToolOutcome(JSONObject().put("ok", true).put("item_id", id), "✅ Recorded ${kind.label.lowercase(Locale.ROOT)}: ${item.name}")
    }

    private fun marketRates(): ToolOutcome {
        val rows = JSONArray()
        data.market.rates.forEach { r ->
            rows.put(JSONObject().apply {
                put("key", r.key)
                put("label", r.label)
                put("value", r.value)
                put("unit", r.unit)
                put("as_of", r.asOf)
                put("source", r.source)
                if (r.sourceUrl.isNotBlank()) put("url", r.sourceUrl)
                put("origin", r.origin.name.lowercase(Locale.ROOT))
                put("age_days", r.ageDays(data.nowMillis))
                put("stale", r.ageDays(data.nowMillis) > MarketDataService.STALE_DAYS)
            })
        }
        val result = JSONObject()
            .put("rates", rows)
            .put("unknown", JSONArray(data.market.missing().map { it.label }))
        return ToolOutcome(result, "📉 Read Ethiopian market data · ${rows.length()} figures")
    }

    private suspend fun addMilestone(args: JSONObject): ToolOutcome {
        val title = args.optString("title", "").trim()
        if (title.isBlank()) return ToolOutcome(JSONObject().put("error", "title is required"), "⚠️ Milestone needs a title")
        val days = args.optInt("deadline_days", 30).coerceIn(1, 3650)
        val benefit = args.optDouble("target_benefit_etb", 0.0).takeIf { !it.isNaN() } ?: 0.0
        val trackCategory = args.optString("track_category", "").trim().takeIf { name -> data.categories.any { it.name.equals(name, ignoreCase = true) } }
        val trackKeyword = args.optString("track_keyword", "").trim().takeIf { it.length >= 3 }
        val id = actions.addMilestone(title, args.optString("description", ""), benefit, data.nowMillis + days * 24L * 3600 * 1000, trackCategory, trackKeyword)
        return ToolOutcome(JSONObject().put("ok", true).put("milestone_id", id), "✅ Added milestone \"$title\" to the Capital Plan")
    }

    private suspend fun updateMilestone(args: JSONObject): ToolOutcome {
        val query = args.optString("milestone_title", "").trim()
        val status = try {
            MilestoneStatus.valueOf(args.optString("status", "").trim().uppercase(Locale.ROOT))
        } catch (_: Exception) {
            return ToolOutcome(JSONObject().put("error", "unknown status"), "⚠️ Unknown milestone status")
        }
        val milestone = data.plan.milestones.firstOrNull { it.title.equals(query, ignoreCase = true) }
            ?: data.plan.milestones.firstOrNull { query.isNotBlank() && it.title.contains(query, ignoreCase = true) }
            ?: return ToolOutcome(JSONObject().put("error", "no milestone matches \"$query\""), "⚠️ No milestone matches \"$query\"")
        actions.updateMilestoneStatus(milestone.id, status)
        return ToolOutcome(JSONObject().put("ok", true), "✅ Marked \"${milestone.title}\" as ${status.name.lowercase(Locale.ROOT).replace('_', ' ')}")
    }

    private suspend fun createGoal(args: JSONObject): ToolOutcome {
        val name = args.optString("name", "").trim()
        val target = args.optDouble("target_amount", 0.0).takeIf { !it.isNaN() } ?: 0.0
        val date = args.optString("target_date", "").trim()
        if (name.isBlank() || target <= 0 || date.isBlank()) {
            return ToolOutcome(JSONObject().put("error", "name, positive target_amount and target_date are required"), "⚠️ Savings goal is missing details")
        }
        actions.createSavingsGoal(name, target, date, args.optString("notes", ""))
        return ToolOutcome(JSONObject().put("ok", true), "✅ Created savings goal \"$name\" (${formatCurrency(target)} by $date)")
    }

    private suspend fun setCategory(args: JSONObject): ToolOutcome {
        val categoryName = args.optString("category", "").trim()
        val category = data.categories.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }
            ?: return ToolOutcome(
                JSONObject().put("error", "no expense category named \"$categoryName\"").put("available", JSONArray(data.categories.map { it.name })),
                "⚠️ No category named \"$categoryName\""
            )
        val idsArray = args.optJSONArray("transaction_ids") ?: JSONArray()
        val known = data.transactions.associateBy { it.transactionId }
        val ids = (0 until idsArray.length()).map { idsArray.optString(it) }
            .filter { id -> known[id]?.let { AdvisorIntelligence.flowOf(it).second > 0 } == true }
        if (ids.isEmpty()) return ToolOutcome(JSONObject().put("error", "no valid debit ids"), "⚠️ No valid debits to categorize")
        val changed = actions.categorizeTransactions(ids, category.categoryId)
        return ToolOutcome(JSONObject().put("ok", true).put("categorized", changed), "🏷 Filed $changed debit(s) under ${category.name}")
    }
}
