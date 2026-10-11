package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.CategorySuggestion
import com.example.data.entity.CategoryEntity
import com.example.data.entity.FlowDirection
import com.example.data.entity.IncomeSourceEntity
import com.example.data.entity.RawSmsEntity
import com.example.data.entity.RegisteredPhoneEntity
import com.example.data.entity.TransactionEntity
import com.example.ui.components.TransactionDetailDialog
import com.example.ui.components.formatCurrency
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.PurpleFintech
import com.example.ui.theme.RoseExpense
import com.example.ui.viewmodel.MainViewModel

private const val UNCATEGORIZED_PAGE_SIZE = 10
private const val CATEGORY_DESCRIPTION_MAX = 300

private val CATEGORY_COLORS = listOf(
    "#10B981" to "Emerald",
    "#3B82F6" to "Blue",
    "#F59E0B" to "Amber",
    "#EF4444" to "Rose",
    "#8B5CF6" to "Purple",
    "#EC4899" to "Pink",
    "#06B6D4" to "Cyan",
    "#F97316" to "Orange",
    "#14B8A6" to "Teal",
    "#64748B" to "Slate"
)

private val CATEGORY_ICONS = listOf(
    "storefront" to Icons.Default.Storefront,
    "restaurant" to Icons.Default.Restaurant,
    "shopping_bag" to Icons.Default.ShoppingBag,
    "directions_car" to Icons.Default.DirectionsCar,
    "home" to Icons.Default.Home,
    "work" to Icons.Default.Work,
    "bolt" to Icons.Default.Bolt,
    "receipt_long" to Icons.Filled.ReceiptLong,
    "favorite" to Icons.Default.Favorite,
    "category" to Icons.Default.Category
)

fun parseHexColor(hex: String, fallback: Color = EmeraldPrimary): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

/** Same credit rule the database uses when filing a phone's incomes under its income source. */
private fun isIncomeCredit(tx: TransactionEntity): Boolean =
    tx.direction == FlowDirection.CREDIT ||
        tx.transactionType == com.example.data.entity.TransactionType.INCOME ||
        tx.transactionType == com.example.data.entity.TransactionType.DEPOSIT

fun resolveCategoryIcon(iconName: String): ImageVector {
    return CATEGORY_ICONS.firstOrNull { it.first == iconName }?.second ?: Icons.Default.Category
}

@Composable
fun IncomeAndExpenseCategoriesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.allCategories.collectAsStateWithLifecycle()
    val incomeSources by viewModel.allIncomeSources.collectAsStateWithLifecycle()
    val uncategorizedExpenses by viewModel.uncategorizedExpenses.collectAsStateWithLifecycle()
    val phones by viewModel.allPhones.collectAsStateWithLifecycle()
    val sources by viewModel.allSources.collectAsStateWithLifecycle()
    val filteredTransactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val everyTransaction by viewModel.allTransactionsUnfiltered.collectAsStateWithLifecycle()
    val suggestions by viewModel.categorySuggestions.collectAsStateWithLifecycle()
    val autoFile by viewModel.autoFileEnabled.collectAsStateWithLifecycle()
    val suggestionStats by viewModel.suggestionStats.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Dialog states
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryToDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    var showAddIncomeSourceDialog by remember { mutableStateOf(false) }
    var incomeSourceToEdit by remember { mutableStateOf<IncomeSourceEntity?>(null) }
    var incomeSourceToDelete by remember { mutableStateOf<IncomeSourceEntity?>(null) }

    var selectedTxForDetail by remember { mutableStateOf<TransactionEntity?>(null) }

    val phoneMap = remember(phones) { phones.associateBy { it.phoneId } }
    val sourceMap = remember(sources) { sources.associateBy { it.sourceId } }
    val incomeSourceMap = remember(incomeSources) { incomeSources.associateBy { it.sourceId } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("income_and_expense_screen")
    ) {
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Expenses & Categories", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (uncategorizedExpenses.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(containerColor = RoseExpense) {
                                Text("${uncategorizedExpenses.size}", fontSize = 10.sp)
                            }
                        }
                    }
                }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Income Sources & Phones", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTabIndex) {
                0 -> ExpensesTabContent(
                    categories = categories,
                    uncategorizedExpenses = uncategorizedExpenses,
                    phoneMap = phoneMap,
                    sourceMap = sourceMap,
                    allTransactions = filteredTransactions,
                    suggestions = suggestions,
                    autoFile = autoFile,
                    suggestionStats = suggestionStats,
                    onAutoFileChange = { viewModel.setAutoFileEnabled(it) },
                    onQuickCategorize = { txId, catId ->
                        viewModel.quickCategorizeExpense(txId, catId)
                    },
                    onOpenTransactionDetail = { selectedTxForDetail = it },
                    onAddCategory = { showAddCategoryDialog = true },
                    onEditCategory = { categoryToEdit = it },
                    onDeleteCategory = { categoryToDelete = it },
                    onAddPresetCategory = { name, icon, color ->
                        viewModel.addCategory(name, icon, color)
                    }
                )
                1 -> IncomeSourcesTabContent(
                    incomeSources = incomeSources,
                    phones = phones,
                    allTransactions = everyTransaction,
                    incomeSourceMap = incomeSourceMap,
                    onAssignPhone = { phoneId, sourceId ->
                        viewModel.assignPhoneToIncomeSource(phoneId, sourceId)
                    },
                    onAddIncomeSource = { showAddIncomeSourceDialog = true },
                    onEditIncomeSource = { incomeSourceToEdit = it },
                    onDeleteIncomeSource = { incomeSourceToDelete = it }
                )
            }
        }
    }

    // Add / Edit Category Dialog
    if (showAddCategoryDialog || categoryToEdit != null) {
        CategoryEditDialog(
            category = categoryToEdit,
            onDismiss = {
                showAddCategoryDialog = false
                categoryToEdit = null
            },
            onSave = { name, color, icon, description ->
                if (categoryToEdit != null) {
                    viewModel.updateCategory(categoryToEdit!!.copy(name = name, colorHex = color, iconName = icon, isCustom = true, description = description))
                } else {
                    viewModel.addCategory(name = name, icon = icon, color = color, description = description)
                }
                showAddCategoryDialog = false
                categoryToEdit = null
            }
        )
    }

    // Delete Category Confirmation
    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Category?") },
            text = { Text("Are you sure you want to delete '${cat.name}'? Any debit transactions previously marked with this category will become uncategorized so you can recategorize them.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCategory(cat)
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseExpense)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Add / Edit Income Source Dialog
    if (showAddIncomeSourceDialog || incomeSourceToEdit != null) {
        IncomeSourceEditDialog(
            source = incomeSourceToEdit,
            phones = phones,
            onDismiss = {
                showAddIncomeSourceDialog = false
                incomeSourceToEdit = null
            },
            onSave = { name, color, icon, phoneId, note ->
                if (incomeSourceToEdit != null) {
                    viewModel.updateIncomeSource(incomeSourceToEdit!!.copy(name = name, colorHex = color, iconName = icon, note = note))
                    if (phoneId != null) {
                        viewModel.assignPhoneToIncomeSource(phoneId, incomeSourceToEdit!!.sourceId)
                    }
                } else {
                    viewModel.addIncomeSource(name = name, colorHex = color, iconName = icon, phoneId = phoneId, note = note)
                }
                showAddIncomeSourceDialog = false
                incomeSourceToEdit = null
            }
        )
    }

    // Delete Income Source Confirmation
    incomeSourceToDelete?.let { src ->
        AlertDialog(
            onDismissRequest = { incomeSourceToDelete = null },
            title = { Text("Delete Income Source?") },
            text = { Text("Are you sure you want to delete '${src.name}'? Phones assigned to this income source will be unassigned.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteIncomeSource(src)
                        incomeSourceToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseExpense)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { incomeSourceToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Transaction Detail Dialog
    selectedTxForDetail?.let { tx ->
        val raw by produceState<RawSmsEntity?>(initialValue = null, tx.rawSmsId) {
            value = viewModel.getRawSms(tx.rawSmsId)
        }
        val phone = phoneMap[tx.phoneId]

        TransactionDetailDialog(
            transaction = tx,
            rawSms = raw,
            phoneName = phone?.friendlyName ?: tx.phoneId,
            categories = categories,
            onDismiss = { selectedTxForDetail = null },
            onSaveCategoryAndNotes = { newCatId, notes ->
                viewModel.updateTransactionCategory(tx.transactionId, newCatId, notes)
                selectedTxForDetail = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpensesTabContent(
    categories: List<CategoryEntity>,
    uncategorizedExpenses: List<TransactionEntity>,
    phoneMap: Map<String, RegisteredPhoneEntity>,
    sourceMap: Map<String, com.example.data.entity.FinancialSourceEntity>,
    allTransactions: List<TransactionEntity>,
    suggestions: Map<String, List<CategorySuggestion>>,
    autoFile: Boolean,
    suggestionStats: Pair<Int, Int>,
    onAutoFileChange: (Boolean) -> Unit,
    onQuickCategorize: (String, String) -> Unit,
    onOpenTransactionDetail: (TransactionEntity) -> Unit,
    onAddCategory: () -> Unit,
    onEditCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onAddPresetCategory: (String, String, String) -> Unit
) {
    val totalUncategorizedAmount = remember(uncategorizedExpenses) {
        uncategorizedExpenses.sumOf { it.amount }
    }

    // Only UNCATEGORIZED_PAGE_SIZE pending debits are shown at a time
    var currentPage by remember { mutableIntStateOf(0) }
    val pageCount = ((uncategorizedExpenses.size + UNCATEGORIZED_PAGE_SIZE - 1) / UNCATEGORIZED_PAGE_SIZE).coerceAtLeast(1)
    val safePage = currentPage.coerceIn(0, pageCount - 1)
    val pageItems = remember(uncategorizedExpenses, safePage) {
        uncategorizedExpenses.drop(safePage * UNCATEGORIZED_PAGE_SIZE).take(UNCATEGORIZED_PAGE_SIZE)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Uncategorized Expenses Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "UNCATEGORIZED EXPENSES",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uncategorizedExpenses.isNotEmpty()) RoseExpense else MaterialTheme.colorScheme.primary
                    )
                    if (uncategorizedExpenses.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = RoseExpense.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${uncategorizedExpenses.size} Pending",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoseExpense,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (uncategorizedExpenses.isNotEmpty()) {
                    Text(
                        text = formatCurrency(totalUncategorizedAmount),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = RoseExpense
                    )
                }
            }
        }

        if (uncategorizedExpenses.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("All debits categorized!", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "New debit SMS will alert you via an ongoing notification with your category buttons below.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(pageItems, key = { it.transactionId }) { tx ->
                val phone = phoneMap[tx.phoneId]
                val source = sourceMap[tx.sourceId]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("uncategorized_expense_${tx.transactionId}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = parseHexColor(source?.colorHex ?: "#10B981").copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = source?.name ?: tx.sourceId.uppercase(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = parseHexColor(source?.colorHex ?: "#10B981"),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${tx.transactionDate} ${tx.transactionTime}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = tx.merchant ?: tx.recipient ?: tx.description.take(45).ifBlank { "Debit Transaction" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = phone?.friendlyName ?: tx.phoneId,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "-${formatCurrency(tx.amount)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RoseExpense
                                )
                                TextButton(
                                    onClick = { onOpenTransactionDetail(tx) },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Notes & Details →", fontSize = 11.sp, color = EmeraldPrimary)
                                }
                            }
                        }

                        val txSuggestions = suggestions[tx.transactionId].orEmpty()
                        val top = txSuggestions.firstOrNull()?.takeIf { it.confidence >= 0.25 }
                        if (top != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = EmeraldPrimary.copy(alpha = 0.14f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("suggested_category_${tx.transactionId}")
                                    .clickable { onQuickCategorize(tx.transactionId, top.categoryId) }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text(
                                        text = "★ Suggested: ${top.categoryName} · ${(top.confidence * 100).toInt()}% sure — tap to file",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldPrimary
                                    )
                                    Text(top.reason, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap to categorize now:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        if (categories.isEmpty()) {
                            Text(
                                text = "Create manual categories below first to categorize.",
                                fontSize = 11.sp,
                                color = AmberAccent
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val suggestedOrder = txSuggestions.map { it.categoryId }
                                categories.sortedBy { c -> suggestedOrder.indexOf(c.categoryId).let { i -> if (i < 0) Int.MAX_VALUE else i } }.forEach { cat ->
                                    val catColor = parseHexColor(cat.colorHex)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = catColor.copy(alpha = 0.12f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, catColor.copy(alpha = 0.35f)),
                                        modifier = Modifier.clickable {
                                            onQuickCategorize(tx.transactionId, cat.categoryId)
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = resolveCategoryIcon(cat.iconName),
                                                contentDescription = null,
                                                tint = catColor,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = cat.name,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = catColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (pageCount > 1) {
                item(key = "uncategorized_pager") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("uncategorized_pager"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { currentPage = (safePage - 1).coerceAtLeast(0) },
                            enabled = safePage > 0,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("‹ Previous", fontSize = 11.sp)
                        }
                        val firstShown = safePage * UNCATEGORIZED_PAGE_SIZE + 1
                        val lastShown = (firstShown + pageItems.size - 1).coerceAtLeast(firstShown)
                        Text(
                            text = "Page ${safePage + 1} of $pageCount · $firstShown–$lastShown of ${uncategorizedExpenses.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { currentPage = (safePage + 1).coerceAtMost(pageCount - 1) },
                            enabled = safePage < pageCount - 1,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("Next ›", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // How well the on-device learner is doing, and the opt-in auto-filing
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("category_learning_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Smart categorizing", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            text = if (suggestionStats.second > 0) {
                                "Learns from every debit you file. Right ${(suggestionStats.first * 100 / suggestionStats.second)}% of the time (${suggestionStats.second} choices)."
                            } else {
                                "Learns from every debit you file and from your category descriptions."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Auto-file a debit when the same payee was always filed in one category (3+ times).",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoFile, onCheckedChange = onAutoFileChange, modifier = Modifier.testTag("auto_file_switch"))
                }
            }
        }

        // Manual Categories Manager Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MANUAL EXPENSE CATEGORIES (${categories.size})",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Categories appear on notification action buttons and chips",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onAddCategory,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (categories.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Category, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("No manual categories yet", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Create custom categories for your debit transactions or tap quick starters below:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Triple("Sus", "storefront", "#EF4444"),
                                Triple("Food & Dining", "restaurant", "#F59E0B"),
                                Triple("Transportation", "directions_car", "#3B82F6"),
                                Triple("Internet & Bills", "bolt", "#8B5CF6"),
                                Triple("Rent", "home", "#EC4899"),
                                Triple("Family Support", "favorite", "#10B981")
                            ).forEach { (name, icon, color) ->
                                OutlinedButton(
                                    onClick = { onAddPresetCategory(name, icon, color) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("+ $name", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            items(categories, key = { it.categoryId }) { cat ->
                val catColor = parseHexColor(cat.colorHex)
                val catTransactions = allTransactions.filter { it.categoryId == cat.categoryId }
                val totalSpent = catTransactions.sumOf { it.amount }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(catColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = resolveCategoryIcon(cat.iconName),
                                    contentDescription = null,
                                    tint = catColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(cat.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (cat.description.isNotBlank()) {
                                    Text(
                                        text = cat.description,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Text(
                                        text = "No details yet — tap edit to describe it for the AI advisor",
                                        fontSize = 10.sp,
                                        color = AmberAccent
                                    )
                                }
                                Text(
                                    text = "${catTransactions.size} transactions · ${formatCurrency(totalSpent)} spent",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row {
                            IconButton(onClick = { onEditCategory(cat) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDeleteCategory(cat) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RoseExpense, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomeSourcesTabContent(
    incomeSources: List<IncomeSourceEntity>,
    phones: List<RegisteredPhoneEntity>,
    allTransactions: List<TransactionEntity>,
    incomeSourceMap: Map<String, IncomeSourceEntity>,
    onAssignPhone: (String, String?) -> Unit,
    onAddIncomeSource: () -> Unit,
    onEditIncomeSource: (IncomeSourceEntity) -> Unit,
    onDeleteIncomeSource: (IncomeSourceEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Info Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Income Sources by Phone", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "Incomes are automatically categorized based on the phone receiving the SMS. Once a phone is assigned to an income source, all incomes from that phone are categorized to it.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Phones and Assignments
        item {
            Text(
                text = "PHONE ASSIGNMENTS (${phones.size})",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (phones.isEmpty()) {
            item {
                Text("No registered phones yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(phones, key = { it.phoneId }) { phone ->
                val assignedSource = phone.assignedIncomeSourceId?.let { incomeSourceMap[it] }
                val phoneIncomes = allTransactions.filter {
                    it.phoneId == phone.phoneId && isIncomeCredit(it)
                }
                val totalIncomeAmount = phoneIncomes.sumOf { it.amount }
                val filedUnderSourceCount = assignedSource?.let { src -> phoneIncomes.count { it.categoryId == src.sourceId } } ?: 0

                var phoneMenuExpanded by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(phone.friendlyName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    if (phone.isCurrentDevice) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = EmeraldPrimary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                "THIS DEVICE",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldPrimary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${phone.modelName.ifBlank { "Device ID" }} · ${phone.phoneId}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "+${formatCurrency(totalIncomeAmount)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary
                                )
                                Text(
                                    text = "${phoneIncomes.size} credits · all time",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (assignedSource != null && phoneIncomes.isNotEmpty()) {
                                    Text(
                                        text = "$filedUnderSourceCount/${phoneIncomes.size} → ${assignedSource.name}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (filedUnderSourceCount == phoneIncomes.size) EmeraldPrimary else AmberAccent
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Income Source: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (assignedSource != null) {
                                    val srcColor = parseHexColor(assignedSource.colorHex)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = srcColor.copy(alpha = 0.15f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, srcColor.copy(alpha = 0.35f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                resolveCategoryIcon(assignedSource.iconName),
                                                contentDescription = null,
                                                tint = srcColor,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(assignedSource.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = srcColor)
                                        }
                                    }
                                } else {
                                    Text("⚠️ Not assigned", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
                                }
                            }

                            Box {
                                OutlinedButton(
                                    onClick = { phoneMenuExpanded = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text(if (assignedSource == null) "Assign Source" else "Change", fontSize = 11.sp)
                                }

                                DropdownMenu(
                                    expanded = phoneMenuExpanded,
                                    onDismissRequest = { phoneMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("None (Unassigned)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                        onClick = {
                                            onAssignPhone(phone.phoneId, null)
                                            phoneMenuExpanded = false
                                        }
                                    )
                                    incomeSources.forEach { src ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(resolveCategoryIcon(src.iconName), contentDescription = null, tint = parseHexColor(src.colorHex), modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(src.name)
                                                }
                                            },
                                            onClick = {
                                                onAssignPhone(phone.phoneId, src.sourceId)
                                                phoneMenuExpanded = false
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("+ Create New Income Source...", fontWeight = FontWeight.Bold, color = EmeraldPrimary) },
                                        onClick = {
                                            phoneMenuExpanded = false
                                            onAddIncomeSource()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Income Sources Management Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INCOME SOURCES (${incomeSources.size})",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(
                    onClick = onAddIncomeSource,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Source", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (incomeSources.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("No income sources created yet", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Add income sources like 'Retail Shop', 'Main Salary', or 'Freelancing' and link phones to them.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onAddIncomeSource,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+ Add First Income Source", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            items(incomeSources, key = { it.sourceId }) { src ->
                val srcColor = parseHexColor(src.colorHex)
                val assignedPhones = phones.filter { it.assignedIncomeSourceId == src.sourceId }
                // Every credit filed under this source (all credits of its assigned phones are filed here)
                val sourceCredits = allTransactions.filter { tx -> tx.categoryId == src.sourceId && isIncomeCredit(tx) }
                val totalIncomeAmount = sourceCredits.sumOf { it.amount }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(srcColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = resolveCategoryIcon(src.iconName),
                                    contentDescription = null,
                                    tint = srcColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(src.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (assignedPhones.isNotEmpty()) {
                                    Text(
                                        text = "📱 Linked: ${assignedPhones.joinToString { it.friendlyName }}",
                                        fontSize = 10.sp,
                                        color = EmeraldPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "No phone linked yet",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "Total Income: +${formatCurrency(totalIncomeAmount)} · ${sourceCredits.size} credits",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row {
                            IconButton(onClick = { onEditIncomeSource(src) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDeleteIncomeSource(src) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RoseExpense, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryEditDialog(
    category: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var description by remember { mutableStateOf(category?.description ?: "") }
    var selectedColor by remember { mutableStateOf(category?.colorHex ?: "#10B981") }
    var selectedIcon by remember { mutableStateOf(category?.iconName ?: "category") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Create Expense Category" else "Edit Expense Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    placeholder = { Text("e.g. Sus, Food & dining, Transport") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= CATEGORY_DESCRIPTION_MAX) description = it },
                    label = { Text("Expense type details") },
                    placeholder = { Text("What goes here? e.g. Weekly equb contribution to the shop group, fixed 2,000 ETB every Friday") },
                    supportingText = {
                        Text("The AI advisor uses this to understand and analyse this expense · ${description.length}/$CATEGORY_DESCRIPTION_MAX")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_description_input"),
                    minLines = 3,
                    maxLines = 5
                )

                Text("Pick Color:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORY_COLORS.forEach { (hex, _) ->
                        val color = parseHexColor(hex)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = hex }
                                .then(
                                    if (selectedColor == hex) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == hex) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Text("Pick Icon:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORY_ICONS.forEach { (iconKey, vector) ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedIcon == iconKey) EmeraldPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { selectedIcon = iconKey },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(vector, contentDescription = null, tint = if (selectedIcon == iconKey) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onSave(name.trim(), selectedColor, selectedIcon, description.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun IncomeSourceEditDialog(
    source: IncomeSourceEntity?,
    phones: List<RegisteredPhoneEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String?, String) -> Unit
) {
    var name by remember { mutableStateOf(source?.name ?: "") }
    var selectedColor by remember { mutableStateOf(source?.colorHex ?: "#10B981") }
    var selectedIcon by remember { mutableStateOf(source?.iconName ?: "storefront") }
    var selectedPhoneId by remember { mutableStateOf<String?>(phones.firstOrNull { it.assignedIncomeSourceId == source?.sourceId }?.phoneId) }
    var note by remember { mutableStateOf(source?.note ?: "") }
    var phoneDropdownOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (source == null) "Create Income Source" else "Edit Income Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Income Source Name") },
                    placeholder = { Text("e.g. Retail Shop, Main Salary, Freelancing") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Assign Phone (Optional):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Box {
                    OutlinedButton(
                        onClick = { phoneDropdownOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = selectedPhoneId?.let { pid -> phones.firstOrNull { it.phoneId == pid }?.friendlyName ?: pid } ?: "No phone linked (assign later)"
                        )
                    }
                    DropdownMenu(
                        expanded = phoneDropdownOpen,
                        onDismissRequest = { phoneDropdownOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None (Unassigned)") },
                            onClick = {
                                selectedPhoneId = null
                                phoneDropdownOpen = false
                            }
                        )
                        phones.forEach { phone ->
                            DropdownMenuItem(
                                text = { Text("${phone.friendlyName} (${phone.modelName})") },
                                onClick = {
                                    selectedPhoneId = phone.phoneId
                                    phoneDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                Text("Pick Color:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORY_COLORS.forEach { (hex, _) ->
                        val color = parseHexColor(hex)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = hex }
                                .then(
                                    if (selectedColor == hex) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == hex) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Text("Pick Icon:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORY_ICONS.forEach { (iconKey, vector) ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedIcon == iconKey) EmeraldPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { selectedIcon = iconKey },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(vector, contentDescription = null, tint = if (selectedIcon == iconKey) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onSave(name.trim(), selectedColor, selectedIcon, selectedPhoneId, note.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
