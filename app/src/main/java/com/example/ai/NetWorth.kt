package com.example.ai

import com.example.data.entity.WealthItemEntity
import com.example.data.entity.WealthKind
import com.example.ui.components.formatCurrency
import java.util.Locale

data class NetWorthLine(
    val itemId: String?,
    val name: String,
    val kind: WealthKind?,
    val valueEtb: Double,
    val detail: String
)

data class NetWorthSummary(
    // Bank, wallet and manual cash balances already tracked by the app
    val cashAndAccounts: Double = 0.0,
    val assets: List<NetWorthLine> = emptyList(),
    val liabilities: List<NetWorthLine> = emptyList()
) {
    val otherAssetsTotal: Double get() = assets.sumOf { it.valueEtb }
    val liabilitiesTotal: Double get() = liabilities.sumOf { it.valueEtb }
    val totalAssets: Double get() = cashAndAccounts + otherAssetsTotal
    val netWorth: Double get() = totalAssets - liabilitiesTotal
}

/** Values the net-worth ledger: gold at the market gram price, shares at the price entered, the rest in ETB. */
object NetWorthCalculator {

    fun valueOf(item: WealthItemEntity, market: MarketSnapshot): Double = when (item.kind) {
        WealthKind.GOLD -> item.quantity * (if (item.unitPriceEtb > 0) item.unitPriceEtb else market.value(MarketIndicators.GOLD_GRAM) ?: 0.0)
        WealthKind.SHARES -> item.quantity * item.unitPriceEtb
        else -> item.quantity
    }

    private fun detailOf(item: WealthItemEntity, market: MarketSnapshot): String {
        val parts = ArrayList<String>()
        when (item.kind) {
            WealthKind.GOLD -> {
                val price = if (item.unitPriceEtb > 0) item.unitPriceEtb else market.value(MarketIndicators.GOLD_GRAM)
                parts += String.format(Locale.US, "%.1f g", item.quantity)
                parts += price?.let { "at ${formatCurrency(it)}/g" } ?: "gold price unknown"
            }
            WealthKind.SHARES -> parts += String.format(Locale.US, "%,.0f shares at %s", item.quantity, formatCurrency(item.unitPriceEtb))
            else -> Unit
        }
        if (item.ratePct > 0) parts += String.format(Locale.US, "%.1f%% a year", item.ratePct)
        if (item.counterparty.isNotBlank()) parts += item.counterparty
        if (item.recurringAmountEtb > 0 && item.recurringEveryDays > 0) parts += "${formatCurrency(item.recurringAmountEtb)} every ${item.recurringEveryDays} days"
        if (item.dueDate.isNotBlank()) parts += (if (item.kind == WealthKind.EQUB) "payout " else "due ") + item.dueDate
        return parts.joinToString(" · ")
    }

    fun summarize(cashAndAccounts: Double, items: List<WealthItemEntity>, market: MarketSnapshot): NetWorthSummary {
        val lines = items.filter { !it.isDeleted }.map { item ->
            item to NetWorthLine(item.itemId, item.name.ifBlank { item.kind.label }, item.kind, valueOf(item, market), detailOf(item, market))
        }
        return NetWorthSummary(
            cashAndAccounts = cashAndAccounts,
            assets = lines.filter { !it.first.kind.isLiability }.map { it.second }.sortedByDescending { it.valueEtb },
            liabilities = lines.filter { it.first.kind.isLiability }.map { it.second }.sortedByDescending { it.valueEtb }
        )
    }

    /** Highest yearly rate among the loans the user owes, if any were entered with a rate. */
    fun highestDebtRatePct(items: List<WealthItemEntity>): Double? =
        items.filter { !it.isDeleted && it.kind.isLiability && it.ratePct > 0 }.maxOfOrNull { it.ratePct }

    /** Facts block for the advisor's system instruction. */
    fun toPromptSection(summary: NetWorthSummary): String = buildString {
        appendLine("NET WORTH (bank/wallet balances plus what the user entered in the net-worth ledger):")
        appendLine("• Cash, bank and wallet balances: ${formatCurrency(summary.cashAndAccounts)}")
        if (summary.assets.isEmpty() && summary.liabilities.isEmpty()) {
            appendLine("• No other assets or debts entered yet (equb, gold, shares, loans, stock, property). Ask about them when relevant.")
        }
        summary.assets.forEach { appendLine("• Asset — ${it.name}: ${formatCurrency(it.valueEtb)}${if (it.detail.isNotBlank()) " (${it.detail})" else ""}") }
        summary.liabilities.forEach { appendLine("• Debt — ${it.name}: ${formatCurrency(it.valueEtb)}${if (it.detail.isNotBlank()) " (${it.detail})" else ""}") }
        appendLine("• Total assets ${formatCurrency(summary.totalAssets)}, debts ${formatCurrency(summary.liabilitiesTotal)}, NET WORTH ${formatCurrency(summary.netWorth)}")
        appendLine("• The objective is to grow REAL net worth (after inflation), not just bank balances.")
    }.trimEnd()
}
