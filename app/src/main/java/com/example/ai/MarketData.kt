package com.example.ai

import com.example.data.repository.FinancialRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Where a market figure came from. Web refreshes never overwrite a value the user just entered. */
enum class MarketOrigin { SEED, WEB, USER }

/** One Ethiopian market figure with the date it applies to and its source. */
data class MarketRate(
    val key: String,
    val label: String,
    val value: Double,
    val unit: String,
    // Date the figure applies to, as published ("08/07/2026", "June 2026")
    val asOf: String,
    val source: String,
    val sourceUrl: String = "",
    val origin: MarketOrigin = MarketOrigin.SEED,
    // When BirrFlow last confirmed the figure (seed values carry their publication date)
    val updatedAt: Long = System.currentTimeMillis(),
    val note: String = ""
) {
    fun ageDays(nowMillis: Long = System.currentTimeMillis()): Int =
        ((nowMillis - updatedAt) / (24L * 3600 * 1000)).toInt().coerceAtLeast(0)

    val display: String
        get() = when (unit) {
            "%" -> String.format(Locale.US, "%.2f%%", value)
            else -> String.format(Locale.US, "%,.2f %s", value, unit)
        }
}

data class MarketSnapshot(
    val rates: List<MarketRate> = emptyList(),
    val lastWebRefreshAt: Long = 0L,
    val lastRefreshError: String? = null
) {
    fun get(key: String): MarketRate? = rates.firstOrNull { it.key == key }
    fun value(key: String): Double? = get(key)?.value
    fun missing(): List<MarketIndicators.Indicator> = MarketIndicators.ALL.filter { ind -> rates.none { it.key == ind.key } }
}

object MarketIndicators {

    data class Indicator(
        val key: String,
        val label: String,
        val unit: String,
        val searchHint: String,
        val min: Double,
        val max: Double
    )

    const val POLICY_RATE = "policy_rate"
    const val INFLATION = "inflation_yoy"
    const val FOOD_INFLATION = "food_inflation_yoy"
    const val TBILL_AVG = "tbill_avg_yield"
    const val TBILL_364 = "tbill_364_yield"
    const val SAVINGS_RATE = "savings_rate"
    const val TERM_DEPOSIT_1Y = "term_deposit_1y"
    const val INTEREST_TAX = "interest_tax"
    const val LENDING_RATE = "lending_rate"
    const val USD_OFFICIAL = "usd_official"
    const val GOLD_GRAM = "gold_24k_gram"
    const val FUEL_BENZINE = "fuel_benzine"

    val ALL = listOf(
        Indicator(POLICY_RATE, "NBE policy rate", "%", "National Bank of Ethiopia monetary policy rate (latest MPC decision)", 0.0, 60.0),
        Indicator(INFLATION, "Headline inflation (year on year)", "%", "Ethiopian Statistical Service consumer price index, latest monthly year-on-year inflation", -5.0, 80.0),
        Indicator(FOOD_INFLATION, "Food inflation (year on year)", "%", "Ethiopian Statistical Service food inflation, latest month", -5.0, 80.0),
        Indicator(TBILL_AVG, "T-bill average yield (latest auction)", "%", "Ministry of Finance Ethiopia treasury bill auction weighted average yield, latest auction", 0.0, 60.0),
        Indicator(TBILL_364, "364-day T-bill yield", "%", "Ethiopia 364-day treasury bill yield, latest auction", 0.0, 60.0),
        Indicator(SAVINGS_RATE, "Typical savings account rate", "%", "Ethiopian commercial banks regular savings deposit interest rate (typical or minimum)", 0.0, 40.0),
        Indicator(TERM_DEPOSIT_1Y, "Typical 1-year term deposit rate", "%", "Ethiopian commercial bank one-year fixed time deposit interest rate (typical)", 0.0, 40.0),
        Indicator(INTEREST_TAX, "Tax on deposit interest", "%", "Ethiopian withholding income tax rate on bank deposit interest", 0.0, 50.0),
        Indicator(LENDING_RATE, "Typical bank lending rate", "%", "Ethiopian commercial bank average lending interest rate", 0.0, 60.0),
        Indicator(USD_OFFICIAL, "Official USD rate (NBE / banks)", "ETB per USD", "Official Ethiopian birr per US dollar exchange rate (NBE or Commercial Bank of Ethiopia buying/selling average)", 20.0, 2000.0),
        Indicator(GOLD_GRAM, "Gold price (24k)", "ETB per gram", "Gold price per gram in Ethiopian birr (Addis Ababa, 24 karat)", 500.0, 500_000.0),
        Indicator(FUEL_BENZINE, "Benzine price (Addis Ababa)", "ETB per litre", "Benzine (gasoline) retail price per litre in Addis Ababa", 10.0, 2000.0)
    )

    fun find(key: String): Indicator? = ALL.firstOrNull { it.key == key }

    /** Verified starting values (July 2026). Refreshing from the web or editing replaces them. */
    val SEED = listOf(
        MarketRate(POLICY_RATE, "NBE policy rate", 16.0, "%", "13/07/2026", "National Bank of Ethiopia (MPC)",
            "https://tradingeconomics.com/ethiopia/interest-rate/news/566412", MarketOrigin.SEED, 1_783_933_200_000L),
        MarketRate(INFLATION, "Headline inflation (year on year)", 13.9, "%", "June 2026", "Ethiopian Statistical Service",
            "https://businessday.ng/?p=1164298", MarketOrigin.SEED, 1_783_674_000_000L),
        MarketRate(FOOD_INFLATION, "Food inflation (year on year)", 15.1, "%", "June 2026", "Ethiopian Statistical Service",
            "https://businessday.ng/?p=1164298", MarketOrigin.SEED, 1_783_674_000_000L),
        MarketRate(TBILL_AVG, "T-bill average yield (latest auction)", 9.2, "%", "08/07/2026", "Ministry of Finance T-bill auction",
            "https://capitalethiopia.com/2026/07/26/t-bill-auctions-to-raise-197b-birr/", MarketOrigin.SEED, 1_783_501_200_000L)
    )
}

/**
 * Stores Ethiopian market figures (rates, inflation, FX, gold, fuel) with dates and sources, and
 * refreshes them from the web through Gemini's Google Search grounding. The advisor may only quote
 * figures that are in here (or a web source it cites).
 */
object MarketDataService {

    private const val KEY_MARKET = "AI_MARKET_DATA"
    const val STALE_DAYS = 45

    suspend fun load(repository: FinancialRepository): MarketSnapshot {
        val raw = repository.getState(KEY_MARKET)
        val stored = if (raw.isNullOrBlank()) MarketSnapshot() else parse(raw)
        // Seeds fill indicators the store has never had
        val missingSeeds = MarketIndicators.SEED.filter { seed -> stored.rates.none { it.key == seed.key } }
        return stored.copy(rates = stored.rates + missingSeeds)
    }

    suspend fun save(repository: FinancialRepository, snapshot: MarketSnapshot) {
        val arr = JSONArray()
        snapshot.rates.forEach { r ->
            arr.put(JSONObject().apply {
                put("key", r.key)
                put("label", r.label)
                put("value", r.value)
                put("unit", r.unit)
                put("asOf", r.asOf)
                put("source", r.source)
                put("sourceUrl", r.sourceUrl)
                put("origin", r.origin.name)
                put("updatedAt", r.updatedAt)
                put("note", r.note)
            })
        }
        val json = JSONObject()
            .put("rates", arr)
            .put("lastWebRefreshAt", snapshot.lastWebRefreshAt)
        snapshot.lastRefreshError?.let { json.put("lastRefreshError", it) }
        repository.putState(KEY_MARKET, json.toString())
    }

    private fun parse(raw: String): MarketSnapshot = try {
        val json = JSONObject(raw)
        val arr = json.optJSONArray("rates") ?: JSONArray()
        val rates = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val value = o.optDouble("value", Double.NaN)
            if (value.isNaN()) return@mapNotNull null
            MarketRate(
                key = o.optString("key"),
                label = o.optString("label"),
                value = value,
                unit = o.optString("unit", "%"),
                asOf = o.optString("asOf", ""),
                source = o.optString("source", ""),
                sourceUrl = o.optString("sourceUrl", ""),
                origin = try { MarketOrigin.valueOf(o.optString("origin", "SEED")) } catch (_: Exception) { MarketOrigin.SEED },
                updatedAt = o.optLong("updatedAt", 0L),
                note = o.optString("note", "")
            )
        }.filter { it.key.isNotBlank() }
        MarketSnapshot(
            rates = rates,
            lastWebRefreshAt = json.optLong("lastWebRefreshAt", 0L),
            lastRefreshError = json.optString("lastRefreshError").takeIf { it.isNotBlank() }
        )
    } catch (_: Exception) {
        MarketSnapshot()
    }

    /** A value the user typed in (e.g. the rate their own bank offered them). */
    fun withUserValue(snapshot: MarketSnapshot, key: String, value: Double, asOf: String, note: String): MarketSnapshot {
        val indicator = MarketIndicators.find(key) ?: return snapshot
        val rate = MarketRate(
            key = key,
            label = indicator.label,
            value = value,
            unit = indicator.unit,
            asOf = asOf.ifBlank { "entered by you" },
            source = "Entered by you",
            origin = MarketOrigin.USER,
            updatedAt = System.currentTimeMillis(),
            note = note
        )
        return snapshot.copy(rates = snapshot.rates.filter { it.key != key } + rate)
    }

    fun isStale(snapshot: MarketSnapshot, nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis - snapshot.lastWebRefreshAt > 24L * 3600 * 1000

    /**
     * Asks Gemini (with Google Search grounding) for the latest value of every indicator and merges
     * the plausible ones. A value the user entered in the last 14 days is kept.
     */
    suspend fun refreshFromWeb(apiKey: String, model: String, current: MarketSnapshot): MarketSnapshot {
        val list = MarketIndicators.ALL.joinToString("\n") { "- ${it.key}: ${it.searchHint} (unit: ${it.unit})" }
        val prompt = """
            Use Google Search to find the most recent published value of each Ethiopian indicator below.
            $list

            Rules:
            - Only use figures you found in search results. Never estimate. If you cannot find one, use null.
            - Prefer official sources (nbe.gov.et, mofed.gov.et, ess.gov.et, bank websites) and reputable Ethiopian business press.
            - "as_of" is the date or month the figure applies to, as published.
            Reply with ONLY this JSON object and nothing else:
            {"indicators":[{"key":"policy_rate","value":16.0,"as_of":"13/07/2026","source":"National Bank of Ethiopia","url":"https://..."}]}
        """.trimIndent()
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            put("generationConfig", JSONObject().put("temperature", 0.1).put("maxOutputTokens", 4096))
        }
        val response = AdvisorAgent.post(apiKey, model, body)
        val parts = response.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val text = buildString {
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                if (!p.optBoolean("thought", false)) append(p.optString("text", ""))
            }
        }
        val found = parseWebIndicators(text, System.currentTimeMillis())
        if (found.isEmpty()) throw Exception("The web search returned no usable market figures.")

        val now = System.currentTimeMillis()
        val keepUser = current.rates.filter { it.origin == MarketOrigin.USER && now - it.updatedAt < 14L * 24 * 3600 * 1000 }.map { it.key }.toSet()
        val merged = current.rates.filter { it.key in keepUser || found.none { f -> f.key == it.key } } +
            found.filter { it.key !in keepUser }
        return MarketSnapshot(rates = merged, lastWebRefreshAt = now, lastRefreshError = null)
    }

    /** Pulls the JSON object out of the model text and keeps values inside each indicator's plausible range. */
    fun parseWebIndicators(text: String, nowMillis: Long): List<MarketRate> {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyList()
        val json = try { JSONObject(text.substring(start, end + 1)) } catch (_: Exception) { return emptyList() }
        val arr = json.optJSONArray("indicators") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val indicator = MarketIndicators.find(o.optString("key")) ?: return@mapNotNull null
            if (o.isNull("value")) return@mapNotNull null
            val value = o.optDouble("value", Double.NaN)
            if (value.isNaN() || value < indicator.min || value > indicator.max) return@mapNotNull null
            MarketRate(
                key = indicator.key,
                label = indicator.label,
                value = value,
                unit = indicator.unit,
                asOf = o.optString("as_of", "").ifBlank { "latest found" },
                source = o.optString("source", "").ifBlank { "Web search" },
                sourceUrl = o.optString("url", ""),
                origin = MarketOrigin.WEB,
                updatedAt = nowMillis
            )
        }.distinctBy { it.key }
    }

    /** Facts block for the advisor's system instruction. */
    fun toPromptSection(snapshot: MarketSnapshot, nowMillis: Long = System.currentTimeMillis()): String = buildString {
        appendLine("MARKET DATA (Ethiopia; the ONLY rates, yields, inflation, FX and prices you may quote, always with their date):")
        if (snapshot.rates.isEmpty()) appendLine("• (none stored yet)")
        snapshot.rates.sortedBy { r -> MarketIndicators.ALL.indexOfFirst { it.key == r.key } }.forEach { r ->
            val stale = if (r.ageDays(nowMillis) > STALE_DAYS) " [STALE: ${r.ageDays(nowMillis)} days old]" else ""
            appendLine("• ${r.label}: ${r.display} (as of ${r.asOf}; ${r.source})$stale")
        }
        val missing = snapshot.missing()
        if (missing.isNotEmpty()) appendLine("• Unknown right now: " + missing.joinToString(", ") { it.label })
        snapshot.value(MarketIndicators.INFLATION)?.let {
            appendLine("• Real return rule: an investment only grows real wealth if it beats $it% inflation after tax and fees.")
        }
    }.trimEnd()
}
