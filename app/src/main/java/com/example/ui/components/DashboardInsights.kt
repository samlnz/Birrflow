package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.parseHexColor
import com.example.ui.screens.resolveCategoryIcon
import com.example.ui.theme.RoseExpense
import com.example.ui.viewmodel.CategorySpend
import com.example.ui.viewmodel.ChartRange
import com.example.ui.viewmodel.DashboardChartData
import com.example.ui.viewmodel.FlowBucket
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * "Spending by category" on the dashboard: every manual category with its amount, and an
 * Uncategorized row holding the debits that still wait for a category.
 */
@Composable
fun SpendingByCategoryCard(
    spends: List<CategorySpend>,
    periodLabel: String,
    modifier: Modifier = Modifier
) {
    val total = spends.sumOf { it.totalAmount }
    Column(modifier = modifier.testTag("spending_by_category")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SPENDING BY CATEGORY",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$periodLabel · ${formatCurrency(total)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                spends.forEachIndexed { index, spend ->
                    val color = if (spend.isUncategorized) RoseExpense else parseHexColor(spend.category.colorHex)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(color.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = resolveCategoryIcon(spend.category.iconName),
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = spend.category.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (spend.isUncategorized) FontWeight.Bold else FontWeight.Medium,
                                        color = if (spend.isUncategorized) RoseExpense else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (spend.isUncategorized) {
                                            "${spend.transactionCount} debits waiting for a category"
                                        } else {
                                            "${spend.transactionCount} transactions · ${(spend.percentage * 100).toInt()}%"
                                        },
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = formatCurrency(spend.totalAmount),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (spend.isUncategorized) RoseExpense else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { spend.percentage.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = color,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                    if (spend.isUncategorized && index < spends.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Dashboard charts (replaces the old "Transactions" shortcut card at the bottom of the dashboard)
// ---------------------------------------------------------------------------------------------

// Validated colorblind-safe pair (blue = money in, orange = money out) for light and dark surfaces
private val InflowLight = Color(0xFF0284C7)
private val OutflowLight = Color(0xFFEA580C)
private val InflowDark = Color(0xFF4FB3F2)
private val OutflowDark = Color(0xFFF48A3C)
private val OtherSliceColor = Color(0xFF94A3B8)
private const val MAX_DONUT_SLICES = 6

@Composable
fun DashboardChartsSection(
    data: DashboardChartData,
    scopeLabel: String,
    onRangeChange: (ChartRange) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val inColor = if (isDark) InflowDark else InflowLight
    val outColor = if (isDark) OutflowDark else OutflowLight
    // One selection shared by the bar and trend charts (they plot the same days/months)
    var selected by remember(data.range, data.buckets.size) { mutableStateOf<Int?>(null) }

    Column(modifier = modifier.testTag("dashboard_charts"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text(
                text = "INSIGHTS & TRENDS",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$scopeLabel · last ${data.range.label}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChartRange.values().forEach { range ->
                FilterChip(
                    selected = data.range == range,
                    onClick = { onRangeChange(range) },
                    label = { Text(range.label, fontSize = 12.sp) }
                )
            }
        }

        if (!data.hasData) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "No transactions in the last ${data.range.label} for this filter yet. Charts fill in as bank SMS arrive.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            return@Column
        }

        // 1. Money in vs money out + running net cash flow
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Money in vs money out", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LegendSwatch(inColor, "In")
                        Spacer(Modifier.width(10.dp))
                        LegendSwatch(outColor, "Out")
                    }
                }
                val focus = selected?.let { data.buckets.getOrNull(it) }
                Text(
                    text = if (focus != null) {
                        "${focus.longLabel} · ${focus.transactionCount} transactions"
                    } else {
                        "Tap a bar for details · total for the period"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FlowFigure("In", focus?.inflow ?: data.totalInflow, inColor)
                    FlowFigure("Out", focus?.outflow ?: data.totalOutflow, outColor)
                    val net = focus?.net ?: (data.totalInflow - data.totalOutflow)
                    FlowFigure("Net", net, null)
                }
                CashFlowBarChart(
                    buckets = data.buckets,
                    selected = selected,
                    onSelect = { selected = if (selected == it) null else it },
                    inColor = inColor,
                    outColor = outColor
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Text("Net cash flow (running total)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                NetTrendChart(
                    values = data.cumulativeNet,
                    selected = selected,
                    onSelect = { selected = if (selected == it) null else it },
                    lineColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 2. Spending mix
        if (data.spending.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Spending mix", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    SpendingDonut(data.spending)
                }
            }
        }

        // 3. Income by source
        if (data.incomeBySource.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Income by source", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    val maxIncome = data.incomeBySource.maxOf { it.amount }.coerceAtLeast(1.0)
                    data.incomeBySource.forEach { slice ->
                        HorizontalValueBar(
                            name = slice.name,
                            detail = "${slice.transactionCount} credits",
                            amount = slice.amount,
                            fraction = (slice.amount / maxIncome).toFloat(),
                            color = parseHexColor(slice.colorHex, OtherSliceColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FlowFigure(label: String, amount: Double, swatch: Color?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (swatch != null) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(swatch))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = (if (swatch == null && amount > 0) "+" else "") + formatCurrency(amount),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CashFlowBarChart(
    buckets: List<FlowBucket>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    inColor: Color,
    outColor: Color
) {
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val axisMax = niceCeiling(buckets.maxOfOrNull { maxOf(it.inflow, it.outflow) } ?: 0.0)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .testTag("cash_flow_bar_chart")
            .pointerInput(buckets) {
                detectTapGestures { offset ->
                    val left = AXIS_GUTTER.toPx()
                    val slot = (size.width - left) / buckets.size.coerceAtLeast(1)
                    val index = ((offset.x - left) / slot).toInt()
                    if (offset.x >= left && index in buckets.indices) onSelect(index)
                }
            }
    ) {
        if (buckets.isEmpty()) return@Canvas
        val left = AXIS_GUTTER.toPx()
        val bottom = size.height - 18.dp.toPx()
        val top = 6.dp.toPx()
        val plotH = bottom - top
        val slot = (size.width - left) / buckets.size
        fun yOf(v: Double) = bottom - (v / axisMax * plotH).toFloat()

        // Recessive grid with three value labels
        listOf(0.0, axisMax / 2, axisMax).forEach { v ->
            val y = yOf(v)
            drawLine(gridColor, Offset(left, y), Offset(size.width, y), strokeWidth = 1f)
            val label = textMeasurer.measure(compactAmount(v), axisStyle)
            drawText(label, topLeft = Offset(0f, (y - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)))
        }

        selected?.let { i ->
            drawRect(highlight, topLeft = Offset(left + slot * i, top), size = Size(slot, plotH))
        }

        val gap = 2.dp.toPx()
        val groupW = slot * 0.72f
        val barW = ((groupW - gap) / 2f).coerceAtLeast(1f)
        val radius = minOf(4.dp.toPx(), barW / 2f)
        buckets.forEachIndexed { i, b ->
            val groupLeft = left + slot * i + (slot - groupW) / 2f
            drawTopRoundedBar(groupLeft, barW, yOf(b.inflow), bottom, radius, inColor)
            drawTopRoundedBar(groupLeft + barW + gap, barW, yOf(b.outflow), bottom, radius, outColor)
        }

        // X labels, thinned so they never collide
        val step = ((buckets.size + 6) / 7).coerceAtLeast(1)
        buckets.forEachIndexed { i, b ->
            if (i % step != 0) return@forEachIndexed
            val label = textMeasurer.measure(b.label, axisStyle)
            val x = (left + slot * i + slot / 2f - label.size.width / 2f).coerceIn(left, size.width - label.size.width)
            drawText(label, topLeft = Offset(x, bottom + 4.dp.toPx()))
        }
    }
}

private fun DrawScope.drawTopRoundedBar(left: Float, width: Float, top: Float, bottom: Float, radius: Float, color: Color) {
    if (bottom - top < 0.5f) return
    val r = minOf(radius, bottom - top)
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = left + width,
                bottom = bottom,
                topLeftCornerRadius = CornerRadius(r, r),
                topRightCornerRadius = CornerRadius(r, r),
                bottomRightCornerRadius = CornerRadius.Zero,
                bottomLeftCornerRadius = CornerRadius.Zero
            )
        )
    }
    drawPath(path, color)
}

@Composable
private fun NetTrendChart(
    values: List<Double>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    lineColor: Color
) {
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val surface = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .testTag("net_trend_chart")
            .pointerInput(values) {
                detectTapGestures { offset ->
                    val left = AXIS_GUTTER.toPx()
                    val slot = (size.width - left) / values.size.coerceAtLeast(1)
                    val index = ((offset.x - left) / slot).toInt()
                    if (offset.x >= left && index in values.indices) onSelect(index)
                }
            }
    ) {
        if (values.isEmpty()) return@Canvas
        val left = AXIS_GUTTER.toPx()
        val top = 14.dp.toPx()
        val bottom = size.height - 6.dp.toPx()
        val slot = (size.width - left) / values.size
        val hi = maxOf(0.0, values.max())
        val lo = minOf(0.0, values.min())
        val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
        fun yOf(v: Double) = bottom - ((v - lo) / span * (bottom - top)).toFloat()
        fun xOf(i: Int) = left + slot * i + slot / 2f

        // Zero baseline (dashed) with its label
        val zeroY = yOf(0.0)
        drawLine(
            gridColor, Offset(left, zeroY), Offset(size.width, zeroY), strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )
        val zero = textMeasurer.measure("0", axisStyle)
        drawText(zero, topLeft = Offset(0f, zeroY - zero.size.height / 2f))

        val line = Path()
        values.forEachIndexed { i, v -> if (i == 0) line.moveTo(xOf(i), yOf(v)) else line.lineTo(xOf(i), yOf(v)) }
        val area = Path().apply {
            addPath(line)
            lineTo(xOf(values.lastIndex), zeroY)
            lineTo(xOf(0), zeroY)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.22f), lineColor.copy(alpha = 0.02f))))
        drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Direct label on the latest value, plus a marker on the tapped point
        val markIndex = selected ?: values.lastIndex
        val markX = xOf(markIndex)
        val markY = yOf(values[markIndex])
        drawCircle(surface, radius = 6.dp.toPx(), center = Offset(markX, markY))
        drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(markX, markY))
        val label = textMeasurer.measure(compactAmount(values[markIndex], signed = true), valueStyle)
        val labelX = (markX - label.size.width / 2f).coerceIn(left, size.width - label.size.width)
        val labelY = (markY - label.size.height - 6.dp.toPx()).coerceAtLeast(0f)
        drawText(label, topLeft = Offset(labelX, labelY))
    }
}

private data class DonutSlice(val name: String, val amount: Double, val color: Color, val count: Int)

@Composable
private fun SpendingDonut(spending: List<CategorySpend>) {
    val sorted = spending.sortedByDescending { it.totalAmount }
    val total = sorted.sumOf { it.totalAmount }
    val slices = buildList {
        sorted.take(if (sorted.size > MAX_DONUT_SLICES) MAX_DONUT_SLICES - 1 else MAX_DONUT_SLICES).forEach {
            add(DonutSlice(it.category.name, it.totalAmount, if (it.isUncategorized) RoseExpense else parseHexColor(it.category.colorHex), it.transactionCount))
        }
        if (sorted.size > MAX_DONUT_SLICES) {
            val rest = sorted.drop(MAX_DONUT_SLICES - 1)
            add(DonutSlice("Other (${rest.size})", rest.sumOf { it.totalAmount }, OtherSliceColor, rest.sumOf { it.transactionCount }))
        }
    }
    val textMeasurer = rememberTextMeasurer()
    val centerStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    val centerSub = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("spending_donut")) {
        Canvas(modifier = Modifier.size(128.dp)) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val gapDeg = if (slices.size > 1) 2f else 0f
            var start = -90f
            slices.forEach { s ->
                val sweep = (s.amount / total * 360.0).toFloat()
                if (sweep > gapDeg) {
                    drawArc(
                        color = s.color,
                        startAngle = start + gapDeg / 2f,
                        sweepAngle = sweep - gapDeg,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke)
                    )
                }
                start += sweep
            }
            val amount = textMeasurer.measure(compactAmount(total), centerStyle)
            val sub = textMeasurer.measure("ETB spent", centerSub)
            val blockH = amount.size.height + sub.size.height
            drawText(amount, topLeft = Offset(center.x - amount.size.width / 2f, center.y - blockH / 2f))
            drawText(sub, topLeft = Offset(center.x - sub.size.width / 2f, center.y - blockH / 2f + amount.size.height))
        }
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            slices.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(s.color))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = s.name,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${compactAmount(s.amount)} · ${(s.amount / total * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizontalValueBar(name: String, detail: String, amount: Double, fraction: Float, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatCurrency(amount), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

private val AXIS_GUTTER = 40.dp

/** Rounds an axis maximum up to 1, 2, 2.5 or 5 x 10^n so gridline labels stay readable. */
private fun niceCeiling(value: Double): Double {
    if (value <= 0.0) return 1.0
    val magnitude = 10.0.pow(floor(log10(value)))
    val normalized = value / magnitude
    val nice = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it >= normalized - 1e-9 }
    return nice * magnitude
}

private fun compactAmount(value: Double, signed: Boolean = false): String {
    val sign = when {
        value < 0 -> "-"
        signed && value > 0 -> "+"
        else -> ""
    }
    val a = abs(value)
    val body = when {
        a >= 1_000_000 -> String.format(Locale.US, "%.1fM", a / 1_000_000)
        a >= 1_000 -> String.format(Locale.US, "%.1fK", a / 1_000)
        else -> String.format(Locale.US, "%.0f", a)
    }
    return sign + body
}
