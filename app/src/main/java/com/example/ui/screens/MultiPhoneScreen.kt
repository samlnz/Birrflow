package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.RegisteredPhoneEntity
import com.example.ui.components.CloudSyncCard
import com.example.ui.components.formatCurrency
import com.example.ui.theme.BlueFintech
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.PurpleFintech
import com.example.ui.theme.RoseExpense
import com.example.ui.viewmodel.MainViewModel

@Composable
fun MultiPhoneScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val phones by viewModel.allPhones.collectAsStateWithLifecycle()
    val phoneMetrics by viewModel.phoneMetrics.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var phoneToEdit by remember { mutableStateOf<RegisteredPhoneEntity?>(null) }

    val metricMap = remember(phoneMetrics) { phoneMetrics.associateBy { it.phone.phoneId } }
    val cloudState by viewModel.cloudSyncState.collectAsStateWithLifecycle()

    val uniquePhones = remember(phones) {
        phones
            .sortedByDescending { it.isCurrentDevice }
            .distinctBy { it.phoneId }
            .distinctBy { "${it.friendlyName.lowercase().trim()}|${it.modelName.lowercase().trim()}" }
    }

    Box(modifier = modifier.fillMaxSize().testTag("multi_phone_screen")) {
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
                            text = "MULTI-PHONE DEVICE IDENTITY",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "BirrFlow automatically streams new bank alerts and transactions across all your connected phones. Tap 'Pull New Data From All Phones' to instantly fetch new transactions from your other devices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                viewModel.syncAllDevicesData()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("manual_sync_all_phones_button")
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Pull New Data From All Phones", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                CloudSyncCard(
                    state = cloudState,
                    phonesInGroup = phones.size,
                    onSyncDevice = { viewModel.syncDevice(it) },
                    onUnsyncDevice = { viewModel.unsyncDevice(it) },
                    onSyncAllDevices = { viewModel.syncAllDevices() },
                    onTriggerUpload = { viewModel.triggerSyncAndUpload() },
                    onUnlink = { viewModel.unlinkSync() },
                    onClearMessage = { viewModel.clearSyncMessage() }
                )
            }

            items(uniquePhones, key = { it.phoneId }) { phone ->
                val metric = metricMap[phone.phoneId]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("phone_card_${phone.phoneId}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = true)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = EmeraldPrimary.copy(alpha = 0.15f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PhoneAndroid,
                                            contentDescription = null,
                                            tint = EmeraldPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f, fill = true)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = phone.friendlyName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (phone.isCurrentDevice) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = EmeraldPrimary.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "THIS DEVICE",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = EmeraldPrimary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = BlueFintech.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "LIVE SYNCED",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BlueFintech,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${phone.phoneId} • ${phone.modelName}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { phoneToEdit = phone }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit phone", modifier = Modifier.size(18.dp))
                            }
                        }

                        if (metric != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Income", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("+${formatCurrency(metric.income)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Expenses", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("-${formatCurrency(metric.expenses)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RoseExpense, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Internal Transfers", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(formatCurrency(metric.internalTransfers), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PurpleFintech, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Transactions", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${metric.transactionCount}", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = EmeraldPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_phone_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Firebase Project Credentials")
        }
    }

    // Configure Cloud Database Credentials Dialog (Railway or Firebase)
    if (showAddDialog) {
        var selectedTab by remember { mutableStateOf(if (!cloudState.savedDatabaseUrl.isNullOrBlank()) 0 else 0) }
        var railwayUrl by remember(cloudState.savedDatabaseUrl) { mutableStateOf(cloudState.savedDatabaseUrl.orEmpty()) }
        var pid by remember(cloudState.savedProjectId) { mutableStateOf(cloudState.savedProjectId.orEmpty()) }
        var apiKey by remember(cloudState.savedApiKey) { mutableStateOf(cloudState.savedApiKey.orEmpty()) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = EmeraldPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Cloud Database Connection")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        androidx.compose.material3.FilterChip(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            label = { Text("Railway (19,000+ Tx)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f))
                        )
                        androidx.compose.material3.FilterChip(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            label = { Text("Firebase Mesh", fontSize = 12.sp) }
                        )
                    }

                    if (selectedTab == 0) {
                        Text(
                            "Paste your Railway PostgreSQL database public URL (e.g. postgresql://postgres:pass@host:5432/railway). Recommended for high volume (19,000+ transactions).",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = railwayUrl,
                            onValueChange = { railwayUrl = it },
                            label = { Text("Railway Database URL") },
                            placeholder = { Text("postgresql://postgres:pass@junction.proxy.rlwy.net:5432/railway") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("fab_railway_url_input")
                        )
                    } else {
                        Text(
                            "Enter your Firebase Project ID and Web API Key to connect this device to Firebase Firestore.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = pid,
                            onValueChange = { pid = it },
                            label = { Text("Firebase Project ID") },
                            placeholder = { Text("e.g. birrflow-521a1") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("fab_firebase_pid_input")
                        )
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Firebase Web API Key") },
                            placeholder = { Text("AIzaSy...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("fab_firebase_key_input")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedTab == 0 && railwayUrl.isNotBlank()) {
                            viewModel.configureRailway(railwayUrl.trim())
                            showAddDialog = false
                        } else if (selectedTab == 1 && pid.isNotBlank() && apiKey.isNotBlank()) {
                            viewModel.configureFirebase(pid.trim(), apiKey.trim(), "com.aistudio.banksms.kzetqw")
                            showAddDialog = false
                        }
                    },
                    enabled = (selectedTab == 0 && railwayUrl.isNotBlank()) || (selectedTab == 1 && pid.isNotBlank() && apiKey.isNotBlank()),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    modifier = Modifier.testTag("confirm_save_firebase_button")
                ) {
                    Text(if (selectedTab == 0) "Save & Connect Railway" else "Save & Connect Firebase")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Edit Phone Dialog
    phoneToEdit?.let { phone ->
        var editFriendlyName by remember { mutableStateOf(phone.friendlyName) }
        var editModel by remember { mutableStateOf(phone.modelName) }
        var editNum by remember { mutableStateOf(phone.phoneNumber) }

        AlertDialog(
            onDismissRequest = { phoneToEdit = null },
            title = { Text("Edit Device Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Device ID: ${phone.phoneId} (Permanent Identity)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = editFriendlyName,
                        onValueChange = { editFriendlyName = it },
                        label = { Text("Friendly Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editModel,
                        onValueChange = { editModel = it },
                        label = { Text("Model") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editNum,
                        onValueChange = { editNum = it },
                        label = { Text("SIM Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updatePhone(phone.copy(friendlyName = editFriendlyName, modelName = editModel, phoneNumber = editNum))
                        phoneToEdit = null
                    }
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                Button(onClick = { phoneToEdit = null }) { Text("Cancel") }
            }
        )
    }
}
