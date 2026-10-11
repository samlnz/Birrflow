package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.NetWorthLine
import com.example.ai.NetWorthSummary
import com.example.data.entity.WealthItemEntity
import com.example.data.entity.WealthKind
import com.example.ui.components.formatCurrency
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.RoseExpense
import com.example.ui.viewmodel.MainViewModel
import java.util.Locale
import java.util.UUID

/** Tabs of the "Me" screen: everything the advisor should know about the user. */
enum class ProfileTab(val title: String) {
    ABOUT_ME("About Me"),
    NET_WORTH("Net Worth"),
    MEMORY("AI Memory")
}

@Composable
fun ProfileHubScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableIntStateOf(0) }
    val tabs = ProfileTab.values()
    Column(modifier = modifier.fillMaxSize().testTag("profile_hub_screen")) {
        ScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(tab.title, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when (tabs[selected]) {
                ProfileTab.ABOUT_ME -> AboutMeTab(viewModel)
                ProfileTab.NET_WORTH -> NetWorthTab(viewModel)
                ProfileTab.MEMORY -> AgentMemoryTab(viewModel)
            }
        }
    }
}

@Composable
private fun NetWorthTab(viewModel: MainViewModel) {
    val summary by viewModel.netWorth.collectAsStateWithLifecycle()
    val items by viewModel.wealthItems.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<WealthItemEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    val byId = remember(items) { items.associateBy { it.itemId } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("net_worth_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { NetWorthHeader(summary) }
        item {
            Button(
                onClick = { adding = true },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add asset or debt (equb, gold, shares, loan, stock…)")
            }
        }
        item { SectionLabel("ASSETS (${summary.assets.size})") }
        if (summary.assets.isEmpty()) {
            item { Hint("Add what bank SMS can't see: equb you've paid into, gold, shares, money you've lent out, business stock, land or a vehicle.") }
        }
        items(summary.assets, key = { "a_" + (it.itemId ?: it.name) }) { line ->
            LedgerRow(line, isDebt = false) { line.itemId?.let { id -> editing = byId[id] } }
        }
        item { SectionLabel("DEBTS (${summary.liabilities.size})") }
        if (summary.liabilities.isEmpty()) {
            item { Hint("Loans or supplier credit you owe. Add the yearly rate so the advisor can tell when paying them down beats investing.") }
        }
        items(summary.liabilities, key = { "d_" + (it.itemId ?: it.name) }) { line ->
            LedgerRow(line, isDebt = true) { line.itemId?.let { id -> editing = byId[id] } }
        }
    }

    if (adding || editing != null) {
        WealthItemDialog(
            existing = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { viewModel.saveWealthItem(it); adding = false; editing = null },
            onDelete = { id -> viewModel.deleteWealthItem(id); adding = false; editing = null }
        )
    }
}

@Composable
private fun NetWorthHeader(summary: NetWorthSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("NET WORTH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(
                formatCurrency(summary.netWorth),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = if (summary.netWorth >= 0) MaterialTheme.colorScheme.onSurface else RoseExpense
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cash, banks & wallets", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatCurrency(summary.cashAndAccounts), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Other assets", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("+" + formatCurrency(summary.otherAssetsTotal), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = EmeraldPrimary)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Debts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("-" + formatCurrency(summary.liabilitiesTotal), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RoseExpense)
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
internal fun Hint(text: String) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LedgerRow(line: NetWorthLine, isDebt: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(line.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(line.kind?.label, line.detail.takeIf { it.isNotBlank() }).joinToString(" · "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                (if (isDebt) "-" else "") + formatCurrency(line.valueEtb),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isDebt) RoseExpense else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun WealthItemDialog(
    existing: WealthItemEntity?,
    onDismiss: () -> Unit,
    onSave: (WealthItemEntity) -> Unit,
    onDelete: (String) -> Unit
) {
    var kind by remember { mutableStateOf(existing?.kind ?: WealthKind.EQUB) }
    var kindMenu by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var quantity by remember { mutableStateOf(existing?.quantity?.let { plain(it) } ?: "") }
    var unitPrice by remember { mutableStateOf(existing?.unitPriceEtb?.takeIf { it > 0 }?.let { plain(it) } ?: "") }
    var rate by remember { mutableStateOf(existing?.ratePct?.takeIf { it > 0 }?.let { plain(it) } ?: "") }
    var counterparty by remember { mutableStateOf(existing?.counterparty ?: "") }
    var dueDate by remember { mutableStateOf(existing?.dueDate ?: "") }
    var recurring by remember { mutableStateOf(existing?.recurringAmountEtb?.takeIf { it > 0 }?.let { plain(it) } ?: "") }
    var everyDays by remember { mutableStateOf(existing?.recurringEveryDays?.takeIf { it > 0 }?.toString() ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    val quantityValue = quantity.replace(",", "").toDoubleOrNull()
    val needsPrice = kind == WealthKind.SHARES
    val priceValue = unitPrice.replace(",", "").toDoubleOrNull()
    val valid = quantityValue != null && quantityValue >= 0 && (!needsPrice || (priceValue != null && priceValue > 0))
    val showRate = kind in listOf(WealthKind.LOAN, WealthKind.RECEIVABLE, WealthKind.DEPOSIT, WealthKind.PAYABLE)
    val showRecurring = kind in listOf(WealthKind.EQUB, WealthKind.LOAN)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add asset or debt" else "Edit ${existing.kind.label.lowercase(Locale.ROOT)}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    OutlinedButton(onClick = { kindMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text((if (kind.isLiability) "Debt: " else "Asset: ") + kind.label)
                    }
                    DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                        WealthKind.values().forEach { k ->
                            DropdownMenuItem(
                                text = { Text((if (k.isLiability) "Debt — " else "Asset — ") + k.label) },
                                onClick = { kind = k; kindMenu = false }
                            )
                        }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") },
                    placeholder = { Text("e.g. Office equb, Wedding gold, Loan from Abay Bank") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text(kind.quantityLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (kind == WealthKind.SHARES || kind == WealthKind.GOLD) {
                    OutlinedTextField(
                        value = unitPrice,
                        onValueChange = { unitPrice = it },
                        label = { Text(if (kind == WealthKind.SHARES) "Price per share (ETB)" else "Price per gram (blank = market price)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (showRate) {
                    OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text("Yearly interest / profit rate (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(value = counterparty, onValueChange = { counterparty = it }, label = { Text("Bank, person or group") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text(if (kind == WealthKind.EQUB) "My payout date (dd/MM/yyyy)" else "Due / maturity date (dd/MM/yyyy)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showRecurring) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = recurring, onValueChange = { recurring = it },
                            label = { Text(if (kind == WealthKind.EQUB) "Contribution (ETB)" else "Instalment (ETB)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = everyDays, onValueChange = { everyDays = it }, label = { Text("Every (days)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                if (existing != null) {
                    TextButton(onClick = { onDelete(existing.itemId) }) { Text("Delete", color = RoseExpense) }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    val item = WealthItemEntity(
                        itemId = existing?.itemId ?: ("W-" + UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT)),
                        kind = kind,
                        name = name.trim().ifBlank { kind.label },
                        quantity = quantityValue ?: 0.0,
                        unitPriceEtb = priceValue ?: 0.0,
                        ratePct = if (showRate) rate.replace(",", "").toDoubleOrNull() ?: 0.0 else 0.0,
                        counterparty = counterparty.trim(),
                        dueDate = dueDate.trim(),
                        recurringAmountEtb = if (showRecurring) recurring.replace(",", "").toDoubleOrNull() ?: 0.0 else 0.0,
                        recurringEveryDays = if (showRecurring) everyDays.toIntOrNull() ?: 0 else 0,
                        notes = notes.trim(),
                        createdAt = existing?.createdAt ?: System.currentTimeMillis()
                    )
                    onSave(item)
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun plain(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
