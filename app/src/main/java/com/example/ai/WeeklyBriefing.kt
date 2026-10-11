package com.example.ai

import com.example.data.repository.FinancialRepository
import com.example.ui.components.formatCurrency
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class WeeklyBriefing(
    val createdAt: Long,
    val title: String,
    val lines: List<String>,
    val netWorth: Double,
    val netWorthChange: Double?
)

data class NetWorthPoint(val timestamp: Long, val netWorth: Double)

/** Builds the weekly money briefing from facts computed on the phone (no API call needed). */
object WeeklyBriefingBuilder {

    fun build(
        nowMillis: Long,
        netWorth: Double,
        previous: NetWorthPoint?,
        weekInflow: Double,
        weekOutflow: Double,
        deep: AdvisorDeepContext,
        ranked: List<RankedOption>,
        market: MarketSnapshot
    ): WeeklyBriefing {
        val lines = ArrayList<String>()
        val change = previous?.let { netWorth - it.netWorth }
        val inflation = market.value(MarketIndicators.INFLATION)

        lines += buildString {
            append("Net worth ${formatCurrency(netWorth)}")
            if (change != null && previous != null) {
                append(if (change >= 0) " (▲ ${formatCurrency(change)}" else " (▼ ${formatCurrency(-change)}")
                append(" since last briefing")
                if (inflation != null) {
                    val days = ((nowMillis - previous.timestamp) / (24.0 * 3600 * 1000)).coerceAtLeast(1.0)
                    val inflationDrag = previous.netWorth.coerceAtLeast(0.0) * inflation / 100.0 * days / 365.0
                    append("; ${formatCurrency(change - inflationDrag)} after inflation")
                }
                append(")")
            }
        }
        lines += "Last 7 days: in ${formatCurrency(weekInflow)}, out ${formatCurrency(weekOutflow)}, net ${formatCurrency(weekInflow - weekOutflow)}"

        deep.alerts.filter { it.severity == AlertSeverity.CRITICAL || it.severity == AlertSeverity.WARNING }
            .take(2)
            .forEach { lines += "⚠️ ${it.title}" }

        val best = ranked.firstOrNull { it.eligible && it.realPct != null && it.option.id != "cash" }
        if (best != null) {
            val real = best.realPct ?: 0.0
            lines += if (real >= 0) {
                String.format(Locale.US, "Best place for idle cash: %s (%+.1f%% after inflation)", best.option.name, real)
            } else {
                String.format(Locale.US, "No suitable option beats inflation right now; best is %s (%+.1f%%). Prefer paying debt or productive stock.", best.option.name, real)
            }
        }

        deep.recurringPayments.firstOrNull { it.nextExpectedMillis in nowMillis..(nowMillis + 7L * 24 * 3600 * 1000) }?.let {
            lines += "Coming up: ~${formatCurrency(it.averageAmount)} to ${it.counterparty}"
        }
        if (deep.trackRecord.measured > 0) {
            lines += "Your plan: ${deep.trackRecord.completed}/${deep.trackRecord.accepted} done, measured gain ${formatCurrency(deep.trackRecord.measuredGainEtb)}"
        }
        if (deep.uncategorizedCount > 0) {
            lines += "${deep.uncategorizedCount} debits still uncategorized (${formatCurrency(deep.uncategorizedAmount)})"
        }

        val title = when {
            change == null -> "Your weekly money briefing"
            change >= 0 -> "Net worth up ${formatCurrency(change)} this week"
            else -> "Net worth down ${formatCurrency(-change)} this week"
        }
        return WeeklyBriefing(nowMillis, title, lines, netWorth, change)
    }
}

/** Stores the latest briefing and the net-worth history in the app's key/value table. */
object BriefingStore {
    private const val KEY_LAST = "WEEKLY_BRIEFING_LAST"
    private const val KEY_HISTORY = "NET_WORTH_HISTORY"

    suspend fun lastBriefing(repository: FinancialRepository): WeeklyBriefing? {
        val raw = repository.getState(KEY_LAST) ?: return null
        return try {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("lines") ?: JSONArray()
            WeeklyBriefing(
                createdAt = o.optLong("createdAt"),
                title = o.optString("title"),
                lines = (0 until arr.length()).map { arr.optString(it) },
                netWorth = o.optDouble("netWorth", 0.0),
                netWorthChange = if (o.has("netWorthChange")) o.optDouble("netWorthChange") else null
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveBriefing(repository: FinancialRepository, briefing: WeeklyBriefing) {
        val o = JSONObject()
            .put("createdAt", briefing.createdAt)
            .put("title", briefing.title)
            .put("lines", JSONArray(briefing.lines))
            .put("netWorth", briefing.netWorth)
        briefing.netWorthChange?.let { o.put("netWorthChange", it) }
        repository.putState(KEY_LAST, o.toString())
    }

    suspend fun history(repository: FinancialRepository): List<NetWorthPoint> {
        val raw = repository.getState(KEY_HISTORY) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                NetWorthPoint(o.optLong("t"), o.optDouble("v", 0.0))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun appendHistory(repository: FinancialRepository, point: NetWorthPoint) {
        val all = (history(repository) + point).sortedBy { it.timestamp }.takeLast(104)
        val arr = JSONArray()
        all.forEach { arr.put(JSONObject().put("t", it.timestamp).put("v", it.netWorth)) }
        repository.putState(KEY_HISTORY, arr.toString())
    }
}
