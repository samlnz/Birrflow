package com.example.ai

import com.example.data.entity.CategoryEntity
import com.example.data.entity.FlowDirection
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransactionType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

data class CategorySuggestion(
    val categoryId: String,
    val categoryName: String,
    // 0..1, share of the evidence pointing to this category
    val confidence: Double,
    val reason: String
)

/**
 * On-device category suggestions for debits, learned from how the user filed earlier debits
 * (same payee, words in the SMS, typical amount) and from the user's own category descriptions.
 * It is rebuilt from the history every time, so each manual choice immediately improves it.
 */
class CategoryLearner private constructor(
    private val categories: Map<String, CategoryEntity>,
    private val byCounterparty: Map<String, Map<String, Int>>,
    private val byToken: Map<String, Map<String, Int>>,
    private val logAmountStats: Map<String, Pair<Double, Double>>,
    private val prior: Map<String, Int>,
    private val descriptionTokens: Map<String, Set<String>>,
    val trainedOn: Int
) {

    fun suggest(tx: TransactionEntity, limit: Int = 3): List<CategorySuggestion> {
        if (categories.isEmpty()) return emptyList()
        val scores = HashMap<String, Double>()
        val reasons = HashMap<String, String>()
        fun add(categoryId: String, points: Double, reason: String) {
            if (categoryId !in categories || points <= 0) return
            scores[categoryId] = (scores[categoryId] ?: 0.0) + points
            if (reason.isNotBlank() && (reasons[categoryId] == null || points >= 1.0)) reasons[categoryId] = reason
        }

        val payee = counterpartyKey(tx)
        payee?.let { key ->
            val counts = byCounterparty[key].orEmpty()
            val total = counts.values.sum()
            counts.forEach { (cat, n) ->
                add(cat, 4.0 * n / total * minOf(1.0, total / 2.0), "you filed $n of $total debits to this payee here")
            }
        }

        val words = tokens(listOfNotNull(tx.merchant, tx.recipient, tx.description).joinToString(" "))
        words.forEach { word ->
            val counts = byToken[word].orEmpty()
            val total = counts.values.sum()
            if (total > 0) counts.forEach { (cat, n) -> add(cat, 0.6 * n / total, "") }
            descriptionTokens.forEach { (cat, descWords) ->
                if (word in descWords) add(cat, 0.9, "matches your description of ${categories[cat]?.name}")
            }
        }

        if (tx.amount > 0) {
            val logAmount = ln(tx.amount)
            logAmountStats.forEach { (cat, stats) ->
                val z = abs(logAmount - stats.first) / maxOf(stats.second, 0.35)
                add(cat, 0.7 * exp(-z * z / 2), "")
            }
        }

        if (trainedOn > 0) prior.forEach { (cat, n) -> add(cat, 0.3 * n / trainedOn, "") }

        val total = scores.values.sum()
        if (total <= 0) return emptyList()
        return scores.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { (cat, score) ->
                CategorySuggestion(
                    categoryId = cat,
                    categoryName = categories[cat]?.name ?: cat,
                    confidence = (score / (total + 0.5)).coerceIn(0.0, 0.99),
                    reason = reasons[cat] ?: "similar to debits you filed there"
                )
            }
    }

    /**
     * The category when the evidence is unambiguous: the same payee filed at least 3 times and always
     * under the same (still existing) category. Used for opt-in auto-filing.
     */
    fun certainCategory(tx: TransactionEntity): String? {
        val key = counterpartyKey(tx) ?: return null
        val counts = byCounterparty[key] ?: return null
        if (counts.size != 1) return null
        val (cat, n) = counts.entries.first()
        return cat.takeIf { n >= 3 && it in categories }
    }

    companion object {
        private val STOP_WORDS = setOf(
            "the", "and", "for", "from", "to", "of", "on", "your", "you", "has", "have", "been", "with", "etb", "birr",
            "account", "transaction", "number", "balance", "current", "transferred", "paid", "debited", "credited",
            "amount", "via", "at", "is", "are", "was", "a", "an", "in", "by", "dear", "customer", "thank", "using",
            "money", "service", "charge", "vat", "total", "ref", "reference", "please", "click", "link", "receipt"
        )

        fun tokens(text: String): Set<String> =
            text.lowercase(Locale.ROOT)
                .split(Regex("[^\\p{L}]+"))
                .filter { it.length >= 3 && it !in STOP_WORDS }
                .toSet()

        fun counterpartyKey(tx: TransactionEntity): String? =
            (tx.merchant ?: tx.recipient)?.lowercase(Locale.ROOT)
                ?.replace(Regex("[^\\p{L}\\p{N} ]"), " ")
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.takeIf { it.length >= 2 }

        fun isDebit(tx: TransactionEntity): Boolean =
            tx.direction == FlowDirection.DEBIT ||
                tx.transactionType == TransactionType.EXPENSE ||
                tx.transactionType == TransactionType.WITHDRAWAL ||
                tx.transactionType == TransactionType.FEE ||
                tx.transactionType == TransactionType.TRANSFER

        /** Learns from debits already filed under one of the user's manual categories. */
        fun train(history: List<TransactionEntity>, categories: List<CategoryEntity>): CategoryLearner {
            val manual = categories.filter { it.categoryId != "cat_reconciled" }.associateBy { it.categoryId }
            val labeled = history.filter { tx -> isDebit(tx) && !tx.isReconciliation && tx.categoryId != null && tx.categoryId in manual }

            val byCounterparty = HashMap<String, HashMap<String, Int>>()
            val byToken = HashMap<String, HashMap<String, Int>>()
            val logAmounts = HashMap<String, MutableList<Double>>()
            val prior = HashMap<String, Int>()
            for (tx in labeled) {
                val cat = tx.categoryId ?: continue
                counterpartyKey(tx)?.let { key ->
                    val m = byCounterparty.getOrPut(key) { HashMap() }
                    m[cat] = (m[cat] ?: 0) + 1
                }
                tokens(listOfNotNull(tx.merchant, tx.recipient, tx.description).joinToString(" ")).forEach { word ->
                    val m = byToken.getOrPut(word) { HashMap() }
                    m[cat] = (m[cat] ?: 0) + 1
                }
                if (tx.amount > 0) logAmounts.getOrPut(cat) { ArrayList() }.add(ln(tx.amount))
                prior[cat] = (prior[cat] ?: 0) + 1
            }
            val stats = logAmounts.filterValues { it.size >= 2 }.mapValues { (_, values) ->
                val mean = values.average()
                mean to sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
            }
            val descTokens = manual.mapValues { (_, c) -> tokens(c.name + " " + c.description) }
            return CategoryLearner(manual, byCounterparty, byToken, stats, prior, descTokens, labeled.size)
        }
    }
}
