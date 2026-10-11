package com.example.ai

import com.example.data.entity.CategoryEntity
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransactionType
import com.example.ui.components.formatCurrency
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How well past advice has worked, from milestones the tracker could measure. */
data class AdviceTrackRecord(
    val accepted: Int = 0,
    val completed: Int = 0,
    val measured: Int = 0,
    // Sum of measured monthly savings and detected amounts moved for tracked milestones
    val measuredGainEtb: Double = 0.0,
    val expectedGainEtb: Double = 0.0,
    val lines: List<String> = emptyList()
)

/**
 * Checks capital-plan milestones against what really happened in the user's transactions:
 * a spending category that should come down is compared with its pre-plan monthly average, and a
 * payee/keyword milestone looks for the payments that prove the action was taken.
 */
object DecisionTracker {

    private const val DAY_MS = 24L * 3600 * 1000

    /** Picks a category to track for an opportunity whose title or summary names one of the user's categories. */
    fun inferTrackCategory(opp: AdvisorOpportunity, categories: List<CategoryEntity>): String? {
        if (opp.pillar != AdvisorPillar.EXPENSE_OPTIMIZATION) return null
        val text = (opp.title + " " + opp.summary).lowercase(Locale.ROOT)
        return categories
            .filter { it.categoryId != "cat_reconciled" && it.name.length >= 3 }
            .firstOrNull { text.contains(it.name.lowercase(Locale.ROOT)) }
            ?.name
    }

    private fun isSpending(tx: TransactionEntity): Boolean =
        tx.transactionType != TransactionType.INTERNAL_TRANSFER && AdvisorIntelligence.flowOf(tx).second > 0

    fun evaluate(
        plan: AdvisorCapitalPlan,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): AdvisorCapitalPlan {
        val categoryIdByName = categories.associate { it.name.lowercase(Locale.ROOT) to it.categoryId }
        val dateFmt = SimpleDateFormat("d MMM", Locale.US)
        val milestones = plan.milestones.map { m ->
            if (m.trackCategory == null && m.trackKeyword == null) return@map m
            val start = m.startedAt ?: nowMillis
            var updated = m.copy(startedAt = start, lastCheckedAt = nowMillis)
            val daysSince = ((nowMillis - start) / DAY_MS).toInt()

            val categoryId = m.trackCategory?.let { categoryIdByName[it.lowercase(Locale.ROOT)] }
            if (categoryId != null) {
                val spend = transactions.filter { it.categoryId == categoryId && isSpending(it) }
                val baseline = m.baselineMonthlyEtb ?: run {
                    val before = spend.filter { it.createdTimestamp in (start - 90 * DAY_MS) until start }
                    before.sumOf { it.amount + it.fee } / 3.0
                }
                updated = updated.copy(baselineMonthlyEtb = baseline)
                if (daysSince >= 7) {
                    val since = spend.filter { it.createdTimestamp >= start }.sumOf { it.amount + it.fee }
                    val currentMonthly = since / daysSince * 30.0
                    val saving = baseline - currentMonthly
                    updated = updated.copy(
                        autoOutcomeEtb = saving,
                        autoNote = if (saving >= 0) {
                            "${m.trackCategory}: ${formatCurrency(currentMonthly)}/month now vs ${formatCurrency(baseline)} before → saving ${formatCurrency(saving)}/month"
                        } else {
                            "${m.trackCategory}: ${formatCurrency(currentMonthly)}/month now vs ${formatCurrency(baseline)} before → up ${formatCurrency(-saving)}/month"
                        }
                    )
                    if (m.status != MilestoneStatus.COMPLETED && m.targetBenefitEtb > 0 && saving >= m.targetBenefitEtb && daysSince >= 21) {
                        updated = updated.copy(status = MilestoneStatus.COMPLETED, completedAt = nowMillis, verifiedOutcomeEtb = saving)
                    } else if (m.status == MilestoneStatus.PENDING && saving > 0) {
                        updated = updated.copy(status = MilestoneStatus.IN_PROGRESS)
                    }
                } else {
                    updated = updated.copy(autoNote = "Tracking ${m.trackCategory} from ${dateFmt.format(Date(start))} (baseline ${formatCurrency(baseline)}/month); first check after 7 days")
                }
            } else if (m.trackKeyword != null) {
                val keyword = m.trackKeyword.lowercase(Locale.ROOT)
                val hits = transactions.filter { tx ->
                    tx.createdTimestamp >= start &&
                        listOfNotNull(tx.merchant, tx.recipient, tx.sender, tx.description).any { it.lowercase(Locale.ROOT).contains(keyword) }
                }
                val moved = hits.sumOf { it.amount }
                updated = updated.copy(
                    autoOutcomeEtb = moved,
                    autoNote = if (hits.isEmpty()) {
                        "No payment matching \"${m.trackKeyword}\" since ${dateFmt.format(Date(start))} yet"
                    } else {
                        "${hits.size} payment(s) matching \"${m.trackKeyword}\" since ${dateFmt.format(Date(start))}: ${formatCurrency(moved)}"
                    }
                )
                if (hits.isNotEmpty() && m.status == MilestoneStatus.PENDING) updated = updated.copy(status = MilestoneStatus.IN_PROGRESS)
            } else {
                updated = updated.copy(autoNote = "Category \"${m.trackCategory}\" no longer exists; tracking paused")
            }
            updated
        }
        val completedCount = milestones.count { it.status == MilestoneStatus.COMPLETED }
        return plan.copy(
            milestones = milestones,
            progressPercentage = if (milestones.isNotEmpty()) completedCount.toFloat() / milestones.size else 0f
        )
    }

    fun trackRecord(plan: AdvisorCapitalPlan): AdviceTrackRecord {
        val fromAdvice = plan.milestones.filter { it.sourceOpportunityId != null || it.trackCategory != null || it.trackKeyword != null }
        val measured = fromAdvice.filter { it.autoOutcomeEtb != null }
        return AdviceTrackRecord(
            accepted = fromAdvice.size,
            completed = fromAdvice.count { it.status == MilestoneStatus.COMPLETED },
            measured = measured.size,
            measuredGainEtb = measured.sumOf { (it.autoOutcomeEtb ?: 0.0).coerceAtLeast(0.0) },
            expectedGainEtb = measured.sumOf { it.targetBenefitEtb },
            lines = measured.take(6).map { "${it.title} [${it.status.name.lowercase(Locale.ROOT)}]: ${it.autoNote ?: ""}" }
        )
    }

    /** Facts block so the advisor learns which kinds of advice this user follows and what they achieved. */
    fun toPromptSection(record: AdviceTrackRecord): String = buildString {
        appendLine("ADVICE TRACK RECORD (measured from transactions; use it to calibrate future advice):")
        if (record.accepted == 0) {
            appendLine("• No tracked advice yet.")
        } else {
            appendLine("• ${record.accepted} accepted, ${record.completed} completed, ${record.measured} measured; measured gain ${formatCurrency(record.measuredGainEtb)} vs ${formatCurrency(record.expectedGainEtb)} expected")
            record.lines.forEach { appendLine("• $it") }
            appendLine("• Favour the kinds of actions this user actually completes; when advice keeps failing, propose smaller, easier steps.")
        }
    }.trimEnd()
}
