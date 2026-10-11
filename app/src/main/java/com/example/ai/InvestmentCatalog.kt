package com.example.ai

import com.example.ui.components.formatCurrency
import java.util.Locale

/** How quickly money can be taken back out. */
enum class Liquidity(val label: String, val lockDays: Int) {
    INSTANT("Instant", 0),
    DAYS("Within days", 7),
    MONTHS("Locked for months", 180),
    YEAR("Locked a year", 365)
}

/** A way to hold or grow money that is open to ordinary people in Ethiopia. */
data class InvestmentOption(
    val id: String,
    val name: String,
    val description: String,
    // Market indicator that gives the yearly nominal return (first one found is used)
    val rateKeys: List<String> = emptyList(),
    // Known fixed nominal return when it is not market-driven (cash, equb)
    val fixedNominalPct: Double? = null,
    val taxedAsInterest: Boolean,
    val yearlyCostPct: Double = 0.0,
    val liquidity: Liquidity,
    // 0 none, 1 low, 2 medium, 3 high
    val risk: Int,
    val minAmountEtb: Double = 0.0,
    val interestBased: Boolean,
    val howTo: String,
    val caution: String = ""
)

/** What the user allows (filled from the About Me profile). */
data class RankingPreferences(
    val interestFree: Boolean = false,
    val maxRisk: Int = 3,
    val maxLockDays: Int = Int.MAX_VALUE,
    val hasDebt: Boolean = false,
    // Rate of the user's most expensive loan, when entered in the net-worth ledger
    val debtRatePct: Double? = null,
    // Own business: gross margin per sale and how many times stock sells through in a year
    val businessMarginPct: Double? = null,
    val businessTurnsPerYear: Double? = null
)

data class RankedOption(
    val option: InvestmentOption,
    val nominalPct: Double?,
    val realPct: Double?,
    // Gain in today's money over one year for the amount considered
    val realYearlyEtb: Double?,
    val eligible: Boolean,
    val reasons: List<String>,
    val rateNote: String
)

/**
 * Ranks Ethiopian options by REAL return: nominal yield, minus tax on interest and yearly costs,
 * deflated by inflation. With inflation above deposit and T-bill rates, holding cash or "safe" yield
 * can still lose purchasing power; this makes that visible.
 */
object InvestmentCatalog {

    val OPTIONS = listOf(
        InvestmentOption(
            id = "cash",
            name = "Cash / wallet balance",
            description = "Money left in a current account, wallet or at home earns nothing.",
            fixedNominalPct = 0.0,
            taxedAsInterest = false,
            liquidity = Liquidity.INSTANT,
            risk = 0,
            interestBased = false,
            howTo = "Keep only what you need for the next few weeks plus your emergency reserve."
        ),
        InvestmentOption(
            id = "savings_account",
            name = "Bank savings account",
            description = "Regular savings deposit at a commercial bank.",
            rateKeys = listOf(MarketIndicators.SAVINGS_RATE),
            taxedAsInterest = true,
            liquidity = Liquidity.INSTANT,
            risk = 0,
            interestBased = true,
            howTo = "Open a savings account (or move money from a current account) at your bank."
        ),
        InvestmentOption(
            id = "wallet_savings",
            name = "Wallet savings (Telebirr Sanduq / M-Pesa)",
            description = "Savings feature inside a mobile wallet; rate assumed similar to bank savings unless you enter it.",
            rateKeys = listOf(MarketIndicators.SAVINGS_RATE),
            taxedAsInterest = true,
            liquidity = Liquidity.INSTANT,
            risk = 0,
            interestBased = true,
            howTo = "Open the savings section of your wallet app and check its current rate before moving money.",
            caution = "Check the exact wallet rate; it can differ from banks."
        ),
        InvestmentOption(
            id = "term_deposit",
            name = "1-year term deposit",
            description = "Fixed-term deposit at a commercial bank.",
            rateKeys = listOf(MarketIndicators.TERM_DEPOSIT_1Y),
            taxedAsInterest = true,
            liquidity = Liquidity.YEAR,
            risk = 0,
            minAmountEtb = 1_000.0,
            interestBased = true,
            howTo = "Ask two or three banks for their 1-year rate and open it where the rate is highest.",
            caution = "Breaking it early usually loses the interest."
        ),
        InvestmentOption(
            id = "mudarabah",
            name = "Interest-free mudarabah investment account",
            description = "Profit-sharing account at an interest-free bank or window; return varies, term-deposit rate used as a rough guide.",
            rateKeys = listOf(MarketIndicators.TERM_DEPOSIT_1Y),
            taxedAsInterest = false,
            liquidity = Liquidity.MONTHS,
            risk = 1,
            minAmountEtb = 1_000.0,
            interestBased = false,
            howTo = "Ask an interest-free bank (or interest-free window) for its latest profit-share ratio and past payouts.",
            caution = "Profit share is not guaranteed; the rate shown is only a proxy."
        ),
        InvestmentOption(
            id = "tbill",
            name = "Treasury bills (via your bank)",
            description = "Government T-bills bought in the Ministry of Finance auctions.",
            rateKeys = listOf(MarketIndicators.TBILL_364, MarketIndicators.TBILL_AVG),
            taxedAsInterest = true,
            liquidity = Liquidity.MONTHS,
            risk = 0,
            interestBased = true,
            howTo = "Ask your bank's treasury desk for the next auction date, available tenors and minimum bid.",
            caution = "Yields move with every auction."
        ),
        InvestmentOption(
            id = "pay_debt",
            name = "Pay down your costliest loan",
            description = "Every birr repaid saves the loan's interest — a guaranteed, untaxed return.",
            rateKeys = listOf(MarketIndicators.LENDING_RATE),
            taxedAsInterest = false,
            liquidity = Liquidity.YEAR,
            risk = 0,
            interestBased = false,
            howTo = "Ask the lender about early repayment and any penalty, then repay the highest-rate loan first."
        ),
        InvestmentOption(
            id = "own_business",
            name = "Working capital in your own trade",
            description = "Extra stock or inputs for a business you already run, estimated from your margin and how fast stock sells.",
            taxedAsInterest = false,
            liquidity = Liquidity.MONTHS,
            risk = 2,
            interestBased = false,
            howTo = "Buy only what you can sell within a cycle; start with your fastest-moving products.",
            caution = "Estimate from your own numbers in About Me; real results vary."
        ),
        InvestmentOption(
            id = "gold",
            name = "Gold",
            description = "Traditional store of value; its birr price tends to follow inflation and the exchange rate over time but pays no income.",
            taxedAsInterest = false,
            yearlyCostPct = 0.0,
            liquidity = Liquidity.DAYS,
            risk = 2,
            interestBased = false,
            howTo = "Buy from a licensed dealer with a receipt; compare the buy and sell price first.",
            caution = "Price can fall; dealer margins reduce the return."
        ),
        InvestmentOption(
            id = "esx_shares",
            name = "Shares on the Ethiopian Securities Exchange",
            description = "Listed shares (banks such as Wegagen, Gadaa, Awash, Abay, and Ethio Telecom) return dividends plus price changes.",
            taxedAsInterest = false,
            liquidity = Liquidity.DAYS,
            risk = 3,
            interestBased = false,
            howTo = "Open an account with a licensed ESX trading member (investment bank/broker) and read each company's latest annual report.",
            caution = "Young, thinly traded market; prices can fall and selling can take time."
        ),
        InvestmentOption(
            id = "equb",
            name = "Equb (rotating savings)",
            description = "Pays no return, but forces saving; an early payout works like an interest-free loan.",
            fixedNominalPct = 0.0,
            taxedAsInterest = false,
            liquidity = Liquidity.MONTHS,
            risk = 1,
            interestBased = false,
            howTo = "Join only groups you trust; prefer a late payout slot if you are saving, an early one if you need capital."
        )
    )

    fun realReturnPct(nominalPct: Double, taxPct: Double, yearlyCostPct: Double, inflationPct: Double): Double {
        val netNominal = nominalPct * (1 - taxPct / 100.0) - yearlyCostPct
        return ((1 + netNominal / 100.0) / (1 + inflationPct / 100.0) - 1) * 100.0
    }

    fun rank(market: MarketSnapshot, amountEtb: Double, prefs: RankingPreferences = RankingPreferences()): List<RankedOption> {
        val inflation = market.value(MarketIndicators.INFLATION)
        val interestTax = market.value(MarketIndicators.INTEREST_TAX)
        return OPTIONS.map { option ->
            val reasons = ArrayList<String>()
            var eligible = true

            var rateNote = ""
            val nominal: Double? = when {
                option.fixedNominalPct != null -> option.fixedNominalPct
                option.id == "own_business" -> {
                    val margin = prefs.businessMarginPct
                    val turns = prefs.businessTurnsPerYear
                    if (margin != null && turns != null) {
                        rateNote = "your margin ${fmt(margin)}% × ${fmt(turns)} turns a year"
                        (margin * turns).coerceAtMost(300.0)
                    } else {
                        rateNote = "add your margin in About Me"
                        null
                    }
                }
                option.id == "gold" -> inflation?.also { rateNote = "assumed to roughly track inflation" }
                option.id == "pay_debt" && prefs.debtRatePct != null -> prefs.debtRatePct.also { rateNote = "your costliest loan's rate" }
                else -> option.rateKeys.firstNotNullOfOrNull { key -> market.get(key) }?.let { rate ->
                    rateNote = "${rate.label} ${rate.display}, ${rate.asOf}"
                    rate.value
                }
            }
            if (nominal == null && rateNote.isBlank()) rateNote = "return unknown (price-based or no market figure yet)"

            val tax = if (option.taxedAsInterest) interestTax ?: 0.0 else 0.0
            if (option.taxedAsInterest && interestTax == null) reasons += "interest tax not included (unknown)"
            val real = if (nominal != null && inflation != null) realReturnPct(nominal, tax, option.yearlyCostPct, inflation) else null
            if (inflation == null) reasons += "inflation unknown"

            if (prefs.interestFree && option.interestBased) { eligible = false; reasons += "interest-based" }
            if (option.risk > prefs.maxRisk) { eligible = false; reasons += "above your risk level" }
            if (option.liquidity.lockDays > prefs.maxLockDays) { eligible = false; reasons += "locks money longer than you allow" }
            if (amountEtb in 0.0..(option.minAmountEtb - 0.01)) { eligible = false; reasons += "needs at least ${formatCurrency(option.minAmountEtb)}" }
            if (option.id == "pay_debt" && !prefs.hasDebt) { eligible = false; reasons += "no loans recorded" }
            if (option.id == "own_business" && nominal == null) { eligible = false; reasons += "no business numbers yet" }

            RankedOption(
                option = option,
                nominalPct = nominal,
                realPct = real,
                realYearlyEtb = real?.let { amountEtb.coerceAtLeast(0.0) * it / 100.0 },
                eligible = eligible,
                reasons = reasons,
                rateNote = rateNote
            )
        }.sortedWith(
            compareByDescending<RankedOption> { it.eligible }
                .thenByDescending { it.realPct ?: Double.NEGATIVE_INFINITY }
                .thenBy { it.option.risk }
        )
    }

    /** Facts block for the advisor's system instruction. */
    fun toPromptSection(ranked: List<RankedOption>, amountEtb: Double): String = buildString {
        appendLine("LOCAL OPTIONS RANKED BY REAL RETURN (for ${formatCurrency(amountEtb)} of deployable cash; after inflation, interest tax and costs):")
        ranked.forEach { r ->
            val real = r.realPct?.let { "${fmt(it)}% real" } ?: "real return unknown"
            val nominal = r.nominalPct?.let { "${fmt(it)}% nominal" } ?: "nominal unknown"
            val status = if (r.eligible) "" else " [NOT SUITABLE: ${r.reasons.filter { it != "inflation unknown" && !it.startsWith("interest tax") }.joinToString(", ")}]"
            appendLine("• ${r.option.name}: $nominal → $real; ${r.option.liquidity.label.lowercase(Locale.ROOT)}, risk ${r.option.risk}/3$status")
        }
        appendLine("• Never recommend an option marked NOT SUITABLE; explain the trade-off between real return, liquidity and risk.")
    }.trimEnd()

    private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)
}
