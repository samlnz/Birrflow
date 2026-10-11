package com.example.ai

import com.example.data.entity.FlowDirection
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransactionType
import com.example.ui.viewmodel.CategorySpend
import java.util.Calendar

/**
 * Deterministic financial calculations engine for the AI Capital Growth Advisor.
 * Computes exact monetary figures and metrics without hallucination or approximation.
 */
object FinancialSnapshotService {

    fun computeSnapshot(
        bankBalance: Double,
        manualBalance: Double,
        transactions: List<TransactionEntity>,
        categorySpends: List<CategorySpend>,
        activeSourcesCount: Int = 1,
        activePhonesCount: Int = 1,
        nowMillis: Long = System.currentTimeMillis(),
        // Months of spending the user wants kept as emergency reserve (About Me; default 3)
        emergencyMonths: Int = 3
    ): AdvisorFinancialSnapshot {
        val totalRecorded = (bankBalance + manualBalance).coerceAtLeast(0.0)

        // Calculate this month's calendar boundaries
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)

        // Today's boundaries
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfToday = cal.timeInMillis

        var monthIncome = 0.0
        var monthExpense = 0.0
        var todayInflow = 0.0
        var todayOutflow = 0.0

        for (tx in transactions) {
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.createdTimestamp }
            val isCurrentMonth = txCal.get(Calendar.MONTH) == currentMonth && txCal.get(Calendar.YEAR) == currentYear
            val isToday = tx.createdTimestamp >= startOfToday

            // Moving money between own accounts is neither income nor spending; only its fee is a cost
            if (tx.transactionType == TransactionType.INTERNAL_TRANSFER) {
                if (isCurrentMonth) monthExpense += tx.fee
                if (isToday) todayOutflow += tx.fee
                continue
            }

            val isCredit = tx.direction == FlowDirection.CREDIT ||
                tx.transactionType == TransactionType.INCOME ||
                tx.transactionType == TransactionType.DEPOSIT

            val isDebit = tx.direction == FlowDirection.DEBIT ||
                tx.transactionType == TransactionType.EXPENSE ||
                tx.transactionType == TransactionType.WITHDRAWAL ||
                tx.transactionType == TransactionType.FEE

            if (isCurrentMonth) {
                if (isCredit) monthIncome += tx.amount
                if (isDebit) monthExpense += (tx.amount + tx.fee)
            }

            if (isToday) {
                if (isCredit) todayInflow += tx.amount
                if (isDebit) todayOutflow += (tx.amount + tx.fee)
            }
        }

        val netCashFlow = monthIncome - monthExpense
        val dailyBurnRate = if (dayOfMonth > 0) monthExpense / dayOfMonth else 0.0
        val monthlyEstimatedBurn = dailyBurnRate * 30.0

        // Runway in months: how long liquid capital lasts if no new income
        val runwayMonths = if (monthlyEstimatedBurn > 0) {
            (totalRecorded / monthlyEstimatedBurn).coerceAtLeast(0.0)
        } else if (totalRecorded > 0) {
            12.0 // Healthy baseline if no current month burn
        } else {
            0.0
        }

        // Emergency reserve required: 3 months of baseline burn
        val emergencyReserveRequired = (monthlyEstimatedBurn * emergencyMonths.coerceIn(1, 24)).coerceAtLeast(15_000.0)
        val reserveRatio = if (emergencyReserveRequired > 0) totalRecorded / emergencyReserveRequired else 1.0
        val reserveStatus = when {
            reserveRatio >= 1.0 -> "Adequate (3+ mo reserve)"
            reserveRatio >= 0.5 -> "Moderate (1-2 mo reserve)"
            else -> "Critical (under 1 mo reserve)"
        }

        // Available liquid cash after setting aside emergency reserve
        val availableLiquid = (totalRecorded - emergencyReserveRequired).coerceAtLeast(0.0)

        fun item(spend: CategorySpend) = CategorySpendItem(
            categoryName = spend.category.name,
            totalAmount = spend.totalAmount,
            percentage = spend.percentage,
            colorHex = spend.category.colorHex,
            description = spend.category.description,
            transactionCount = spend.transactionCount
        )
        val topSpends = categorySpends.filter { it.totalAmount > 0 }.sortedByDescending { it.totalAmount }.take(5).map { item(it) }
        val categoryProfiles = categorySpends.filter { !it.isUncategorized }.map { item(it) }
        val uncategorized = categorySpends.firstOrNull { it.isUncategorized }

        return AdvisorFinancialSnapshot(
            totalRecordedBalance = totalRecorded,
            availableLiquidCash = availableLiquid,
            currentMonthIncome = monthIncome,
            currentMonthExpense = monthExpense,
            netCashFlow = netCashFlow,
            burnRateDaily = dailyBurnRate,
            runwayMonths = runwayMonths,
            emergencyReserveRequired = emergencyReserveRequired,
            emergencyReserveStatus = reserveStatus,
            topSpendingCategories = topSpends,
            expenseCategoryProfiles = categoryProfiles,
            uncategorizedExpense = uncategorized?.totalAmount ?: 0.0,
            uncategorizedCount = uncategorized?.transactionCount ?: 0,
            inflowsToday = todayInflow,
            outflowsToday = todayOutflow,
            activeSourcesCount = activeSourcesCount,
            activePhonesCount = activePhonesCount,
            timestamp = nowMillis
        )
    }
}
