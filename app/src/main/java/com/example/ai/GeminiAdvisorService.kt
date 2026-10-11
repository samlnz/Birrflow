package com.example.ai

import com.example.ui.viewmodel.TotalCashAssetSummary

/**
 * Backward-compatibility facade delegating to [GeminiClientService].
 */
object GeminiAdvisorService {

    fun resolveApiKey(userEnteredKey: String?): String {
        val entered = userEnteredKey?.trim()
        if (!entered.isNullOrEmpty()) return entered
        return try {
            com.example.BuildConfig.GEMINI_API_KEY.trim()
        } catch (_: Throwable) {
            ""
        }
    }

    suspend fun consultAdvisor(
        apiKey: String,
        userQuery: String,
        totalAssetSummary: TotalCashAssetSummary,
        spendingBreakdownText: String,
        recentActivitySummary: String
    ): Result<String> {
        val snapshot = AdvisorFinancialSnapshot(
            totalRecordedBalance = totalAssetSummary.totalCashAsset,
            availableLiquidCash = (totalAssetSummary.totalCashAsset - 20_000.0).coerceAtLeast(0.0),
            currentMonthIncome = totalAssetSummary.totalInflowToday * 10,
            currentMonthExpense = totalAssetSummary.totalOutflowToday * 10,
            netCashFlow = totalAssetSummary.netFlowToday,
            inflowsToday = totalAssetSummary.totalInflowToday,
            outflowsToday = totalAssetSummary.totalOutflowToday,
            activePhonesCount = totalAssetSummary.activePhoneCount
        )

        return GeminiClientService.sendConsultationMessage(
            apiKey = apiKey,
            model = SupportedGeminiModels.GEMINI_3_5_FLASH,
            userQuery = userQuery.ifBlank { "Provide an actionable capital growth strategy for my current assets." },
            conversationHistory = emptyList(),
            snapshot = snapshot
        ).map { it.message }
    }
}
