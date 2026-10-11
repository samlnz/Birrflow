package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AdvisorDeepContext
import com.example.ai.AlertSeverity
import com.example.ai.MarketDataService
import com.example.ai.MarketIndicators
import com.example.ai.MarketOrigin
import com.example.ai.MarketRate
import com.example.ai.MarketSnapshot
import com.example.ai.RankedOption
import com.example.ai.WeeklyBriefing
import com.example.ai.SmartAlert
import com.example.ui.components.formatCurrency
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BlueFintech
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.PurpleFintech
import com.example.ui.theme.RoseExpense
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun severityColor(severity: AlertSeverity): Color = when (severity) {
    AlertSeverity.CRITICAL -> RoseExpense
    AlertSeverity.WARNING -> AmberAccent
    AlertSeverity.INFO -> BlueFintech
    AlertSeverity.POSITIVE -> EmeraldPrimary
}

private fun severityLabel(severity: AlertSeverity): String = when (severity) {
    AlertSeverity.CRITICAL -> "Critical"
    AlertSeverity.WARNING -> "Watch"
    AlertSeverity.INFO -> "Info"
    AlertSeverity.POSITIVE -> "Good"
}

/** On-device findings (no API call); tapping one asks the advisor agent about it. */
@Composable
fun SmartAlertsCard(
    alerts: List<SmartAlert>,
    onAsk: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("advisor_smart_alerts"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PurpleFintech, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Smart Alerts", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text("· computed on your phone, updated live", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (alerts.isEmpty()) {
                Text(
                    "Nothing needs your attention right now. Alerts appear here for cash-runway risks, spending spikes, unusual debits, upcoming recurring payments and goals falling behind.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            alerts.take(6).forEach { alert ->
                val color = severityColor(alert.severity)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.08f))
                        .clickable(enabled = alert.suggestedQuestion != null) { alert.suggestedQuestion?.let(onAsk) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = when (alert.severity) {
                            AlertSeverity.CRITICAL, AlertSeverity.WARNING -> Icons.Default.Warning
                            AlertSeverity.POSITIVE -> Icons.Default.CheckCircle
                            AlertSeverity.INFO -> Icons.Default.Info
                        },
                        contentDescription = severityLabel(alert.severity),
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(alert.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(alert.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (alert.suggestedQuestion != null) {
                            Text("Ask the advisor →", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PurpleFintech)
                        }
                    }
                }
            }
        }
    }
}

/** Cash forecast, savings rate, monthly history and recurring payments from the deep context. */
@Composable
fun ForecastAndPatternsCard(deep: AdvisorDeepContext) {
    val f = deep.forecast
    val dateFmt = SimpleDateFormat("d MMM", Locale.US)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("advisor_forecast"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cash Forecast & Patterns", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                text = if (f.basedOnDays > 0) {
                    "At your ${f.basedOnDays}-day trend of ${formatCurrency(f.averageDailyNet)} per day"
                } else {
                    "Not enough history yet for a trend"
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ForecastFigure("Now", f.currentBalance)
                ForecastFigure("30 days", f.in30Days)
                ForecastFigure("60 days", f.in60Days)
                ForecastFigure("90 days", f.in90Days)
            }
            f.daysUntilEmpty?.let {
                Text("⚠️ At this pace the balance reaches zero in about $it days.", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RoseExpense)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                deep.savingsRate?.let {
                    Text("Savings rate (3 mo): ${(it * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                deep.expenseChangePct?.let {
                    Text(
                        "Spending vs last month: ${if (it >= 0) "+" else ""}${it.toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (it > 10) RoseExpense else if (it < -10) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (deep.monthlyHistory.any { it.income > 0 || it.expense > 0 }) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Text("Last 6 months (in / out / net)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                deep.monthlyHistory.forEach { m ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(m.label, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                        Text("+${formatCurrency(m.income)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("-${formatCurrency(m.expense)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            formatCurrency(m.net),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (m.net >= 0) EmeraldPrimary else RoseExpense
                        )
                    }
                }
            }

            if (deep.recurringPayments.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Text("Recurring payments detected", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                deep.recurringPayments.take(5).forEach { r ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            r.counterparty,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "~${formatCurrency(r.averageAmount)} / ${r.cadenceDays}d · next ${dateFmt.format(Date(r.nextExpectedMillis))}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastFigure(label: String, amount: Double) {
    Column {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatCurrency(amount).removeSuffix(" ETB"),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (amount < 0) RoseExpense else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Agent mode / live web toggle shown above the chat input. */
@Composable
fun AdvisorModeBar(
    webMode: Boolean,
    onWebModeChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !webMode,
                onClick = { onWebModeChange(false) },
                label = { Text("🤖 Agent (your data)", fontSize = 11.sp) }
            )
            FilterChip(
                selected = webMode,
                onClick = { onWebModeChange(true) },
                label = { Text("🌐 Live web (Google Search)", fontSize = 11.sp) }
            )
        }
        Text(
            text = if (webMode) {
                "Answers use current information from the web (rates, T-bill auctions, prices) with sources."
            } else {
                "The agent looks up your transactions, trends and plan itself, and can add milestones, create goals or categorize debits when you ask."
            },
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** What the agent did (tool calls) and the web sources it used, shown under its answer. */
@Composable
fun AgentTraceChips(toolActions: List<String>, sources: List<String>) {
    if (toolActions.isEmpty() && sources.isEmpty()) return
    Column(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        toolActions.forEach { action ->
            Surface(shape = RoundedCornerShape(8.dp), color = PurpleFintech.copy(alpha = 0.10f)) {
                Text(
                    action,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        if (sources.isNotEmpty()) {
            Text("Sources", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            sources.forEach { src ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(BlueFintech))
                    Spacer(Modifier.width(4.dp))
                    Text(src, fontSize = 10.sp, color = BlueFintech, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Ethiopian market figures the advisor is allowed to quote, with dates, sources and staleness. */
@Composable
fun MarketDataCard(
    market: MarketSnapshot,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onEdit: (MarketIndicators.Indicator, MarketRate?) -> Unit
) {
    val now = System.currentTimeMillis()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("advisor_market_data"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ethiopian Market Data", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = if (market.lastWebRefreshAt > 0) {
                            "Web check: ${SimpleDateFormat("d MMM HH:mm", Locale.US).format(Date(market.lastWebRefreshAt))} · tap a row to correct it"
                        } else {
                            "Starting values from July 2026 · tap a row to correct it"
                        },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onRefresh, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Refresh", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            market.lastRefreshError?.let {
                Text("Last web refresh failed: $it", fontSize = 10.sp, color = RoseExpense)
            }
            MarketIndicators.ALL.forEach { indicator ->
                val rate = market.get(indicator.key)
                val stale = rate != null && rate.ageDays(now) > MarketDataService.STALE_DAYS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEdit(indicator, rate) }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(indicator.label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = when {
                                rate == null -> "Unknown — refresh or enter it"
                                else -> "${rate.asOf} · ${if (rate.origin == MarketOrigin.USER) "you" else rate.source}" + (if (stale) " · old" else "")
                            },
                            fontSize = 10.sp,
                            color = if (rate == null || stale) AmberAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = rate?.display ?: "—",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun MarketValueDialog(
    indicator: MarketIndicators.Indicator,
    current: MarketRate?,
    onDismiss: () -> Unit,
    onSave: (Double, String, String) -> Unit
) {
    var valueText by remember { mutableStateOf(current?.value?.toString() ?: "") }
    var asOf by remember { mutableStateOf(current?.asOf ?: SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date())) }
    var note by remember { mutableStateOf(current?.note ?: "") }
    val parsed = valueText.replace(",", "").toDoubleOrNull()
    val valid = parsed != null && parsed >= indicator.min && parsed <= indicator.max
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(indicator.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current != null && current.sourceUrl.isNotBlank()) {
                    Text("Source: ${current.source} — ${current.sourceUrl}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Value (${indicator.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = valueText.isNotBlank() && !valid
                )
                OutlinedTextField(
                    value = asOf,
                    onValueChange = { asOf = it },
                    label = { Text("Applies to (date or month)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (e.g. which bank quoted it)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { parsed?.let { onSave(it, asOf.trim(), note.trim()) } }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Local options ranked by real (after inflation, tax and costs) return for the user's deployable cash. */
@Composable
fun RealReturnRankingCard(
    ranked: List<RankedOption>,
    amountEtb: Double,
    inflation: MarketRate?
) {
    var expandedId by remember { mutableStateOf<String?>(null) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("advisor_real_return_ranking"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Where Your Money Grows (after inflation)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                text = "For ${formatCurrency(amountEtb)} above your emergency reserve · " +
                    (inflation?.let { "inflation ${it.display} (${it.asOf})" } ?: "inflation unknown — refresh market data"),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ranked.forEach { r ->
                val real = r.realPct
                val realColor = when {
                    real == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    real >= 0 -> EmeraldPrimary
                    else -> RoseExpense
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (r.eligible) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                        .clickable { expandedId = if (expandedId == r.option.id) null else r.option.id }
                        .padding(vertical = 5.dp, horizontal = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                r.option.name,
                                fontSize = 12.sp,
                                fontWeight = if (r.eligible) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (r.eligible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${r.option.liquidity.label} · risk ${r.option.risk}/3" +
                                    (if (!r.eligible) " · not for you: " + r.reasons.filter { it != "inflation unknown" && !it.startsWith("interest tax") }.joinToString(", ") else ""),
                                fontSize = 10.sp,
                                color = if (r.eligible) MaterialTheme.colorScheme.onSurfaceVariant else AmberAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = real?.let { String.format(Locale.US, "%+.1f%% real", it) } ?: "real ?",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = realColor
                            )
                            Text(
                                text = r.nominalPct?.let { String.format(Locale.US, "%.1f%% nominal", it) } ?: "price-based",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (expandedId == r.option.id) {
                        Spacer(Modifier.height(4.dp))
                        Text(r.option.description, fontSize = 11.sp)
                        if (r.rateNote.isNotBlank()) Text("Based on: ${r.rateNote}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        r.realYearlyEtb?.let {
                            Text("≈ ${formatCurrency(it)} per year in today's money", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = realColor)
                        }
                        Text("How: ${r.option.howTo}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (r.option.caution.isNotBlank()) Text("Careful: ${r.option.caution}", fontSize = 10.sp, color = AmberAccent)
                    }
                }
            }
        }
    }
}

/** The latest weekly money briefing (also posted as a notification once a week). */
@Composable
fun WeeklyBriefingCard(
    briefing: WeeklyBriefing?,
    isGenerating: Boolean,
    onGenerate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("advisor_weekly_briefing"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PurpleFintech.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(briefing?.title ?: "Weekly money briefing", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = briefing?.let { "Week of ${SimpleDateFormat("d MMM", Locale.US).format(Date(it.createdAt))} · arrives as a notification every week" }
                            ?: "Arrives as a notification every week; tap to create one now",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onGenerate, enabled = !isGenerating) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (briefing == null) "Create" else "Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            briefing?.lines?.forEach { line ->
                Text("• $line", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** 👍 / 👎 under an advisor answer; feedback becomes part of the agent's memory. */
@Composable
fun AnswerFeedbackRow(rating: Int, onRate: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (rating) {
            1 -> Text("👍 Thanks — the advisor will keep doing this", fontSize = 10.sp, color = EmeraldPrimary)
            -1 -> Text("👎 Noted — the advisor learned from this", fontSize = 10.sp, color = AmberAccent)
            else -> {
                Text("Helpful?", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onRate(true) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) { Text("👍", fontSize = 13.sp) }
                TextButton(onClick = { onRate(false) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) { Text("👎", fontSize = 13.sp) }
            }
        }
    }
}

@Composable
fun AnswerFeedbackDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What was wrong?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The advisor saves this as a lesson and avoids it next time.", fontSize = 12.sp)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(300) },
                    placeholder = { Text("e.g. too generic, wrong numbers, ignored that I can't lock money") },
                    minLines = 2
                )
            }
        },
        confirmButton = { Button(onClick = { onSubmit(reason.trim()) }) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
