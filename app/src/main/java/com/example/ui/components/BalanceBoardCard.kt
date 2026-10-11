package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ManualSourceEntity
import com.example.data.entity.ManualSourceKind
import com.example.data.entity.RegisteredPhoneEntity
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BlueFintech
import com.example.ui.theme.CyanIncome
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.RoseExpense
import com.example.ui.viewmodel.BalanceBoard
import com.example.ui.viewmodel.BalanceGrouping
import com.example.ui.viewmodel.BalanceRow
import com.example.ui.viewmodel.FlowFilter

/**
 * "Latest bank balances & activity": SMS balances + manual cash sources with a grand total,
 * grouped by bank or by phone, with credit/debit activity for the selected period.
 */
@Composable
fun BalanceBoardCard(
    board: BalanceBoard,
    phones: List<RegisteredPhoneEntity>,
    periodLabel: String,
    onGroupingChange: (BalanceGrouping) -> Unit,
    onFlowFilterChange: (FlowFilter) -> Unit,
    onSourceClick: (String) -> Unit,
    onSaveManual: (existing: ManualSourceEntity?, name: String, kind: ManualSourceKind, balance: Double, phoneId: String?, note: String) -> Unit,
    onDeleteManual: (ManualSourceEntity) -> Unit,
    onAdjustManual: ((ManualSourceEntity, Double) -> Unit)? = null,
    onToggleDisable: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var editorTarget by remember { mutableStateOf<ManualEditorTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<ManualSourceEntity?>(null) }
    var adjustTarget by remember { mutableStateOf<ManualAdjustTarget?>(null) }
    var expandedRowKeys by remember { mutableStateOf(emptySet<String>()) }

    Column(modifier = modifier.padding(horizontal = 16.dp).testTag("balance_board")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LATEST BANK BALANCES & ACTIVITY",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Bank SMS accounts + Other incomes & cash assets",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { editorTarget = ManualEditorTarget(null) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("add_manual_source_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("+ Income / Cash", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Grand total card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = EmeraldPrimary.copy(alpha = 0.12f))
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("TOTAL CASH ASSET (All Sources Cumulative)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                Text(
                    formatCurrency(board.totalBalance),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("balance_board_total")
                )
                Text(
                    "Banks & Wallets: ${formatCurrency(board.smsBalanceTotal)}  ·  Other Incomes / Cash: ${formatCurrency(board.manualBalanceTotal)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (board.flowFilter != FlowFilter.DEBIT) {
                        TotalPill("Credit ($periodLabel)", "+" + formatCurrency(board.totalCredit), "${board.creditCount} tx", EmeraldPrimary, Modifier.weight(1f))
                    }
                    if (board.flowFilter != FlowFilter.CREDIT) {
                        TotalPill("Debit ($periodLabel)", "−" + formatCurrency(board.totalDebit), "${board.debitCount} tx", RoseExpense, Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BalanceGrouping.values().forEach { g ->
                FilterChip(
                    selected = board.grouping == g,
                    onClick = { onGroupingChange(g) },
                    label = { Text(g.label, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            if (g == BalanceGrouping.BY_PHONE) Icons.Default.PhoneAndroid else Icons.Default.AccountBalance,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BlueFintech.copy(alpha = 0.2f))
                )
            }
            Spacer(Modifier.width(6.dp))
            FlowFilter.values().forEach { f ->
                FilterChip(
                    selected = board.flowFilter == f,
                    onClick = { onFlowFilterChange(f) },
                    label = { Text(f.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (f) {
                            FlowFilter.CREDIT -> EmeraldPrimary.copy(alpha = 0.2f)
                            FlowFilter.DEBIT -> RoseExpense.copy(alpha = 0.2f)
                            FlowFilter.ALL -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    ),
                    modifier = Modifier.testTag("flow_filter_${f.name}")
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            board.rows.forEach { row ->
                key(row.key) {
                    BalanceRowCard(
                        row = row,
                        flowFilter = board.flowFilter,
                        isExpanded = row.key in expandedRowKeys,
                        onToggleExpand = {
                            expandedRowKeys = if (row.key in expandedRowKeys) expandedRowKeys - row.key else expandedRowKeys + row.key
                        },
                        onClick = { row.sourceId?.let(onSourceClick) },
                        onEditManual = { editorTarget = ManualEditorTarget(it) },
                        onDeleteManual = { deleteTarget = it },
                        onQuickAdjust = { src, delta ->
                            if (onAdjustManual != null) {
                                onAdjustManual(src, delta)
                            } else {
                                adjustTarget = ManualAdjustTarget(src)
                            }
                        },
                        onToggleDisable = onToggleDisable
                    )
                }
            }
            if (board.rows.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No active balances yet.", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Read latest bank SMS or add other incomes / cash sources to begin.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    editorTarget?.let { target ->
        ManualSourceDialog(
            existing = target.source,
            phones = phones,
            onDismiss = { editorTarget = null },
            onSave = { name, kind, balance, phoneId, note ->
                onSaveManual(target.source, name, kind, balance, phoneId, note)
                editorTarget = null
            },
            onDelete = target.source?.let { src -> { editorTarget = null; deleteTarget = src } }
        )
    }

    deleteTarget?.let { src ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove \"${src.name}\"?") },
            text = { Text("Its balance of ${formatCurrency(src.balance)} (${src.kind.label}) will be removed from your total cash asset. This deletion will sync across all your connected phones.") },
            confirmButton = {
                Button(
                    onClick = { onDeleteManual(src); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseExpense),
                    modifier = Modifier.testTag("confirm_delete_manual_source")
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    adjustTarget?.let { target ->
        ManualAdjustDialog(
            source = target.source,
            onDismiss = { adjustTarget = null },
            onConfirm = { delta ->
                onAdjustManual?.invoke(target.source, delta)
                adjustTarget = null
            }
        )
    }
}

private data class ManualEditorTarget(val source: ManualSourceEntity?)
private data class ManualAdjustTarget(val source: ManualSourceEntity)

@Composable
private fun TotalPill(label: String, value: String, count: String, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.12f), modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BalanceRowCard(
    row: BalanceRow,
    flowFilter: FlowFilter,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit,
    onEditManual: (ManualSourceEntity) -> Unit,
    onDeleteManual: (ManualSourceEntity) -> Unit,
    onQuickAdjust: (ManualSourceEntity, Double) -> Unit,
    onToggleDisable: ((String) -> Unit)? = null
) {
    val accent = if (row.isManual) AmberAccent else BlueFintech
    val containerColor = if (row.isDisabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth().testTag("balance_row_${row.key}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Content section - only clicks for transaction navigation when not manual
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = row.sourceId != null && !row.isManual) {
                            onClick()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(8.dp), color = if (row.isDisabled) Color.Gray.copy(alpha = 0.15f) else accent.copy(alpha = 0.15f), modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                when {
                                    row.isManual -> when (row.manualSource?.kind) {
                                        ManualSourceKind.SALARY -> Icons.Default.Payments
                                        ManualSourceKind.BUSINESS -> Icons.Default.AccountBalance
                                        ManualSourceKind.RENTAL -> Icons.Default.Payments
                                        ManualSourceKind.INVESTMENT -> Icons.Default.Payments
                                        else -> Icons.Default.Payments
                                    }
                                    row.sourceId == null -> Icons.Default.PhoneAndroid
                                    else -> Icons.Default.AccountBalance
                                },
                                contentDescription = null,
                                tint = if (row.isDisabled) Color.Gray else accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                row.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                                color = if (row.isDisabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                            )
                            if (row.isManual && row.manualSource != null) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AmberAccent.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        row.manualSource.kind.label,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AmberAccent,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            if (row.isDisabled) "Excluded from Total · ${row.subtitle}" else row.subtitle,
                            fontSize = 11.sp,
                            color = if (row.isDisabled) RoseExpense.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (row.recoveredCount > 0) {
                            Text("${row.recoveredCount} recovered from balance changes", fontSize = 10.sp, color = CyanIncome, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            row.balance?.let { formatCurrency(it) } ?: "—",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (row.isDisabled) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None,
                            color = if (row.isDisabled) Color.Gray else if (row.balance != null) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowAmounts(row, flowFilter)
                    }
                }

                if (row.children.isNotEmpty()) {
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(36.dp).testTag("chevron_${row.key}")
                    ) {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                // Independent Action Buttons (Disable, Edit, Delete) - completely isolated from content click
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (onToggleDisable != null) {
                        Surface(
                            shape = CircleShape,
                            color = if (row.isDisabled) RoseExpense.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(36.dp)
                        ) {
                            IconButton(
                                onClick = { onToggleDisable(row.key) },
                                modifier = Modifier.fillMaxSize().testTag("toggle_disable_${row.key}")
                            ) {
                                Icon(
                                    imageVector = if (row.isDisabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (row.isDisabled) "Enable source in total balance" else "Exclude source from total balance",
                                    tint = if (row.isDisabled) RoseExpense else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (row.isManual && row.manualSource != null) {
                        IconButton(onClick = { onEditManual(row.manualSource) }, modifier = Modifier.size(30.dp).testTag("edit_manual_${row.manualSource.manualSourceId}")) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(15.dp))
                        }
                        IconButton(onClick = { onDeleteManual(row.manualSource) }, modifier = Modifier.size(30.dp).testTag("delete_manual_${row.manualSource.manualSourceId}")) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = RoseExpense, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded && row.children.isNotEmpty()) {
                Column(Modifier.padding(top = 8.dp)) {
                    HorizontalDivider()
                    row.children.forEach { child ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(child.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(child.subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(child.balance?.let { formatCurrency(it) } ?: "—", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                FlowAmounts(child, flowFilter)
                            }
                            if (child.isManual && child.manualSource != null) {
                                IconButton(onClick = { onEditManual(child.manualSource) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    if (row.sourceId != null && !row.isManual) {
                        TextButton(onClick = onClick) { Text("View transactions →", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowAmounts(row: BalanceRow, flowFilter: FlowFilter) {
    if (row.isManual) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (flowFilter != FlowFilter.DEBIT && (row.creditCount > 0 || flowFilter == FlowFilter.CREDIT)) {
            Text("+${formatCurrency(row.credit)}", fontSize = 10.sp, color = EmeraldPrimary)
        }
        if (flowFilter != FlowFilter.CREDIT && (row.debitCount > 0 || flowFilter == FlowFilter.DEBIT)) {
            Text("−${formatCurrency(row.debit)}", fontSize = 10.sp, color = RoseExpense)
        }
    }
}

@Composable
fun ManualAdjustDialog(
    source: ManualSourceEntity,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var isAdd by rememberSaveable { mutableStateOf(true) }
    val amount = amountText.replace(",", "").toDoubleOrNull()
    val valid = amount != null && amount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust ${source.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Current Balance: ${formatCurrency(source.balance)}", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isAdd,
                        onClick = { isAdd = true },
                        label = { Text("+ Inflow / Add Income") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f))
                    )
                    FilterChip(
                        selected = !isAdd,
                        onClick = { isAdd = false },
                        label = { Text("− Expense / Deduct") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RoseExpense.copy(alpha = 0.2f))
                    )
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Adjustment Amount (ETB)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (amount != null) {
                        onConfirm(if (isAdd) amount else -amount)
                    }
                },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = if (isAdd) EmeraldPrimary else RoseExpense)
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ManualSourceDialog(
    existing: ManualSourceEntity?,
    phones: List<RegisteredPhoneEntity>,
    onDismiss: () -> Unit,
    onSave: (name: String, kind: ManualSourceKind, balance: Double, phoneId: String?, note: String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var balanceText by rememberSaveable { mutableStateOf(existing?.balance?.let { if (it == 0.0) "" else "%.2f".format(it) } ?: "") }
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: ManualSourceKind.CASH) }
    var phoneId by rememberSaveable { mutableStateOf(existing?.phoneId) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    val balance = balanceText.replace(",", "").toDoubleOrNull()
    val valid = name.isNotBlank() && balance != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Other Income / Cash Source" else "Edit ${existing.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Income / Source Name (e.g. Salary, Cash, Store Revenue)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("manual_source_name")
                )
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Current Balance / Amount (ETB)") },
                    singleLine = true,
                    isError = balanceText.isNotEmpty() && balance == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("manual_source_balance")
                )
                Text("Income Category", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ManualSourceKind.values().forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(k.label, fontSize = 12.sp) }
                        )
                    }
                }
                Text("Phone Assignment (Optional)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = phoneId == null, onClick = { phoneId = null }, label = { Text("All / Shared", fontSize = 12.sp) })
                    phones.forEach { p ->
                        FilterChip(selected = phoneId == p.phoneId, onClick = { phoneId = p.phoneId }, label = { Text(p.friendlyName, fontSize = 12.sp) })
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Description (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseExpense),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Remove this income source")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, kind, balance ?: 0.0, phoneId, note) },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.testTag("save_manual_source")
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
