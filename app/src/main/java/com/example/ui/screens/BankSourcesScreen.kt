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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.FinancialSourceEntity
import com.example.data.entity.SourceType
import com.example.ui.components.formatCurrency
import com.example.ui.theme.BlueFintech
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.PurpleFintech
import com.example.ui.theme.RoseExpense
import com.example.ui.viewmodel.MainViewModel

@Composable
fun BankSourcesScreen(
    viewModel: MainViewModel,
    onNavigateToTransactionsForSource: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sourceMetrics by viewModel.sourceMetrics.collectAsStateWithLifecycle()
    val senders by viewModel.allSenderIdentifiers.collectAsStateWithLifecycle()
    var showAddSourceDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().testTag("bank_sources_screen")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "FINANCIAL SOURCES & INSTITUTIONS",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Track balances, volume, and SMS transactions individually for Ethiopian banks and mobile wallets. Extensible for new banks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(sourceMetrics, key = { it.source.sourceId }) { metric ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToTransactionsForSource(metric.source.sourceId) }
                        .testTag("source_card_${metric.source.sourceId}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = BlueFintech.copy(alpha = 0.15f),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalance,
                                            contentDescription = null,
                                            tint = BlueFintech,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = metric.source.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${metric.source.type.name} • ${metric.source.institutionName}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (metric.source.isActive) EmeraldPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline
                            ) {
                                Text(
                                    text = if (metric.source.isActive) "ACTIVE" else "INACTIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (metric.source.isActive) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Balance card if available
                        if (metric.lastKnownBalance != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Latest Known Balance:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = formatCurrency(metric.lastKnownBalance),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = EmeraldPrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Metrics 3 columns
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Total Income", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("+${formatCurrency(metric.income)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                            }
                            Column {
                                Text("Total Expenses", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("-${formatCurrency(metric.expenses)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RoseExpense)
                            }
                            Column {
                                Text("Internal Transfers", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatCurrency(metric.internalTransfers), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PurpleFintech)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Transactions", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${metric.transactionCount}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Configured SMS Senders for this source
                        val sendersForThisSource = senders.filter { it.sourceId == metric.source.sourceId }
                        if (sendersForThisSource.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Configured SMS Senders:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                sendersForThisSource.take(4).forEach { ident ->
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = "${ident.senderName ?: ident.senderNumber} (P${ident.priority})",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddSourceDialog = true },
            containerColor = EmeraldPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_source_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Financial Source")
        }
    }

    if (showAddSourceDialog) {
        var sourceId by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var institution by remember { mutableStateOf("") }
        var senderIds by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddSourceDialog = false },
            title = { Text("Add Financial Institution") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (sourceId.isEmpty()) {
                                sourceId = it.lowercase().replace(" ", "_")
                            }
                        },
                        label = { Text("Display Name (e.g. Awash Bank)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = sourceId,
                        onValueChange = { sourceId = it },
                        label = { Text("Source ID (e.g. awash_bank)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = institution,
                        onValueChange = { institution = it },
                        label = { Text("Institution Full Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = senderIds,
                        onValueChange = { senderIds = it },
                        label = { Text("Sender Names/Shortcodes (comma-separated)") },
                        placeholder = { Text("awash,awashbank,888") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && sourceId.isNotBlank()) {
                            viewModel.addFinancialSource(
                                sourceId = sourceId,
                                name = name,
                                type = SourceType.BANK,
                                institution = institution,
                                senderIds = senderIds,
                                colorHex = "#3B82F6"
                            )
                            showAddSourceDialog = false
                        }
                    }
                ) {
                    Text("Add Institution")
                }
            },
            dismissButton = {
                Button(onClick = { showAddSourceDialog = false }) { Text("Cancel") }
            }
        )
    }
}
