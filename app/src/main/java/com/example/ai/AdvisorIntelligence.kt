package com.example.ai

import com.example.data.entity.CategoryEntity
import com.example.data.entity.FlowDirection
import com.example.data.entity.IncomeSourceEntity
import com.example.data.entity.SavingsGoalEntity
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransactionType
import com.example.ui.components.formatCurrency
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

enum class AlertSeverity { CRITICAL, WARNING, INFO, POSITIVE }

/** A finding computed on the phone (no API call), with a follow-up question for the advisor chat. */
data class SmartAlert(
    val severity: AlertSeverity,
    val title: String,
    val detail: String,
    val suggestedQuestion: String? = null
)

data class MonthlyFlow(val label: String, val income: Double, val expense: Double) {
    val net: Double get() = income - expense
}

data class RecurringPayment(
    val counterparty: String,
    val averageAmount: Double,
    val occurrences: Int,
    val cadenceDays: Int,
    val lastPaidMillis: Long,
    val nextExpectedMillis: Long,
    val categoryName: String?
)

data class SpendingAnomaly(
    val label: String,
    val amount: Double,
    val typicalAmount: Double,
    val timestamp: Long
)

data class CounterpartyTotal(val name: String, val amount: Double, val count: Int)

data class CashForecast(
    val currentBalance: Double = 0.0,
    val averageDailyNet: Double = 0.0,
    val in30Days: Double = 0.0,
    val in60Days: Double = 0.0,
    val in90Days: Double = 0.0,
    // Days until the balance reaches zero at the current trend; null when it is not trending down
    val daysUntilEmpty: Int? = null,
    val basedOnDays: Int = 0
)

data class GoalProgress(
    val name: String,
    val target: Double,
    val current: Double,
    val targetDate: String,
    val monthlyNeeded: Double,
    val onTrack: Boolean
)

/** Everything the advisor knows beyond the headline snapshot; computed locally from the user's data. */
data class AdvisorDeepContext(
    val monthlyHistory: List<MonthlyFlow> = emptyList(),
    val recurringPayments: List<RecurringPayment> = emptyList(),
    val anomalies: List<SpendingAnomaly> = emptyList(),
    val topCounterparties: List<CounterpartyTotal> = emptyList(),
    val incomeBySource: List<CounterpartyTotal> = emptyList(),
    val weekdaySpend: List<Pair<String, Double>> = emptyList(),
    val forecast: CashForecast = CashForecast(),
    // Share of income kept over the last three full months; null without income
    val savingsRate: Double? = null,
    // Month-to-date spending vs. the same days of last month, in percent; null without a baseline
    val expenseChangePct: Double? = null,
    val uncategorizedCount: Int = 0,
    val uncategorizedAmount: Double = 0.0,
    val goals: List<GoalProgress> = emptyList(),
    val overdueMilestones: List<String> = emptyList(),
    val alerts: List<SmartAlert> = emptyList(),
    // What past advice achieved, measured from transactions (see DecisionTracker)
    val trackRecord: AdviceTrackRecord = AdviceTrackRecord()
)

/**
 * Deterministic analytics behind the AI advisor: cash forecast, recurring payments, anomalies,
 * trends and alerts. The model receives these as facts instead of guessing them.
 */
object AdvisorIntelligence {

    private const val DAY_MS = 24L * 3600 * 1000

    /** Money in / out of one transaction; internal transfers only count their fee. */
    fun flowOf(tx: TransactionEntity): Pair<Double, Double> {
        if (tx.transactionType == TransactionType.INTERNAL_TRANSFER) return 0.0 to tx.fee.coerceAtLeast(0.0)
        return when {
            tx.direction == FlowDirection.CREDIT ||
                tx.transactionType == TransactionType.INCOME ||
                tx.transactionType == TransactionType.DEPOSIT -> tx.amount to 0.0
            tx.direction == FlowDirection.DEBIT ||
                tx.transactionType == TransactionType.EXPENSE ||
                tx.transactionType == TransactionType.WITHDRAWAL ||
                tx.transactionType == TransactionType.FEE ||
                tx.transactionType == TransactionType.TRANSFER -> 0.0 to (tx.amount + tx.fee)
            else -> 0.0 to 0.0
        }
    }

    fun counterpartyOf(tx: TransactionEntity): String? {
        val isCredit = flowOf(tx).first > 0
        val raw = if (isCredit) tx.sender ?: tx.merchant else tx.merchant ?: tx.recipient
        return raw?.trim()?.takeIf { it.length >= 2 }
    }

    fun analyze(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        incomeSources: List<IncomeSourceEntity>,
        goals: List<SavingsGoalEntity>,
        plan: AdvisorCapitalPlan,
        currentBalance: Double,
        nowMillis: Long = System.currentTimeMillis()
    ): AdvisorDeepContext {
        val categoryNames = categories.associate { it.categoryId to it.name }
        val txs = transactions.filter { it.createdTimestamp in 1..nowMillis }
        val debits = txs.filter { it.transactionType != TransactionType.INTERNAL_TRANSFER && flowOf(it).second > 0 }
        val credits = txs.filter { it.transactionType != TransactionType.INTERNAL_TRANSFER && flowOf(it).first > 0 }

        val monthly = monthlyHistory(txs, nowMillis, months = 6)
        val recurring = detectRecurring(debits, categoryNames, nowMillis)
        val anomalies = detectAnomalies(debits, nowMillis)
        val forecast = forecast(txs, currentBalance, nowMillis)

        val since90 = nowMillis - 90 * DAY_MS
        val topCounterparties = debits.filter { it.createdTimestamp >= since90 }
            .mapNotNull { tx -> counterpartyOf(tx)?.let { it to tx } }
            .groupBy({ it.first.lowercase(Locale.ROOT) }, { it })
            .map { (_, list) -> CounterpartyTotal(list.first().first, list.sumOf { it.second.amount + it.second.fee }, list.size) }
            .sortedByDescending { it.amount }
            .take(8)

        val sourceNames = incomeSources.associate { it.sourceId to it.name }
        val incomeBySource = credits.filter { it.createdTimestamp >= since90 }
            .groupBy { tx -> tx.categoryId?.let { sourceNames[it] } ?: "Unassigned income" }
            .map { (name, list) -> CounterpartyTotal(name, list.sumOf { it.amount }, list.size) }
            .sortedByDescending { it.amount }

        val weekdayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val weekday = DoubleArray(7)
        val cal = Calendar.getInstance()
        debits.filter { it.createdTimestamp >= since90 }.forEach { tx ->
            cal.timeInMillis = tx.createdTimestamp
            weekday[cal.get(Calendar.DAY_OF_WEEK) - 1] += tx.amount + tx.fee
        }
        val weekdaySpend = weekdayNames.indices.map { weekdayNames[it] to weekday[it] }

        // Savings rate over the last three complete months (current month is partial)
        val fullMonths = monthly.dropLast(1).takeLast(3)
        val income3 = fullMonths.sumOf { it.income }
        val savingsRate = if (income3 > 0) fullMonths.sumOf { it.net } / income3 else null

        val expenseChange = expenseChangeVsLastMonth(debits, nowMillis)

        val uncategorized = txs.filter {
            (it.direction == FlowDirection.DEBIT || flowOf(it).second > 0) &&
                it.transactionType != TransactionType.INTERNAL_TRANSFER &&
                (it.categoryId.isNullOrBlank() || it.categoryId == "uncategorized" || it.categoryId !in categoryNames)
        }

        val goalProgress = goals.map { goalProgressOf(it, nowMillis) }
        val overdue = plan.milestones
            .filter { it.status != MilestoneStatus.COMPLETED && it.deadlineMillis < nowMillis }
            .map { it.title }

        val base = AdvisorDeepContext(
            monthlyHistory = monthly,
            recurringPayments = recurring,
            anomalies = anomalies,
            topCounterparties = topCounterparties,
            incomeBySource = incomeBySource,
            weekdaySpend = weekdaySpend,
            forecast = forecast,
            savingsRate = savingsRate,
            expenseChangePct = expenseChange,
            uncategorizedCount = uncategorized.size,
            uncategorizedAmount = uncategorized.sumOf { it.amount + it.fee },
            goals = goalProgress,
            overdueMilestones = overdue,
            trackRecord = DecisionTracker.trackRecord(plan)
        )
        return base.copy(alerts = buildAlerts(base, nowMillis))
    }

    fun monthlyHistory(txs: List<TransactionEntity>, nowMillis: Long, months: Int): List<MonthlyFlow> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -(months - 1))
        }
        val fmt = SimpleDateFormat("MMM yyyy", Locale.US)
        val starts = ArrayList<Long>()
        val labels = ArrayList<String>()
        repeat(months + 1) {
            starts += cal.timeInMillis
            labels += fmt.format(cal.time)
            cal.add(Calendar.MONTH, 1)
        }
        return (0 until months).map { i ->
            val inMonth = txs.filter { it.createdTimestamp >= starts[i] && it.createdTimestamp < starts[i + 1] }
            MonthlyFlow(labels[i], inMonth.sumOf { flowOf(it).first }, inMonth.sumOf { flowOf(it).second })
        }
    }

    /** Same counterparty, similar amount, at a steady weekly-to-monthly rhythm, at least three times. */
    fun detectRecurring(debits: List<TransactionEntity>, categoryNames: Map<String, String>, nowMillis: Long): List<RecurringPayment> {
        val since = nowMillis - 200 * DAY_MS
        return debits.filter { it.createdTimestamp >= since }
            .mapNotNull { tx -> counterpartyOf(tx)?.let { it.lowercase(Locale.ROOT) to tx } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (_, list) ->
                if (list.size < 3) return@mapNotNull null
                val sorted = list.sortedBy { it.createdTimestamp }
                val amounts = sorted.map { it.amount }
                val mean = amounts.average()
                if (mean <= 0) return@mapNotNull null
                val sd = sqrt(amounts.sumOf { (it - mean) * (it - mean) } / amounts.size)
                if (sd / mean > 0.25) return@mapNotNull null
                val gaps = sorted.zipWithNext { a, b -> (b.createdTimestamp - a.createdTimestamp).toDouble() / DAY_MS }
                val cadence = median(gaps)
                if (cadence < 5 || cadence > 40) return@mapNotNull null
                val regular = gaps.count { abs(it - cadence) <= cadence * 0.4 }
                if (regular < (gaps.size + 1) / 2) return@mapNotNull null
                val last = sorted.last()
                val category = sorted.mapNotNull { it.categoryId?.let(categoryNames::get) }
                    .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                RecurringPayment(
                    counterparty = counterpartyOf(last) ?: "Unknown",
                    averageAmount = mean,
                    occurrences = sorted.size,
                    cadenceDays = cadence.toInt(),
                    lastPaidMillis = last.createdTimestamp,
                    nextExpectedMillis = last.createdTimestamp + (cadence * DAY_MS).toLong(),
                    categoryName = category
                )
            }
            .sortedByDescending { it.averageAmount * (30.0 / it.cadenceDays.coerceAtLeast(1)) }
            .take(10)
    }

    /** Debits of the last 30 days far above the user's usual debit size. */
    fun detectAnomalies(debits: List<TransactionEntity>, nowMillis: Long): List<SpendingAnomaly> {
        val history = debits.filter { it.createdTimestamp >= nowMillis - 120 * DAY_MS }.map { it.amount }
        if (history.size < 8) return emptyList()
        val med = median(history)
        val mean = history.average()
        val sd = sqrt(history.sumOf { (it - mean) * (it - mean) } / history.size)
        val threshold = maxOf(med * 4, mean + 3 * sd, 500.0)
        return debits
            .filter { it.createdTimestamp >= nowMillis - 30 * DAY_MS && it.amount > threshold }
            .sortedByDescending { it.amount }
            .take(5)
            .map { SpendingAnomaly(counterpartyOf(it) ?: it.description.take(40).ifBlank { "Debit" }, it.amount, med, it.createdTimestamp) }
    }

    fun forecast(txs: List<TransactionEntity>, currentBalance: Double, nowMillis: Long): CashForecast {
        if (txs.isEmpty()) return CashForecast(currentBalance = currentBalance, in30Days = currentBalance, in60Days = currentBalance, in90Days = currentBalance)
        val window = 90
        val since = nowMillis - window * DAY_MS
        val recent = txs.filter { it.createdTimestamp >= since }
        val first = recent.minOfOrNull { it.createdTimestamp } ?: nowMillis
        val days = ((nowMillis - first) / DAY_MS).toInt().coerceIn(14, window)
        val net = recent.sumOf { flowOf(it).first - flowOf(it).second }
        val daily = net / days
        val empty = if (daily < 0 && currentBalance > 0) (currentBalance / -daily).toInt().takeIf { it <= 365 } else null
        return CashForecast(
            currentBalance = currentBalance,
            averageDailyNet = daily,
            in30Days = currentBalance + daily * 30,
            in60Days = currentBalance + daily * 60,
            in90Days = currentBalance + daily * 90,
            daysUntilEmpty = empty,
            basedOnDays = days
        )
    }

    private fun expenseChangeVsLastMonth(debits: List<TransactionEntity>, nowMillis: Long): Double? {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val thisStart = cal.timeInMillis
        cal.add(Calendar.MONTH, -1)
        val lastStart = cal.timeInMillis
        val lastSameDay = lastStart + (dayOfMonth.toLong() * DAY_MS)
        val thisMonth = debits.filter { it.createdTimestamp >= thisStart }.sumOf { it.amount + it.fee }
        val lastMonth = debits.filter { it.createdTimestamp in lastStart until minOf(lastSameDay, thisStart) }.sumOf { it.amount + it.fee }
        if (lastMonth <= 0.0) return null
        return (thisMonth - lastMonth) / lastMonth * 100.0
    }

    private fun goalProgressOf(goal: SavingsGoalEntity, nowMillis: Long): GoalProgress {
        val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)
        val targetMillis = parseDate(goal.targetDate)
        val monthsLeft = if (targetMillis != null) ((targetMillis - nowMillis).toDouble() / (30.0 * DAY_MS)).coerceAtLeast(0.0) else null
        val monthlyNeeded = when {
            remaining <= 0.0 -> 0.0
            monthsLeft == null -> remaining / 12.0
            monthsLeft < 1.0 -> remaining
            else -> remaining / monthsLeft
        }
        val elapsedShare = if (targetMillis != null && targetMillis > goal.createdAt) {
            ((nowMillis - goal.createdAt).toDouble() / (targetMillis - goal.createdAt)).coerceIn(0.0, 1.0)
        } else 0.0
        val savedShare = if (goal.targetAmount > 0) goal.currentAmount / goal.targetAmount else 1.0
        return GoalProgress(goal.goalName, goal.targetAmount, goal.currentAmount, goal.targetDate, monthlyNeeded, savedShare + 0.05 >= elapsedShare)
    }

    private fun buildAlerts(ctx: AdvisorDeepContext, nowMillis: Long): List<SmartAlert> {
        val alerts = ArrayList<SmartAlert>()
        fun alert(severity: AlertSeverity, title: String, detail: String, question: String?) {
            alerts.add(SmartAlert(severity, title, detail, question))
        }
        val dateFmt = SimpleDateFormat("EEE d MMM", Locale.US)

        ctx.forecast.daysUntilEmpty?.let { days ->
            if (days <= 90) {
                alert(
                    if (days <= 45) AlertSeverity.CRITICAL else AlertSeverity.WARNING,
                    "Cash could run out in about $days days",
                    "Over the last ${ctx.forecast.basedOnDays} days you averaged ${formatCurrency(ctx.forecast.averageDailyNet)} per day.",
                    "My cash is trending to zero in about $days days. What should I cut or change first?"
                )
            }
        }
        ctx.expenseChangePct?.let { pct ->
            if (pct >= 25) {
                alert(
                    AlertSeverity.WARNING,
                    "Spending is up ${pct.toInt()}% vs last month",
                    "Compared with the same days of last month.",
                    "Why is my spending up ${pct.toInt()}% this month and where exactly?"
                )
            } else if (pct <= -15) {
                alert(
                    AlertSeverity.POSITIVE,
                    "Spending is down ${(-pct).toInt()}% vs last month",
                    "Compared with the same days of last month.",
                    "How should I put the money I saved this month to work?"
                )
            }
        }
        ctx.anomalies.firstOrNull()?.let { a ->
            alert(
                AlertSeverity.WARNING,
                "Unusual debit: ${formatCurrency(a.amount)} to ${a.label}",
                "Your typical debit is about ${formatCurrency(a.typicalAmount)}.",
                "Explain my unusual payment of ${formatCurrency(a.amount)} to ${a.label} and whether I should worry."
            )
        }
        ctx.recurringPayments
            .filter { it.nextExpectedMillis in nowMillis..(nowMillis + 7 * DAY_MS) }
            .take(2)
            .forEach { r ->
                alert(
                    AlertSeverity.INFO,
                    "Upcoming: ~${formatCurrency(r.averageAmount)} to ${r.counterparty}",
                    "Usually every ${r.cadenceDays} days · expected ${dateFmt.format(Date(r.nextExpectedMillis))}.",
                    "List my recurring payments and which ones I could reduce or renegotiate."
                )
            }
        if (ctx.uncategorizedCount >= 5) {
            alert(
                AlertSeverity.INFO,
                "${ctx.uncategorizedCount} debits are uncategorized",
                "${formatCurrency(ctx.uncategorizedAmount)} the advisor cannot attribute yet.",
                "Look at my uncategorized expenses and categorize them using my category descriptions."
            )
        }
        ctx.savingsRate?.let { rate ->
            when {
                rate < 0 -> alert(
                    AlertSeverity.WARNING,
                    "You spent more than you earned (last 3 months)",
                    "Savings rate ${(rate * 100).toInt()}%.",
                    "Build me a plan to get back to a positive savings rate within 2 months."
                )
                rate >= 0.2 -> alert(
                    AlertSeverity.POSITIVE,
                    "Strong savings rate: ${(rate * 100).toInt()}%",
                    "Over the last 3 full months.",
                    "Where should I invest my monthly surplus safely in Ethiopia?"
                )
                else -> Unit
            }
        }
        if (ctx.overdueMilestones.isNotEmpty()) {
            alert(
                AlertSeverity.WARNING,
                "${ctx.overdueMilestones.size} capital plan milestone(s) overdue",
                ctx.overdueMilestones.take(3).joinToString(" · "),
                "Help me re-plan my overdue capital plan milestones."
            )
        }
        ctx.goals.filter { !it.onTrack && it.current < it.target }.take(1).forEach { g ->
            alert(
                AlertSeverity.INFO,
                "Goal behind schedule: ${g.name}",
                "Needs about ${formatCurrency(g.monthlyNeeded)} per month to reach ${formatCurrency(g.target)} by ${g.targetDate}.",
                "How can I catch up on my savings goal '${g.name}'?"
            )
        }
        return alerts.sortedBy { it.severity.ordinal }
    }

    /** Facts block appended to the advisor's system instruction. */
    fun toPromptSection(ctx: AdvisorDeepContext): String = buildString {
        appendLine("DEEP CONTEXT (computed on-device from the user's own transactions; treat as facts):")
        if (ctx.monthlyHistory.isNotEmpty()) {
            appendLine("• Monthly history (income / expense / net): " + ctx.monthlyHistory.joinToString("; ") {
                "${it.label}: ${formatCurrency(it.income)} / ${formatCurrency(it.expense)} / ${formatCurrency(it.net)}"
            })
        }
        ctx.savingsRate?.let { appendLine("• Savings rate (last 3 full months): ${(it * 100).toInt()}%") }
        ctx.expenseChangePct?.let { appendLine("• Month-to-date spending vs same days last month: ${if (it >= 0) "+" else ""}${it.toInt()}%") }
        with(ctx.forecast) {
            appendLine(
                "• Cash forecast at the ${basedOnDays}-day trend (${formatCurrency(averageDailyNet)}/day): " +
                    "30d ${formatCurrency(in30Days)}, 60d ${formatCurrency(in60Days)}, 90d ${formatCurrency(in90Days)}" +
                    (daysUntilEmpty?.let { ", balance reaches zero in ~$it days" } ?: "")
            )
        }
        if (ctx.recurringPayments.isNotEmpty()) {
            appendLine("• Recurring payments: " + ctx.recurringPayments.joinToString("; ") {
                "${it.counterparty} ~${formatCurrency(it.averageAmount)} every ${it.cadenceDays}d (${it.occurrences}x${it.categoryName?.let { c -> ", $c" } ?: ""})"
            })
        }
        if (ctx.topCounterparties.isNotEmpty()) {
            appendLine("• Top payees (90d): " + ctx.topCounterparties.joinToString("; ") { "${it.name} ${formatCurrency(it.amount)} (${it.count}x)" })
        }
        if (ctx.incomeBySource.isNotEmpty()) {
            appendLine("• Income by source (90d): " + ctx.incomeBySource.joinToString("; ") { "${it.name} ${formatCurrency(it.amount)} (${it.count}x)" })
        }
        if (ctx.anomalies.isNotEmpty()) {
            appendLine("• Unusual debits (30d): " + ctx.anomalies.joinToString("; ") { "${it.label} ${formatCurrency(it.amount)} (typical ${formatCurrency(it.typicalAmount)})" })
        }
        if (ctx.weekdaySpend.any { it.second > 0 }) {
            appendLine("• Spending by weekday (90d): " + ctx.weekdaySpend.joinToString("; ") { "${it.first} ${formatCurrency(it.second)}" })
        }
        appendLine("• Uncategorized debits: ${ctx.uncategorizedCount} totalling ${formatCurrency(ctx.uncategorizedAmount)}")
        if (ctx.goals.isNotEmpty()) {
            appendLine("• Savings goals: " + ctx.goals.joinToString("; ") {
                "${it.name} ${formatCurrency(it.current)}/${formatCurrency(it.target)} by ${it.targetDate} (needs ${formatCurrency(it.monthlyNeeded)}/mo, ${if (it.onTrack) "on track" else "behind"})"
            })
        }
        if (ctx.overdueMilestones.isNotEmpty()) appendLine("• Overdue plan milestones: " + ctx.overdueMilestones.joinToString("; "))
    }.trimEnd()

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    private fun parseDate(text: String): Long? {
        for (pattern in listOf("dd/MM/yyyy", "yyyy-MM-dd", "d/M/yyyy")) {
            try {
                return SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(text.trim())?.time
            } catch (_: Exception) {
            }
        }
        return null
    }
}
