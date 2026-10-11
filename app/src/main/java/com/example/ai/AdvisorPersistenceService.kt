package com.example.ai

import com.example.data.repository.FinancialRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Handles persistent storage of Gemini advisor state, plans, opportunities,
 * and audit logs in the local Room database via [FinancialRepository].
 */
object AdvisorPersistenceService {

    private const val KEY_GEMINI_CONFIG = "AI_ADVISOR_GEMINI_CONFIG"
    private const val KEY_CAPITAL_PLAN = "AI_ADVISOR_CAPITAL_PLAN"
    private const val KEY_OPPORTUNITIES = "AI_ADVISOR_OPPORTUNITIES"
    private const val KEY_EXECUTIVE_SUMMARY = "AI_ADVISOR_EXECUTIVE_SUMMARY"
    private const val KEY_CHAT_MESSAGES = "AI_ADVISOR_CHAT_MESSAGES"
    private const val KEY_USAGE_LOGS = "AI_ADVISOR_USAGE_LOGS"

    suspend fun loadGeminiConfig(repository: FinancialRepository): GeminiConfig {
        val raw = repository.getState(KEY_GEMINI_CONFIG)
        if (raw.isNullOrBlank()) {
            // Check legacy custom key
            val legacyKey = repository.getState("GEMINI_CUSTOM_API_KEY")
            return GeminiConfig(
                apiKey = legacyKey ?: "",
                selectedModel = SupportedGeminiModels.GEMINI_3_5_FLASH,
                status = if (!legacyKey.isNullOrBlank()) GeminiConnectionStatus.CONFIGURED_UNTESTED else GeminiConnectionStatus.NOT_CONFIGURED
            )
        }
        return try {
            val json = JSONObject(raw)
            GeminiConfig(
                apiKey = json.optString("apiKey", ""),
                selectedModel = json.optString("selectedModel", SupportedGeminiModels.GEMINI_3_5_FLASH),
                status = try {
                    GeminiConnectionStatus.valueOf(json.optString("status", GeminiConnectionStatus.NOT_CONFIGURED.name))
                } catch (_: Exception) {
                    GeminiConnectionStatus.NOT_CONFIGURED
                },
                lastTestedAt = if (json.has("lastTestedAt")) json.getLong("lastTestedAt") else null,
                lastLatencyMs = if (json.has("lastLatencyMs")) json.getLong("lastLatencyMs") else null,
                lastError = json.optString("lastError").takeIf { !it.isNullOrBlank() },
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            GeminiConfig()
        }
    }

    suspend fun saveGeminiConfig(repository: FinancialRepository, config: GeminiConfig) {
        val json = JSONObject().apply {
            put("apiKey", config.apiKey)
            put("selectedModel", config.selectedModel)
            put("status", config.status.name)
            config.lastTestedAt?.let { put("lastTestedAt", it) }
            config.lastLatencyMs?.let { put("lastLatencyMs", it) }
            config.lastError?.let { put("lastError", it) }
            put("updatedAt", config.updatedAt)
        }
        repository.putState(KEY_GEMINI_CONFIG, json.toString())
        // Keep legacy key synced for compatibility
        if (config.apiKey.isNotBlank()) {
            repository.putState("GEMINI_CUSTOM_API_KEY", config.apiKey)
        }
    }

    suspend fun loadCapitalPlan(repository: FinancialRepository, baselineCash: Double = 0.0): AdvisorCapitalPlan {
        val raw = repository.getState(KEY_CAPITAL_PLAN)
        if (raw.isNullOrBlank()) {
            // Default initial plan
            val target = if (baselineCash > 0) baselineCash * 1.5 else 100_000.0
            return AdvisorCapitalPlan(
                baselineCash = baselineCash,
                targetCash = target,
                targetDateMillis = System.currentTimeMillis() + (90L * 24 * 3600 * 1000L),
                progressPercentage = 0.1f,
                milestones = listOf(
                    PlanMilestone(
                        id = UUID.randomUUID().toString(),
                        title = "Lock In Emergency Reserve",
                        description = "Segregate 3 months baseline burn into low-risk high-liquidity account.",
                        targetBenefitEtb = 30_000.0,
                        deadlineMillis = System.currentTimeMillis() + (14L * 24 * 3600 * 1000L),
                        status = MilestoneStatus.PENDING
                    ),
                    PlanMilestone(
                        id = UUID.randomUUID().toString(),
                        title = "Put Idle Cash in the Best Real-Return Option",
                        description = "Deploy uncommitted cash where it beats inflation after tax (compare deposits, T-bills and interest-free accounts using current market data).",
                        targetBenefitEtb = 15_000.0,
                        deadlineMillis = System.currentTimeMillis() + (30L * 24 * 3600 * 1000L),
                        status = MilestoneStatus.PENDING
                    ),
                    PlanMilestone(
                        id = UUID.randomUUID().toString(),
                        title = "Cut Uncategorized / Recurring Leakages",
                        description = "Audit recurring transactions and trim top expense category by 15%.",
                        targetBenefitEtb = 5_000.0,
                        deadlineMillis = System.currentTimeMillis() + (21L * 24 * 3600 * 1000L),
                        status = MilestoneStatus.PENDING
                    )
                ),
                lastReviewedAt = System.currentTimeMillis()
            )
        }

        return try {
            val json = JSONObject(raw)
            val milestones = mutableListOf<PlanMilestone>()
            val arr = json.optJSONArray("milestones")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    milestones.add(
                        PlanMilestone(
                            id = m.optString("id", UUID.randomUUID().toString()),
                            title = m.optString("title", "Milestone"),
                            description = m.optString("description", ""),
                            targetBenefitEtb = m.optDouble("targetBenefitEtb", 0.0),
                            deadlineMillis = m.optLong("deadlineMillis", System.currentTimeMillis()),
                            status = try {
                                MilestoneStatus.valueOf(m.optString("status", MilestoneStatus.PENDING.name))
                            } catch (_: Exception) {
                                MilestoneStatus.PENDING
                            },
                            verifiedOutcomeEtb = if (m.has("verifiedOutcomeEtb")) m.getDouble("verifiedOutcomeEtb") else null,
                            completedAt = if (m.has("completedAt")) m.getLong("completedAt") else null,
                            sourceOpportunityId = m.optString("sourceOpportunityId").takeIf { it.isNotBlank() },
                            trackCategory = m.optString("trackCategory").takeIf { it.isNotBlank() },
                            trackKeyword = m.optString("trackKeyword").takeIf { it.isNotBlank() },
                            baselineMonthlyEtb = if (m.has("baselineMonthlyEtb")) m.getDouble("baselineMonthlyEtb") else null,
                            startedAt = if (m.has("startedAt")) m.getLong("startedAt") else null,
                            autoOutcomeEtb = if (m.has("autoOutcomeEtb")) m.getDouble("autoOutcomeEtb") else null,
                            autoNote = m.optString("autoNote").takeIf { it.isNotBlank() },
                            lastCheckedAt = if (m.has("lastCheckedAt")) m.getLong("lastCheckedAt") else null
                        )
                    )
                }
            }

            AdvisorCapitalPlan(
                baselineCash = json.optDouble("baselineCash", baselineCash),
                targetCash = json.optDouble("targetCash", 100_000.0),
                targetDateMillis = json.optLong("targetDateMillis", System.currentTimeMillis() + (90L * 24 * 3600 * 1000L)),
                progressPercentage = json.optDouble("progressPercentage", 0.0).toFloat(),
                milestones = milestones,
                lastReviewedAt = json.optLong("lastReviewedAt", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            AdvisorCapitalPlan(baselineCash = baselineCash)
        }
    }

    suspend fun saveCapitalPlan(repository: FinancialRepository, plan: AdvisorCapitalPlan) {
        val json = JSONObject().apply {
            put("baselineCash", plan.baselineCash)
            put("targetCash", plan.targetCash)
            put("targetDateMillis", plan.targetDateMillis)
            put("progressPercentage", plan.progressPercentage.toDouble())
            put("lastReviewedAt", plan.lastReviewedAt)

            val arr = JSONArray()
            plan.milestones.forEach { m ->
                val mJson = JSONObject().apply {
                    put("id", m.id)
                    put("title", m.title)
                    put("description", m.description)
                    put("targetBenefitEtb", m.targetBenefitEtb)
                    put("deadlineMillis", m.deadlineMillis)
                    put("status", m.status.name)
                    m.verifiedOutcomeEtb?.let { put("verifiedOutcomeEtb", it) }
                    m.completedAt?.let { put("completedAt", it) }
                    m.sourceOpportunityId?.let { put("sourceOpportunityId", it) }
                    m.trackCategory?.let { put("trackCategory", it) }
                    m.trackKeyword?.let { put("trackKeyword", it) }
                    m.baselineMonthlyEtb?.let { put("baselineMonthlyEtb", it) }
                    m.startedAt?.let { put("startedAt", it) }
                    m.autoOutcomeEtb?.let { put("autoOutcomeEtb", it) }
                    m.autoNote?.let { put("autoNote", it) }
                    m.lastCheckedAt?.let { put("lastCheckedAt", it) }
                }
                arr.put(mJson)
            }
            put("milestones", arr)
        }
        repository.putState(KEY_CAPITAL_PLAN, json.toString())
    }

    suspend fun loadOpportunities(repository: FinancialRepository): List<AdvisorOpportunity> {
        val raw = repository.getState(KEY_OPPORTUNITIES) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<AdvisorOpportunity>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val pillar = try {
                    AdvisorPillar.valueOf(o.optString("pillar", AdvisorPillar.CAPITAL_ALLOCATION.name))
                } catch (_: Exception) {
                    AdvisorPillar.CAPITAL_ALLOCATION
                }
                val status = try {
                    OpportunityStatus.valueOf(o.optString("status", OpportunityStatus.DISCOVERED.name))
                } catch (_: Exception) {
                    OpportunityStatus.DISCOVERED
                }

                val assArr = o.optJSONArray("assumptions")
                val assumptions = mutableListOf<String>()
                if (assArr != null) {
                    for (j in 0 until assArr.length()) assumptions.add(assArr.optString(j))
                }

                list.add(
                    AdvisorOpportunity(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        title = o.optString("title", "Opportunity"),
                        pillar = pillar,
                        summary = o.optString("summary", ""),
                        estimatedBenefitEtb = o.optDouble("estimatedBenefitEtb", 0.0),
                        benefitPeriod = o.optString("benefitPeriod", "Monthly"),
                        capitalRequiredEtb = o.optDouble("capitalRequiredEtb", 0.0),
                        timeframe = o.optString("timeframe", "2-4 weeks"),
                        riskLevel = o.optString("riskLevel", "Low"),
                        confidenceScore = o.optInt("confidenceScore", 80),
                        recommendedNextStep = o.optString("recommendedNextStep", ""),
                        assumptions = assumptions,
                        sourceOrInstitution = o.optString("sourceOrInstitution", "Ethiopian Financial Market"),
                        status = status,
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        realReturnPct = if (o.has("realReturnPct")) o.optDouble("realReturnPct") else null
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveOpportunities(repository: FinancialRepository, list: List<AdvisorOpportunity>) {
        val arr = JSONArray()
        list.forEach { o ->
            val obj = JSONObject().apply {
                put("id", o.id)
                put("title", o.title)
                put("pillar", o.pillar.name)
                put("summary", o.summary)
                put("estimatedBenefitEtb", o.estimatedBenefitEtb)
                put("benefitPeriod", o.benefitPeriod)
                put("capitalRequiredEtb", o.capitalRequiredEtb)
                put("timeframe", o.timeframe)
                put("riskLevel", o.riskLevel)
                put("confidenceScore", o.confidenceScore)
                put("recommendedNextStep", o.recommendedNextStep)
                put("sourceOrInstitution", o.sourceOrInstitution)
                put("status", o.status.name)
                put("createdAt", o.createdAt)
                o.realReturnPct?.let { put("realReturnPct", it) }

                val assArr = JSONArray()
                o.assumptions.forEach { assArr.put(it) }
                put("assumptions", assArr)
            }
            arr.put(obj)
        }
        repository.putState(KEY_OPPORTUNITIES, arr.toString())
    }

    suspend fun loadExecutiveSummary(repository: FinancialRepository): ExecutiveSummary? {
        val raw = repository.getState(KEY_EXECUTIVE_SUMMARY) ?: return null
        return try {
            val json = JSONObject(raw)
            ExecutiveSummary(
                analysisTimestamp = json.optLong("analysisTimestamp", System.currentTimeMillis()),
                overview = json.optString("overview", ""),
                majorChanges = json.optString("majorChanges", ""),
                cashGrowthBarriers = toStringList(json.optJSONArray("cashGrowthBarriers")),
                topOpportunities = toStringList(json.optJSONArray("topOpportunities")),
                financialRisks = toStringList(json.optJSONArray("financialRisks")),
                priorityActions = toStringList(json.optJSONArray("priorityActions")),
                isStale = json.optBoolean("isStale", false)
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveExecutiveSummary(repository: FinancialRepository, summary: ExecutiveSummary) {
        val json = JSONObject().apply {
            put("analysisTimestamp", summary.analysisTimestamp)
            put("overview", summary.overview)
            put("majorChanges", summary.majorChanges)
            put("isStale", summary.isStale)
            put("cashGrowthBarriers", toJSONArray(summary.cashGrowthBarriers))
            put("topOpportunities", toJSONArray(summary.topOpportunities))
            put("financialRisks", toJSONArray(summary.financialRisks))
            put("priorityActions", toJSONArray(summary.priorityActions))
        }
        repository.putState(KEY_EXECUTIVE_SUMMARY, json.toString())
    }

    suspend fun loadChatMessages(repository: FinancialRepository): List<AdvisorChatMessage> {
        val raw = repository.getState(KEY_CHAT_MESSAGES) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<AdvisorChatMessage>()
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                list.add(
                    AdvisorChatMessage(
                        id = m.optString("id", UUID.randomUUID().toString()),
                        isUser = m.optBoolean("isUser", false),
                        message = m.optString("message", ""),
                        timestamp = m.optLong("timestamp", System.currentTimeMillis()),
                        suggestedActions = toStringList(m.optJSONArray("suggestedActions")),
                        toolActions = toStringList(m.optJSONArray("toolActions")),
                        sources = toStringList(m.optJSONArray("sources")),
                        rating = m.optInt("rating", 0)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveChatMessages(repository: FinancialRepository, messages: List<AdvisorChatMessage>) {
        val arr = JSONArray()
        // Save latest 30 messages
        messages.takeLast(30).forEach { m ->
            val obj = JSONObject().apply {
                put("id", m.id)
                put("isUser", m.isUser)
                put("message", m.message)
                put("timestamp", m.timestamp)
                put("suggestedActions", toJSONArray(m.suggestedActions))
                put("toolActions", toJSONArray(m.toolActions))
                put("sources", toJSONArray(m.sources))
                put("rating", m.rating)
            }
            arr.put(obj)
        }
        repository.putState(KEY_CHAT_MESSAGES, arr.toString())
    }

    suspend fun loadUsageLogs(repository: FinancialRepository): List<AdvisorUsageLog> {
        val raw = repository.getState(KEY_USAGE_LOGS) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<AdvisorUsageLog>()
            for (i in 0 until arr.length()) {
                val l = arr.optJSONObject(i) ?: continue
                list.add(
                    AdvisorUsageLog(
                        id = l.optString("id", UUID.randomUUID().toString()),
                        timestamp = l.optLong("timestamp", System.currentTimeMillis()),
                        model = l.optString("model", SupportedGeminiModels.GEMINI_3_5_FLASH),
                        latencyMs = l.optLong("latencyMs", 0L),
                        status = l.optString("status", "SUCCESS"),
                        promptTokens = if (l.has("promptTokens")) l.getInt("promptTokens") else null,
                        completionTokens = if (l.has("completionTokens")) l.getInt("completionTokens") else null,
                        estimatedCostUsd = if (l.has("estimatedCostUsd")) l.getDouble("estimatedCostUsd") else null,
                        errorMessage = l.optString("errorMessage").takeIf { !it.isNullOrBlank() }
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun appendUsageLog(repository: FinancialRepository, log: AdvisorUsageLog) {
        val existing = loadUsageLogs(repository).toMutableList()
        existing.add(0, log)
        val trimmed = existing.take(50) // keep last 50 logs
        val arr = JSONArray()
        trimmed.forEach { l ->
            val obj = JSONObject().apply {
                put("id", l.id)
                put("timestamp", l.timestamp)
                put("model", l.model)
                put("latencyMs", l.latencyMs)
                put("status", l.status)
                l.promptTokens?.let { put("promptTokens", it) }
                l.completionTokens?.let { put("completionTokens", it) }
                l.estimatedCostUsd?.let { put("estimatedCostUsd", it) }
                l.errorMessage?.let { put("errorMessage", it) }
            }
            arr.put(obj)
        }
        repository.putState(KEY_USAGE_LOGS, arr.toString())
    }

    private fun toStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list.add(arr.optString(i))
        return list
    }

    private fun toJSONArray(list: List<String>): JSONArray {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr
    }
}
