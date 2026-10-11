package com.example.ai

import com.example.ui.components.formatCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Robust Gemini REST client service adhering strictly to the official API specification.
 * Never leaks the API key to logs or UI, manages connection testing, structured generation,
 * interactive financial consultation, and normalized error reporting.
 */
object GeminiClientService {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    /**
     * Executes a low-cost, lightweight connection ping to test the key and model.
     */
    suspend fun testConnection(
        apiKey: String,
        model: String = SupportedGeminiModels.GEMINI_3_5_FLASH
    ): Result<ConnectionTestResult> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("No Gemini API Key provided. Please enter an API key.")
            )
        }

        val startTime = System.currentTimeMillis()
        try {
            val endpoint = "$BASE_URL/$model:generateContent?key=$cleanKey"
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", "Ping test. Reply with 'PONG' only."))
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("maxOutputTokens", 10)
                    put("temperature", 0.1)
                })
            }

            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(endpoint).post(body).build()

            val response = httpClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val (status, msg) = parseErrorResponse(response.code, responseBody)
                return@withContext Result.success(
                    ConnectionTestResult(
                        success = false,
                        status = status,
                        latencyMs = latency,
                        modelUsed = model,
                        message = msg
                    )
                )
            }

            // Verify valid candidate
            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                Result.success(
                    ConnectionTestResult(
                        success = true,
                        status = GeminiConnectionStatus.CONNECTED,
                        latencyMs = latency,
                        modelUsed = model,
                        message = "Connected successfully to $model in ${latency}ms!"
                    )
                )
            } else {
                Result.success(
                    ConnectionTestResult(
                        success = false,
                        status = GeminiConnectionStatus.SERVICE_UNAVAILABLE,
                        latencyMs = latency,
                        modelUsed = model,
                        message = "API returned empty candidate response."
                    )
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val status = if (e.message?.contains("timeout", ignoreCase = true) == true) {
                GeminiConnectionStatus.NETWORK_TIMEOUT
            } else {
                GeminiConnectionStatus.SERVICE_UNAVAILABLE
            }
            Result.success(
                ConnectionTestResult(
                    success = false,
                    status = status,
                    latencyMs = latency,
                    modelUsed = model,
                    message = e.localizedMessage ?: "Network connection failure."
                )
            )
        }
    }

    /**
     * Runs full evidence-grounded financial analysis to discover opportunities and
     * synthesize an executive summary.
     */
    suspend fun generateExecutiveSummaryAndOpportunities(
        apiKey: String,
        model: String,
        snapshot: AdvisorFinancialSnapshot,
        userPreferencesText: String = "",
        extraContext: String = ""
    ): Result<Pair<ExecutiveSummary, List<AdvisorOpportunity>>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Gemini API key is required to run capital analysis.")
            )
        }

        try {
            val systemInstructions = systemInstructionFor(snapshot, forAgent = false, extraContext = extraContext)
            val prompt = """
                Perform a comprehensive capital growth analysis of my current finances.
                Evaluate opportunities across ALL SIX financial pillars:
                1. EXPENSE_OPTIMIZATION
                2. INCOME_GROWTH
                3. DEBT_MANAGEMENT
                4. BUSINESS_OPPORTUNITY
                5. INVESTMENT
                6. CAPITAL_ALLOCATION

                User preferences / notes: $userPreferencesText

                Return your output in strictly valid JSON format matching this schema:
                {
                  "executiveSummary": {
                    "overview": "Clear 2-3 sentence diagnosis of current financial baseline and net cash flow trajectory.",
                    "majorChanges": "Assessment of current month cash inflows vs outflows and burn rate.",
                    "cashGrowthBarriers": ["Barrier 1", "Barrier 2"],
                    "topOpportunities": ["Highlight 1", "Highlight 2"],
                    "financialRisks": ["Risk 1", "Risk 2"],
                    "priorityActions": ["Action 1", "Action 2", "Action 3"]
                  },
                  "opportunities": [
                    {
                      "title": "Short actionable title",
                      "pillar": "EXPENSE_OPTIMIZATION | INCOME_GROWTH | DEBT_MANAGEMENT | BUSINESS_OPPORTUNITY | INVESTMENT | CAPITAL_ALLOCATION",
                      "summary": "Detailed explanation of why this works and concrete mechanics",
                      "estimatedBenefitEtb": 15000.0,
                      "benefitPeriod": "Monthly | One-time | Annual",
                      "capitalRequiredEtb": 5000.0,
                      "timeframe": "1-2 weeks",
                      "riskLevel": "Low | Medium | High",
                      "confidenceScore": 85,
                      "recommendedNextStep": "Immediate concrete step user should take",
                      "sourceOrInstitution": "e.g. CBE / Telebirr / NBE T-Bills / Wholesale",
                      "assumptions": ["Assumption 1", "Assumption 2"]
                    }
                  ]
                }
                Use the DEEP CONTEXT facts: attack the recurring payments, unusual debits and fastest-growing categories,
                respect the cash forecast, and size every opportunity so the forecast balance never drops below the emergency reserve.
                Provide at least 5-8 concrete opportunities grounded in the user's available capital (${formatCurrency(snapshot.availableLiquidCash)}) and total liquid balance (${formatCurrency(snapshot.totalRecordedBalance)}).
            """.trimIndent()

            val responseText = executeGeminiRequest(cleanKey, model, systemInstructions, prompt, jsonMode = true)

            val parsedPair = parseAnalysisJson(responseText, snapshot)
            Result.success(parsedPair)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Interactive financial consultation chat with historical context.
     */
    suspend fun sendConsultationMessage(
        apiKey: String,
        model: String,
        userQuery: String,
        conversationHistory: List<AdvisorChatMessage>,
        snapshot: AdvisorFinancialSnapshot
    ): Result<AdvisorChatMessage> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Gemini API key is required to consult the advisor.")
            )
        }

        try {
            val systemInstructions = buildSystemInstructions(snapshot)
            val historyContext = buildString {
                append("CONVERSATION HISTORY:\n")
                conversationHistory.takeLast(6).forEach { msg ->
                    append(if (msg.isUser) "User: " else "Advisor: ")
                    append(msg.message)
                    append("\n")
                }
            }

            val prompt = """
                $historyContext
                
                Current User Question: $userQuery
                
                Respond as the Senior AI Capital Growth Advisor.
                • Reference their actual numbers: Total Balance: ${formatCurrency(snapshot.totalRecordedBalance)}, Available Liquid: ${formatCurrency(snapshot.availableLiquidCash)}, Runway: ${String.format("%.1f", snapshot.runwayMonths)} months.
                • Give practical, step-by-step guidance tailored to Ethiopia's financial and business environment.
                • End with 2-3 short, bulleted follow-up suggested questions or actions for the user.
            """.trimIndent()

            val reply = executeGeminiRequest(cleanKey, model, systemInstructions, prompt)

            // Extract suggested actions if present
            val suggestions = extractSuggestedActions(reply)

            Result.success(
                AdvisorChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    message = reply,
                    timestamp = System.currentTimeMillis(),
                    suggestedActions = suggestions
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Prompt/completion token counts of the most recent request (from the API's usageMetadata). */
    @Volatile
    var lastUsage: Pair<Int, Int>? = null
        private set

    /** User-facing message for a failed Gemini HTTP response. */
    fun describeError(code: Int, body: String): String = parseErrorResponse(code, body).second

    private fun executeGeminiRequest(
        apiKey: String,
        model: String,
        systemInstruction: String,
        promptText: String,
        jsonMode: Boolean = false
    ): String {
        val endpoint = "$BASE_URL/$model:generateContent?key=$apiKey"
        val requestJson = JSONObject().apply {
            // A real system instruction (not glued to the prompt) is followed far more reliably
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", promptText))
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", if (jsonMode) 0.4 else 0.7)
                put("maxOutputTokens", 8192)
                if (jsonMode) put("responseMimeType", "application/json")
            })
        }

        var responseBody = ""
        var lastError: Exception? = null
        for (attempt in 0..2) {
            if (attempt > 0) Thread.sleep(1_500L * attempt * attempt)
            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(endpoint).post(body).build()
            val response = try {
                httpClient.newCall(request).execute()
            } catch (e: Exception) {
                lastError = e
                continue
            }
            responseBody = response.body?.string() ?: ""
            val code = response.code
            response.close()
            if (response.isSuccessful) {
                lastError = null
                break
            }
            lastError = Exception(parseErrorResponse(code, responseBody).second)
            // Only rate limits and transient server errors are worth retrying
            if (code != 429 && code < 500) break
        }
        lastError?.let { throw it }

        val json = JSONObject(responseBody)
        lastUsage = json.optJSONObject("usageMetadata")?.let {
            it.optInt("promptTokenCount", 0) to it.optInt("candidatesTokenCount", 0)
        }
        val candidates = json.optJSONArray("candidates") ?: throw Exception("No candidates returned from Gemini.")
        if (candidates.length() == 0) throw Exception("Empty response candidates.")

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content") ?: throw Exception("Malformed content in Gemini candidate.")
        val parts = content.optJSONArray("parts") ?: throw Exception("No parts in candidate content.")

        val textBuilder = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            if (part.optBoolean("thought", false)) continue
            textBuilder.append(part.optString("text", ""))
        }

        val result = textBuilder.toString().trim()
        if (result.isEmpty()) throw Exception("Empty text returned from Gemini.")
        return result
    }

    private fun parseAnalysisJson(
        rawText: String,
        snapshot: AdvisorFinancialSnapshot
    ): Pair<ExecutiveSummary, List<AdvisorOpportunity>> {
        val cleanJsonText = stripMarkdownCodeBlocks(rawText)
        val json = try {
            JSONObject(cleanJsonText)
        } catch (_: Exception) {
            // If model returned freeform text or invalid JSON, construct a fallback structured output
            return createFallbackStructuredAnalysis(rawText, snapshot)
        }

        val summaryObj = json.optJSONObject("executiveSummary")
        val overview = summaryObj?.optString("overview") ?: "Financial analysis completed."
        val majorChanges = summaryObj?.optString("majorChanges") ?: "Baseline active."
        val barriers = jsonArrayToStringList(summaryObj?.optJSONArray("cashGrowthBarriers"))
        val topOpps = jsonArrayToStringList(summaryObj?.optJSONArray("topOpportunities"))
        val risks = jsonArrayToStringList(summaryObj?.optJSONArray("financialRisks"))
        val priorityActions = jsonArrayToStringList(summaryObj?.optJSONArray("priorityActions"))

        val execSummary = ExecutiveSummary(
            analysisTimestamp = System.currentTimeMillis(),
            overview = overview,
            majorChanges = majorChanges,
            cashGrowthBarriers = if (barriers.isNotEmpty()) barriers else listOf("Unoptimized bank account yield", "High proportion of uncategorized debits"),
            topOpportunities = if (topOpps.isNotEmpty()) topOpps else listOf("Move idle cash to the option with the best real (after-inflation) return", "Negotiate bulk vendor pricing"),
            financialRisks = if (risks.isNotEmpty()) risks else listOf("ETB currency inflation impact on non-yielding cash"),
            priorityActions = if (priorityActions.isNotEmpty()) priorityActions else listOf("Maintain 3-month emergency reserve", "Allocate surplus into Treasury Bills or high-interest mudarabah/term deposits")
        )

        val opportunities = mutableListOf<AdvisorOpportunity>()
        val oppArray = json.optJSONArray("opportunities")
        if (oppArray != null) {
            for (i in 0 until oppArray.length()) {
                val o = oppArray.optJSONObject(i) ?: continue
                val title = o.optString("title", "Growth Strategy #${i + 1}")
                val pillarStr = o.optString("pillar", "CAPITAL_ALLOCATION")
                val pillar = try {
                    AdvisorPillar.valueOf(pillarStr.trim().uppercase())
                } catch (_: Exception) {
                    AdvisorPillar.CAPITAL_ALLOCATION
                }

                opportunities.add(
                    AdvisorOpportunity(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        pillar = pillar,
                        summary = o.optString("summary", "Optimization strategy"),
                        estimatedBenefitEtb = o.optDouble("estimatedBenefitEtb", 5000.0),
                        benefitPeriod = o.optString("benefitPeriod", "Monthly"),
                        capitalRequiredEtb = o.optDouble("capitalRequiredEtb", 0.0),
                        timeframe = o.optString("timeframe", "2-4 weeks"),
                        riskLevel = o.optString("riskLevel", "Low"),
                        confidenceScore = o.optInt("confidenceScore", 85).coerceIn(40, 99),
                        recommendedNextStep = o.optString("recommendedNextStep", "Review with banking institution"),
                        sourceOrInstitution = o.optString("sourceOrInstitution", "Ethiopian Financial Market"),
                        assumptions = jsonArrayToStringList(o.optJSONArray("assumptions")),
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }

        if (opportunities.isEmpty()) {
            opportunities.addAll(generateDefaultEthiopianOpportunities(snapshot))
        }

        return Pair(execSummary, opportunities)
    }

    private fun createFallbackStructuredAnalysis(
        rawText: String,
        snapshot: AdvisorFinancialSnapshot
    ): Pair<ExecutiveSummary, List<AdvisorOpportunity>> {
        val execSummary = ExecutiveSummary(
            analysisTimestamp = System.currentTimeMillis(),
            overview = rawText.take(400) + if (rawText.length > 400) "..." else "",
            majorChanges = "Liquid balance standing at ${formatCurrency(snapshot.totalRecordedBalance)}.",
            cashGrowthBarriers = listOf("Idle non-yielding deposits", "Unhedged cash in checking accounts"),
            topOpportunities = listOf("Deposits or T-bills only where they beat inflation (see market data)", "Short-term Treasury Bill auction participation"),
            financialRisks = listOf("Inflation erosion on static Birr balances"),
            priorityActions = listOf("Secure emergency liquidity", "Deploy surplus capital into yielding accounts")
        )

        return Pair(execSummary, generateDefaultEthiopianOpportunities(snapshot))
    }

    private fun generateDefaultEthiopianOpportunities(snapshot: AdvisorFinancialSnapshot): List<AdvisorOpportunity> {
        val available = snapshot.availableLiquidCash
        val market = snapshot.market
        // Benefits use the stored market figures; when one is unknown the benefit is left at 0 instead of guessed
        fun rateText(key: String): String = market.get(key)?.let { "${it.display} as of ${it.asOf}" } ?: "rate unknown — refresh market data"
        fun monthlyAt(key: String, capital: Double): Double = market.value(key)?.let { capital * it / 100.0 / 12.0 } ?: 0.0
        val termCapital = (available * 0.4).coerceAtLeast(10_000.0)
        val tbillCapital = (available * 0.3).coerceAtLeast(50_000.0)
        return listOf(
            AdvisorOpportunity(
                id = UUID.randomUUID().toString(),
                title = "Term Deposit / Interest-free Mudarabah Account",
                pillar = AdvisorPillar.INVESTMENT,
                summary = "Move idle surplus into a fixed-term deposit (or an interest-free mudarabah account) at your main bank. Latest typical 1-year rate: ${rateText(MarketIndicators.TERM_DEPOSIT_1Y)}. Compare it with inflation (${rateText(MarketIndicators.INFLATION)}) before locking money in.",
                estimatedBenefitEtb = monthlyAt(MarketIndicators.TERM_DEPOSIT_1Y, termCapital),
                benefitPeriod = "Monthly",
                capitalRequiredEtb = termCapital,
                timeframe = "1-2 weeks",
                riskLevel = "Low",
                confidenceScore = 85,
                recommendedNextStep = "Ask two or three banks for their current term-deposit rates and pick the best real return.",
                sourceOrInstitution = "Ethiopian commercial banks",
                assumptions = listOf("Capital stays locked for the agreed tenure", "Rate from BirrFlow market data, not a quote")
            ),
            AdvisorOpportunity(
                id = UUID.randomUUID().toString(),
                title = "Treasury Bill (T-Bill) Auction",
                pillar = AdvisorPillar.CAPITAL_ALLOCATION,
                summary = "Bid in the Ministry of Finance treasury bill auctions through your bank. Latest average yield: ${rateText(MarketIndicators.TBILL_AVG)}. Sovereign-backed, but check that the yield beats inflation.",
                estimatedBenefitEtb = monthlyAt(MarketIndicators.TBILL_AVG, tbillCapital),
                benefitPeriod = "Monthly",
                capitalRequiredEtb = tbillCapital,
                timeframe = "2-4 weeks",
                riskLevel = "Low",
                confidenceScore = 85,
                recommendedNextStep = "Ask your bank's treasury desk for the next auction date and minimum bid.",
                sourceOrInstitution = "Ministry of Finance / NBE",
                assumptions = listOf("Held to maturity", "Yield from BirrFlow market data")
            ),
            AdvisorOpportunity(
                id = UUID.randomUUID().toString(),
                title = "Digital Wallet Savings for Working Cash",
                pillar = AdvisorPillar.INCOME_GROWTH,
                summary = "Keep day-to-day cash in a wallet savings feature (Telebirr Sanduq, M-Pesa savings) instead of a zero-interest balance. Typical savings rate: ${rateText(MarketIndicators.SAVINGS_RATE)}.",
                estimatedBenefitEtb = monthlyAt(MarketIndicators.SAVINGS_RATE, snapshot.totalRecordedBalance * 0.2),
                benefitPeriod = "Monthly",
                capitalRequiredEtb = 5_000.0,
                timeframe = "Immediate",
                riskLevel = "Low",
                confidenceScore = 80,
                recommendedNextStep = "Check the savings rate inside your wallet app and move idle wallet cash into it.",
                sourceOrInstitution = "Ethio Telecom Telebirr / Safaricom M-Pesa",
                assumptions = listOf("Money stays instantly accessible")
            ),
            AdvisorOpportunity(
                id = UUID.randomUUID().toString(),
                title = "Eliminate Redundant Subscriptions & High-Fee Transfers",
                pillar = AdvisorPillar.EXPENSE_OPTIMIZATION,
                summary = "Audit recurring debits and batch outgoing inter-bank transfers to cut fees and avoid paying twice for the same service.",
                estimatedBenefitEtb = snapshot.deepContext.recurringPayments.sumOf { it.averageAmount * 30.0 / it.cadenceDays.coerceAtLeast(1) } * 0.1,
                benefitPeriod = "Monthly",
                capitalRequiredEtb = 0.0,
                timeframe = "1 week",
                riskLevel = "Low",
                confidenceScore = 80,
                recommendedNextStep = "Categorize pending debits and review the recurring payments list.",
                sourceOrInstitution = "BirrFlow Expense Tracker",
                assumptions = listOf("Savings estimated at 10% of detected recurring payments")
            ),
            AdvisorOpportunity(
                id = UUID.randomUUID().toString(),
                title = "Fast-Turning Working Capital in Your Own Trade",
                pillar = AdvisorPillar.BUSINESS_OPPORTUNITY,
                summary = "If you run a business, extra stock that sells within a month at a solid margin usually beats deposits after inflation. Size it to what you can sell, not what you can buy.",
                estimatedBenefitEtb = 0.0,
                benefitPeriod = "Monthly",
                capitalRequiredEtb = (available * 0.5).coerceAtLeast(25_000.0),
                timeframe = "1 month",
                riskLevel = "Medium",
                confidenceScore = 70,
                recommendedNextStep = "Write down your margin and how fast each product sells, then ask the advisor to size the order.",
                sourceOrInstitution = "Your business",
                assumptions = listOf("Requires stock to turn over within 30 days", "Benefit depends on your own margin")
            )
        )
    }

    /**
     * System instruction for the advisor; [forAgent] adds the tool-use rules of the chat agent and
     * [extraContext] carries the agent memory.
     */
    fun systemInstructionFor(snapshot: AdvisorFinancialSnapshot, forAgent: Boolean, extraContext: String = ""): String {
        val base = buildSystemInstructions(snapshot) + (if (extraContext.isNotBlank()) "\n\n" + extraContext else "")
        return if (forAgent) base + "\n\n" + AdvisorAgent.AGENT_RULES.trim() else base
    }

    /**
     * Folds older chat messages into the running conversation summary and extracts durable facts the
     * user stated about themselves (stored as memories).
     */
    suspend fun summarizeConversation(
        apiKey: String,
        model: String,
        previousSummary: String,
        messages: List<AdvisorChatMessage>
    ): Pair<String, List<Pair<String, MemoryKind>>> = withContext(Dispatchers.IO) {
        val transcript = messages.joinToString("\n") { (if (it.isUser) "User: " else "Advisor: ") + it.message.take(1200) }
        val prompt = """
            Previous summary (may be empty): $previousSummary

            New conversation turns:
            $transcript

            Update the summary of this conversation between a user and their financial advisor so it keeps
            every decision, number and open question that matters later (max 120 words).
            Also list up to 4 DURABLE facts the user stated about themselves (income, family, business,
            goals, preferences, constraints). Do not list advice or temporary details.
            Reply with ONLY: {"summary":"...","facts":[{"text":"...","kind":"FACT|PREFERENCE|GOAL|CONSTRAINT"}]}
        """.trimIndent()
        val raw = executeGeminiRequest(apiKey.trim(), model, "You maintain a financial advisor's memory. Be factual and brief.", prompt, jsonMode = true)
        val json = JSONObject(stripMarkdownCodeBlocks(raw))
        val facts = ArrayList<Pair<String, MemoryKind>>()
        val arr = json.optJSONArray("facts")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                val text = f.optString("text", "").trim()
                if (text.isBlank()) continue
                val kind = try { MemoryKind.valueOf(f.optString("kind", "FACT").trim().uppercase()) } catch (_: Exception) { MemoryKind.FACT }
                facts += text to kind
            }
        }
        json.optString("summary", previousSummary).trim().take(1200) to facts.take(4)
    }

    private fun buildSystemInstructions(snapshot: AdvisorFinancialSnapshot): String {
        return """
            You are the senior, evidence-driven AI Capital Growth Advisor embedded in BirrFlow.
            Your mission: Help the user increase sustainable, available cash, improve financial resilience, and grow wealth through practical, risk-aware decisions.

            AUTHORITATIVE FINANCIAL BASELINE (SOURCE OF TRUTH):
            • Total Recorded Liquid Balance: ${formatCurrency(snapshot.totalRecordedBalance)}
            • Available Liquid Cash (after emergency reserve): ${formatCurrency(snapshot.availableLiquidCash)}
            • Current Month Incomes: ${formatCurrency(snapshot.currentMonthIncome)}
            • Current Month Expenses: ${formatCurrency(snapshot.currentMonthExpense)}
            • Net Cash Flow: ${formatCurrency(snapshot.netCashFlow)}
            • Daily Burn Rate: ${formatCurrency(snapshot.burnRateDaily)}/day
            • Cash Runway: ${String.format("%.1f", snapshot.runwayMonths)} months
            • Required Emergency Reserve (${snapshot.profile.emergencyMonths} mo): ${formatCurrency(snapshot.emergencyReserveRequired)} [${snapshot.emergencyReserveStatus}]
            • Active Accounts & Phones: ${snapshot.activeSourcesCount} sources, ${snapshot.activePhonesCount} registered devices
            • Spending Profile: ${snapshot.topSpendingCategories.joinToString("; ") { "${it.categoryName}: ${formatCurrency(it.totalAmount)} (${(it.percentage * 100).toInt()}%)" }}
            • Uncategorized debits: ${formatCurrency(snapshot.uncategorizedExpense)} across ${snapshot.uncategorizedCount} transactions

            USER-DEFINED EXPENSE TYPES (what each category means, in the user's own words):
            ${buildCategoryDefinitions(snapshot)}

            MANDATORY REASONING RULES:
            1. Never invent or hallucinate account balances or transactions. The above snapshot is authoritative.
            2. Never recommend an investment that consumes the user's mandatory emergency reserve (${formatCurrency(snapshot.emergencyReserveRequired)}).
            3. Do not treat loans or internal transfers as earned income.
            4. Tailor recommendations realistically to available capital (${formatCurrency(snapshot.availableLiquidCash)}).
            5. Deeply ground recommendations in Ethiopia's macroeconomic landscape:
               - Ethiopian Birr (ETB) currency dynamics and inflation hedging.
               - Licensed Ethiopian commercial banks (CBE, Awash, Dashen, Bank of Abyssinia, Coop Bank).
               - National Bank of Ethiopia (NBE) Treasury Bills (T-Bills): use the yields in MARKET DATA only.
               - Telebirr Sanduq, CBE Birr, and digital payment rails.
               - Practical Ethiopian business ventures (wholesale, commodities, logistics, retail trade).
            6. Balance short-term cash flow improvements with long-term wealth building.
            7. Distinguish verified facts from estimates and assumptions. Never promise guaranteed returns.
            8. Interpret each expense category through the user's own description above (e.g. whether it is a fixed commitment, a business cost, family support or discretionary) before suggesting cuts or reallocations.
            9. Use the DEEP CONTEXT below (forecast, recurring payments, anomalies, trends, goals) as facts and reference it explicitly.
            10. Quote interest rates, yields, inflation, exchange rates and prices ONLY from MARKET DATA (with its as-of date) or a web source you cite. If a figure is unknown or marked STALE, say so instead of guessing.
            11. Judge every option by its REAL return (after inflation, tax and fees), not its headline rate.
            12. Respect ABOUT THE USER: their risk level, interest-free preference, lock-up limit, obligations and language.
        """.trimIndent() + "\n\n" + UserProfileService.toPromptSection(snapshot.profile) +
            "\n\n" + NetWorthCalculator.toPromptSection(snapshot.netWorth) +
            "\n\n" + MarketDataService.toPromptSection(snapshot.market) +
            (if (snapshot.rankedOptions.isNotEmpty()) "\n\n" + InvestmentCatalog.toPromptSection(snapshot.rankedOptions, snapshot.availableLiquidCash) else "") +
            "\n\n" + AdvisorIntelligence.toPromptSection(snapshot.deepContext) +
            "\n\n" + DecisionTracker.toPromptSection(snapshot.deepContext.trackRecord)
    }

    /** One line per manual expense category: name, the user's description, and current spend. */
    internal fun buildCategoryDefinitions(snapshot: AdvisorFinancialSnapshot): String {
        if (snapshot.expenseCategoryProfiles.isEmpty()) return "• (no manual expense categories yet)"
        return snapshot.expenseCategoryProfiles.joinToString("\n            ") { c ->
            val details = c.description.ifBlank { "no description given" }
            "• ${c.categoryName}: $details — ${formatCurrency(c.totalAmount)} in ${c.transactionCount} transactions"
        }
    }

    private fun stripMarkdownCodeBlocks(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```json", ignoreCase = true)) {
            text = text.substring(7)
        } else if (text.startsWith("```")) {
            text = text.substring(3)
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length - 3)
        }
        return text.trim()
    }

    private fun jsonArrayToStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.optString(i)
            if (item.isNotBlank()) list.add(item)
        }
        return list
    }

    fun extractSuggestedActions(text: String): List<String> {
        val lines = text.lines()
        val bullets = lines.filter { it.trim().startsWith("•") || it.trim().startsWith("-") || it.trim().startsWith("*") }
            .map { it.replace(Regex("^[•\\-*\\s]+"), "").trim() }
            .filter { it.length in 10..90 }
        return if (bullets.isNotEmpty()) bullets.take(3) else listOf(
            "How do I apply for NBE Treasury Bills?",
            "What is my target emergency reserve amount?",
            "How can I cut my highest spending category?"
        )
    }

    private fun parseErrorResponse(code: Int, body: String): Pair<GeminiConnectionStatus, String> {
        val (errorMsg, statusStr) = try {
            val json = JSONObject(body)
            val errObj = json.optJSONObject("error")
            val m = errObj?.optString("message") ?: "HTTP $code"
            val s = errObj?.optString("status") ?: ""
            Pair(m, s)
        } catch (_: Exception) {
            Pair("HTTP $code: $body", "")
        }

        return when {
            code == 401 || code == 403 || statusStr.contains("UNAUTHENTICATED") || errorMsg.contains("API key not valid", ignoreCase = true) -> {
                Pair(GeminiConnectionStatus.AUTH_FAILED, "Invalid Gemini API key. Please verify your key in settings.")
            }
            code == 429 || statusStr.contains("RESOURCE_EXHAUSTED") || errorMsg.contains("Quota exceeded", ignoreCase = true) -> {
                Pair(GeminiConnectionStatus.QUOTA_EXCEEDED, "Gemini API Quota exceeded. Check your Google Cloud billing or rate limits.")
            }
            code == 503 || statusStr.contains("UNAVAILABLE") -> {
                Pair(GeminiConnectionStatus.SERVICE_UNAVAILABLE, "Gemini service temporarily unavailable. Please retry in a few moments.")
            }
            else -> {
                Pair(GeminiConnectionStatus.SERVICE_UNAVAILABLE, errorMsg)
            }
        }
    }
}
