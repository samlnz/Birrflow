package com.example.ai

import com.example.BuildConfig

/**
 * Modern supported Google Gemini models verified against official documentation.
 * Strictly adheres to gemini-api guidelines (never uses deprecated 1.5/2.0 models).
 */
object SupportedGeminiModels {
    const val GEMINI_3_5_FLASH = "gemini-3.5-flash"
    const val GEMINI_3_1_PRO = "gemini-3.1-pro-preview"
    const val GEMINI_3_1_FLASH_LITE = "gemini-3.1-flash-lite-preview"
    const val GEMINI_FLASH_LATEST = "gemini-flash-latest"

    val ALL = listOf(
        ModelOption(
            id = GEMINI_3_5_FLASH,
            displayName = "Gemini 3.5 Flash (Recommended)",
            description = "Fast, cost-efficient, optimal for real-time financial advisory and Q&A.",
            isDefault = true
        ),
        ModelOption(
            id = GEMINI_3_1_PRO,
            displayName = "Gemini 3.1 Pro Preview",
            description = "Advanced reasoning, best for complex capital allocation & business cases.",
            isDefault = false
        ),
        ModelOption(
            id = GEMINI_3_1_FLASH_LITE,
            displayName = "Gemini 3.1 Flash Lite Preview",
            description = "Ultra-low latency and lightweight for rapid interaction.",
            isDefault = false
        ),
        ModelOption(
            id = GEMINI_FLASH_LATEST,
            displayName = "Gemini Flash Latest",
            description = "Latest stable general-purpose Gemini Flash model.",
            isDefault = false
        )
    )

    fun find(modelId: String): ModelOption = ALL.firstOrNull { it.id == modelId } ?: ALL.first()
}

data class ModelOption(
    val id: String,
    val displayName: String,
    val description: String,
    val isDefault: Boolean
)

enum class GeminiConnectionStatus(val label: String) {
    NOT_CONFIGURED("Not configured"),
    CONFIGURED_UNTESTED("Configured, connection not tested"),
    CONNECTED("Connected"),
    AUTH_FAILED("Authentication failed (Invalid Key)"),
    QUOTA_EXCEEDED("API Quota exceeded"),
    RATE_LIMITED("Rate limited"),
    SERVICE_UNAVAILABLE("Temporarily unavailable"),
    NETWORK_TIMEOUT("Connection timed out")
}

data class GeminiConfig(
    val apiKey: String = "",
    val selectedModel: String = SupportedGeminiModels.GEMINI_3_5_FLASH,
    val status: GeminiConnectionStatus = GeminiConnectionStatus.NOT_CONFIGURED,
    val lastTestedAt: Long? = null,
    val lastLatencyMs: Long? = null,
    val lastError: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() || resolveEffectiveKey().isNotBlank()

    val maskedKey: String
        get() {
            val key = resolveEffectiveKey()
            return if (key.length >= 8) {
                "••••••••••••" + key.takeLast(4)
            } else if (key.isNotEmpty()) {
                "••••••••"
            } else {
                "No API key saved"
            }
        }

    fun resolveEffectiveKey(): String {
        if (apiKey.isNotBlank()) return apiKey.trim()
        return try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (_: Throwable) {
            ""
        }
    }
}

data class ConnectionTestResult(
    val success: Boolean,
    val status: GeminiConnectionStatus,
    val latencyMs: Long,
    val modelUsed: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Six core financial pillars of the AI Capital Growth Advisor.
 */
enum class AdvisorPillar(val displayName: String, val colorHex: String, val iconName: String) {
    EXPENSE_OPTIMIZATION("Expense Optimization", "#EF4444", "trending_down"),
    INCOME_GROWTH("Income Growth", "#10B981", "trending_up"),
    DEBT_MANAGEMENT("Debt Optimization", "#F59E0B", "account_balance"),
    BUSINESS_OPPORTUNITY("Business Opportunity", "#8B5CF6", "storefront"),
    INVESTMENT("Investment Evaluation", "#3B82F6", "payments"),
    CAPITAL_ALLOCATION("Capital Allocation", "#06B6D4", "savings")
}

enum class OpportunityStatus {
    DISCOVERED,
    PLANNED,
    IN_PROGRESS,
    COMPLETED,
    DISMISSED
}

data class AdvisorOpportunity(
    val id: String,
    val title: String,
    val pillar: AdvisorPillar,
    val summary: String,
    val estimatedBenefitEtb: Double,
    val benefitPeriod: String = "Monthly", // "Monthly", "One-time", "Annual"
    val capitalRequiredEtb: Double = 0.0,
    val timeframe: String = "2-4 weeks",
    val riskLevel: String = "Low", // "Low", "Medium", "High"
    val confidenceScore: Int = 85, // 0 - 100
    val recommendedNextStep: String,
    val assumptions: List<String> = emptyList(),
    val sourceOrInstitution: String = "Ethiopian Financial Market",
    val status: OpportunityStatus = OpportunityStatus.DISCOVERED,
    val createdAt: Long = System.currentTimeMillis(),
    // Yearly return on the capital after inflation, when the benefit and capital allow computing it
    val realReturnPct: Double? = null
)

enum class MilestoneStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    POSTPONED
}

data class PlanMilestone(
    val id: String,
    val title: String,
    val description: String,
    val targetBenefitEtb: Double,
    val deadlineMillis: Long,
    val status: MilestoneStatus = MilestoneStatus.PENDING,
    val verifiedOutcomeEtb: Double? = null,
    val completedAt: Long? = null,
    // The AI opportunity this milestone was created from ("Add to Capital Plan"), if any
    val sourceOpportunityId: String? = null,
    // Automatic outcome check (DecisionTracker): spending category to bring down, or a payee/keyword
    // whose payments show the action was taken
    val trackCategory: String? = null,
    val trackKeyword: String? = null,
    val baselineMonthlyEtb: Double? = null,
    val startedAt: Long? = null,
    val autoOutcomeEtb: Double? = null,
    val autoNote: String? = null,
    val lastCheckedAt: Long? = null
)

data class AdvisorCapitalPlan(
    val baselineCash: Double = 0.0,
    val targetCash: Double = 0.0,
    val targetDateMillis: Long = System.currentTimeMillis() + (90L * 24 * 3600 * 1000L), // 90 days default
    val progressPercentage: Float = 0.0f,
    val milestones: List<PlanMilestone> = emptyList(),
    val lastReviewedAt: Long = System.currentTimeMillis()
)

data class CategorySpendItem(
    val categoryName: String,
    val totalAmount: Double,
    val percentage: Float,
    val colorHex: String = "#10B981",
    // User-written explanation of the expense type (set when the category is created)
    val description: String = "",
    val transactionCount: Int = 0
)

data class AdvisorFinancialSnapshot(
    val totalRecordedBalance: Double = 0.0,
    val availableLiquidCash: Double = 0.0,
    val currentMonthIncome: Double = 0.0,
    val currentMonthExpense: Double = 0.0,
    val netCashFlow: Double = 0.0,
    val burnRateDaily: Double = 0.0,
    val runwayMonths: Double = 0.0,
    val emergencyReserveRequired: Double = 0.0,
    val emergencyReserveStatus: String = "Adequate",
    val topSpendingCategories: List<CategorySpendItem> = emptyList(),
    // Every manual expense category with the user's description of what it covers
    val expenseCategoryProfiles: List<CategorySpendItem> = emptyList(),
    val uncategorizedExpense: Double = 0.0,
    val uncategorizedCount: Int = 0,
    val inflowsToday: Double = 0.0,
    val outflowsToday: Double = 0.0,
    val activeSourcesCount: Int = 0,
    val activePhonesCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    // Forecast, recurring payments, anomalies, trends and alerts (see AdvisorIntelligence)
    val deepContext: AdvisorDeepContext = AdvisorDeepContext(),
    // Ethiopian market figures with dates and sources (see MarketDataService)
    val market: MarketSnapshot = MarketSnapshot(),
    // Local options ranked by real return for the deployable cash (see InvestmentCatalog)
    val rankedOptions: List<RankedOption> = emptyList(),
    // Everything owned and owed, beyond bank balances (see NetWorthCalculator)
    val netWorth: NetWorthSummary = NetWorthSummary(),
    // What the user wrote about themselves in About Me
    val profile: UserProfile = UserProfile()
)

data class ExecutiveSummary(
    val analysisTimestamp: Long = System.currentTimeMillis(),
    val overview: String,
    val majorChanges: String,
    val cashGrowthBarriers: List<String>,
    val topOpportunities: List<String>,
    val financialRisks: List<String>,
    val priorityActions: List<String>,
    val isStale: Boolean = false
)

data class AdvisorChatMessage(
    val id: String,
    val isUser: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val suggestedActions: List<String> = emptyList(),
    // What the agent looked up or changed while answering ("Looked up spending breakdown (30 days)")
    val toolActions: List<String> = emptyList(),
    // Web pages the answer was grounded on (live web mode), as "title — url"
    val sources: List<String> = emptyList(),
    // User feedback on an advisor answer: 1 helpful, -1 not helpful, 0 none
    val rating: Int = 0
)

data class AdvisorUsageLog(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val model: String,
    val latencyMs: Long,
    val status: String, // "SUCCESS", "FAILED"
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val estimatedCostUsd: Double? = null,
    val errorMessage: String? = null
)
