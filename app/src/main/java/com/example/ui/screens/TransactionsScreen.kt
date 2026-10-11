package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransactionType
import com.example.ui.components.BankFilterSelectorBar
import com.example.ui.components.MultiPhoneSelectorBar
import com.example.ui.components.TransactionCard
import com.example.ui.components.TransactionDetailDialog
import com.example.ui.components.formatCurrency
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.PurpleFintech
import com.example.ui.theme.RoseExpense
import com.example.ui.viewmodel.MainViewModel

@Composable
fun TransactionsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val transactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val phones by viewModel.allPhones.collectAsStateWithLifecycle()
    val sources by viewModel.allSources.collectAsStateWithLifecycle()
    val categories by viewModel.allCategories.collectAsStateWithLifecycle()

    val selectedPhoneId by viewModel.selectedPhoneId.collectAsStateWithLifecycle()
    val selectedSourceId by viewModel.selectedSourceId.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedTransactionType.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var selectedTransactionForDetail by remember { mutableStateOf<TransactionEntity?>(null) }

    val phoneMap = remember(phones) { phones.associateBy { it.phoneId } }
    val categoryMap = remember(categories) { categories.associateBy { it.categoryId } }

    val typeFilters = listOf(
        null to "All Types",
        TransactionType.INCOME to "Income",
        TransactionType.EXPENSE to "Expenses",
        TransactionType.INTERNAL_TRANSFER to "Internal Transfers",
        TransactionType.TRANSFER to "External Transfers"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("transactions_screen")
    ) {
        // Search TextField
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("Search by reference, party, or description...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_transactions_input")
            )
        }

        // Multi-phone Selector & Bank Filter
        MultiPhoneSelectorBar(
            phones = phones,
            selectedPhoneId = selectedPhoneId,
            onPhoneSelected = { viewModel.selectedPhoneId.value = it }
        )

        BankFilterSelectorBar(
            sources = sources,
            selectedSourceId = selectedSourceId,
            onSourceSelected = { viewModel.selectedSourceId.value = it }
        )

        // Transaction Type Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            typeFilters.forEach { (type, label) ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { viewModel.selectedTransactionType.value = type },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (type) {
                            TransactionType.INCOME -> EmeraldPrimary.copy(alpha = 0.2f)
                            TransactionType.EXPENSE -> RoseExpense.copy(alpha = 0.2f)
                            TransactionType.INTERNAL_TRANSFER -> PurpleFintech.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                    modifier = Modifier.testTag("type_filter_${type?.name ?: "ALL"}")
                )
            }
        }

        // Transactions List Count Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${transactions.size} TRANSACTIONS FOUND",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val filteredSum = remember(transactions) {
                transactions.sumOf { tx ->
                    when (tx.transactionType) {
                        TransactionType.INCOME, TransactionType.DEPOSIT -> tx.amount
                        TransactionType.EXPENSE, TransactionType.WITHDRAWAL, TransactionType.FEE -> -tx.amount
                        else -> 0.0
                    }
                }
            }
            Text(
                text = "Net: ${formatCurrency(filteredSum)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (filteredSum >= 0) EmeraldPrimary else RoseExpense
            )
        }

        // Transactions LazyColumn
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (transactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No matching transactions found.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(transactions, key = { it.transactionId }) { tx ->
                    val phoneName = phoneMap[tx.phoneId]?.friendlyName ?: tx.phoneId
                    val categoryName = categoryMap[tx.categoryId]?.name ?: ""
                    TransactionCard(
                        transaction = tx,
                        phoneName = phoneName,
                        categoryName = categoryName,
                        onClick = { selectedTransactionForDetail = tx }
                    )
                }
            }
        }
    }

    // Detail Dialog
    selectedTransactionForDetail?.let { tx ->
        val rawSms by produceState<com.example.data.entity.RawSmsEntity?>(initialValue = null, tx.rawSmsId) {
            value = viewModel.getRawSms(tx.rawSmsId)
        }
        val phoneName = phoneMap[tx.phoneId]?.friendlyName ?: tx.phoneId
        TransactionDetailDialog(
            transaction = tx,
            rawSms = rawSms,
            phoneName = phoneName,
            categories = categories,
            onDismiss = { selectedTransactionForDetail = null },
            onSaveCategoryAndNotes = { newCatId, notes ->
                viewModel.updateTransactionCategory(tx.transactionId, newCatId, notes)
            }
        )
    }
}
