package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.ai.SupportedGeminiModels
import com.example.ai.GeminiConnectionStatus
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.FinancialSourceEntity
import com.example.data.entity.InternalTransferConfigEntity
import com.example.data.entity.MatchMethod
import com.example.data.entity.RawSmsEntity
import com.example.data.entity.SenderIdentifierType
import com.example.data.entity.SmsProcessingStatus
import com.example.data.entity.SmsSenderIdentifierEntity
import com.example.data.entity.VerificationStatus
import com.example.parser.SenderTestResult
import com.example.ui.components.formatCurrency
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BlueFintech
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.PurpleFintech
import com.example.ui.theme.RoseExpense
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminHubScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val unknownSenders by viewModel.unknownSenderSms.collectAsStateWithLifecycle()

    val tabs = listOf(
        "Sender Targeting",
        "Unknown Senders (${unknownSenders.size})",
        "Parser Testing",
        "Internal Transfers",
        "Failed Formats",
        "Categories",
        "Audit Trail",
        "Gemini AI Settings"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_hub_screen")
    ) {
        // Admin header banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val isScanning by viewModel.isScanningInbox.collectAsStateWithLifecycle()

            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("ADMINISTRATOR CONTROL HUB", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Text("Configure SMS senders, test matching rules, and resolve unknown alerts.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(
                    onClick = { viewModel.scanPhoneInbox(context) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    if (isScanning) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan Inbox", fontSize = 11.sp)
                    }
                }
            }
        }

        // Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 12.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        when (selectedTab) {
            0 -> SenderTargetingTab(viewModel)
            1 -> UnknownSendersTab(viewModel)
            2 -> ParserTestingTab(viewModel)
            3 -> InternalTransfersTab(viewModel)
            4 -> FailedFormatsTab(viewModel)
            5 -> CategoriesTab(viewModel)
            6 -> AuditTrailTab(viewModel)
            7 -> GeminiSettingsAdminTab(viewModel)
        }
    }
}

/**
 * 1. SMS SENDER TARGETING TAB
 * Displays configured SMS sender names/numbers with priority, matching rules, and interactive sender testing tool.
 */
@Composable
private fun SenderTargetingTab(viewModel: MainViewModel) {
    val senders by viewModel.allSenderIdentifiers.collectAsStateWithLifecycle()
    val sources by viewModel.allSources.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var senderToEdit by remember { mutableStateOf<SmsSenderIdentifierEntity?>(null) }
    var showTestingTool by remember { mutableStateOf(false) }

    val sourceMap = remember(sources) { sources.associateBy { it.sourceId } }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SMS SENDER IDENTIFIERS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Map telecom sender names/shortcodes to financial institutions by priority.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { showTestingTool = !showTestingTool },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showTestingTool) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("toggle_sender_test_tool_button")
                    ) {
                        Text(
                            text = if (showTestingTool) "Hide Test" else "Test Sender",
                            fontSize = 11.sp,
                            color = if (showTestingTool) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Sender Testing Tool
            if (showTestingTool) {
                item {
                    SenderTestCard(viewModel)
                }
            }

            // Senders List Table
            items(senders, key = { it.id }) { ident ->
                val institutionName = sourceMap[ident.sourceId]?.name ?: ident.sourceId.uppercase()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sender_identifier_item_${ident.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = EmeraldPrimary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Priority ${ident.priority}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = institutionName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Sender: ${ident.senderName ?: ident.senderNumber ?: "Any"}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "Type: ${ident.identifierType.name} • Match: ${ident.matchMethod.name}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (ident.description.isNotEmpty()) {
                                Text(
                                    text = ident.description,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = ident.isActive,
                                onCheckedChange = { viewModel.updateSenderIdentifier(ident.copy(isActive = it)) }
                            )
                            IconButton(onClick = { senderToEdit = ident }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit sender", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.deleteSenderIdentifier(ident) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete sender", tint = RoseExpense, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = EmeraldPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).testTag("add_sender_identifier_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add SMS Sender")
        }
    }

    // Add Sender Dialog
    if (showAddDialog) {
        SenderIdentifierEditDialog(
            sources = sources,
            initialIdentifier = null,
            onDismiss = { showAddDialog = false },
            onSave = { srcId, name, num, type, method, priority, desc ->
                viewModel.addSenderIdentifier(srcId, name, num, type, method, priority, desc)
                showAddDialog = false
            }
        )
    }

    // Edit Sender Dialog
    senderToEdit?.let { ident ->
        SenderIdentifierEditDialog(
            sources = sources,
            initialIdentifier = ident,
            onDismiss = { senderToEdit = null },
            onSave = { srcId, name, num, type, method, priority, desc ->
                viewModel.updateSenderIdentifier(
                    ident.copy(
                        sourceId = srcId,
                        senderName = name,
                        senderNumber = num,
                        identifierType = type,
                        matchMethod = method,
                        priority = priority,
                        description = desc
                    )
                )
                senderToEdit = null
            }
        )
    }
}

@Composable
private fun SenderTestCard(viewModel: MainViewModel) {
    val nameInput by viewModel.testSenderName.collectAsStateWithLifecycle()
    val numInput by viewModel.testSenderNumber.collectAsStateWithLifecycle()
    val testResult by viewModel.testSenderResult.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sender_test_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Test Sender Identification", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { viewModel.testSenderName.value = it },
                    label = { Text("Sender Name") },
                    placeholder = { Text("e.g. CBE or telebirr") },
                    modifier = Modifier.weight(1f).testTag("test_sender_name_input")
                )
                OutlinedTextField(
                    value = numInput,
                    onValueChange = { viewModel.testSenderNumber.value = it },
                    label = { Text("Shortcode/Num") },
                    placeholder = { Text("e.g. 127 or 889") },
                    modifier = Modifier.weight(1f).testTag("test_sender_num_input")
                )
            }

            Button(
                onClick = { viewModel.runSenderTest() },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.fillMaxWidth().testTag("execute_sender_test_button")
            ) {
                Text("Evaluate Sender")
            }

            testResult?.let { res ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (res.isApplicable) EmeraldPrimary.copy(alpha = 0.15f) else RoseExpense.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (res.isApplicable) "Matched: ${res.matchedInstitutionName}" else "No Match",
                                fontWeight = FontWeight.Bold,
                                color = if (res.isApplicable) EmeraldPrimary else RoseExpense
                            )
                            Text(
                                text = "Applicable: ${if (res.isApplicable) "YES" else "NO"}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (res.isApplicable) EmeraldPrimary else RoseExpense
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = res.reason, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/**
 * 2. UNKNOWN SENDERS TAB
 * Inspect messages received from unconfigured senders with a 1-click "Create Sender Identifier" workflow.
 */
@Composable
private fun UnknownSendersTab(viewModel: MainViewModel) {
    val unknownSenders by viewModel.unknownSenderSms.collectAsStateWithLifecycle()
    val sources by viewModel.allSources.collectAsStateWithLifecycle()

    var smsToCreateSenderFor by remember { mutableStateOf<RawSmsEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("UNKNOWN SMS SENDERS (${unknownSenders.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Messages from senders not recognized by any financial institution.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (unknownSenders.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No unknown sender messages in the queue! All received senders are matched.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(unknownSenders, key = { it.rawSmsId }) { raw ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unknown_sender_card_${raw.rawSmsId}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.HelpOutline, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Sender: ${raw.sender}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AmberAccent.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "UNKNOWN_SENDER",
                                        color = AmberAccent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = raw.body,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                fontSize = 11.sp
                            )

                            val dateFormatted = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(raw.receivedTimestamp))
                            Text(
                                text = "Device: ${raw.phoneId} • Received: $dateFormatted",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { smsToCreateSenderFor = raw },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("create_sender_button_${raw.rawSmsId}")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Create Sender Identifier", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal to create Sender Identifier from Unknown SMS and reprocess
    smsToCreateSenderFor?.let { raw ->
        var selectedSourceId by remember { mutableStateOf(sources.firstOrNull()?.sourceId ?: "cbe") }
        var senderName by remember { mutableStateOf(raw.sender) }
        var senderNumber by remember { mutableStateOf(if (raw.sender.matches(Regex("^[0-9+]+$"))) raw.sender else "") }
        var matchMethod by remember { mutableStateOf(MatchMethod.CASE_INSENSITIVE) }
        var priorityStr by remember { mutableStateOf("1") }
        var methodMenuExpanded by remember { mutableStateOf(false) }
        var sourceMenuExpanded by remember { mutableStateOf(false) }

        val selectedSource = sources.firstOrNull { it.sourceId == selectedSourceId }

        AlertDialog(
            onDismissRequest = { smsToCreateSenderFor = null },
            title = { Text("Configure Sender Identifier") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Associate sender '${raw.sender}' with a financial institution and reprocess.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Financial Source Dropdown
                    Box {
                        OutlinedTextField(
                            value = selectedSource?.name ?: selectedSourceId,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Financial Institution") },
                            modifier = Modifier.fillMaxWidth().clickable { sourceMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = sourceMenuExpanded,
                            onDismissRequest = { sourceMenuExpanded = false }
                        ) {
                            sources.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.name) },
                                    onClick = {
                                        selectedSourceId = s.sourceId
                                        sourceMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = senderName,
                        onValueChange = { senderName = it },
                        label = { Text("Sender Name") },
                        modifier = Modifier.fillMaxWidth().testTag("unknown_to_sender_name_input")
                    )

                    OutlinedTextField(
                        value = senderNumber,
                        onValueChange = { senderNumber = it },
                        label = { Text("Sender Number (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Match Method
                    Box {
                        OutlinedTextField(
                            value = matchMethod.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Match Method") },
                            modifier = Modifier.fillMaxWidth().clickable { methodMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = methodMenuExpanded,
                            onDismissRequest = { methodMenuExpanded = false }
                        ) {
                            MatchMethod.values().forEach { method ->
                                DropdownMenuItem(
                                    text = { Text(method.name) },
                                    onClick = {
                                        matchMethod = method
                                        methodMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = priorityStr,
                        onValueChange = { priorityStr = it },
                        label = { Text("Priority (1 = Highest)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val prio = priorityStr.toIntOrNull() ?: 1
                        viewModel.createSenderFromUnknownSms(
                            sourceId = selectedSourceId,
                            senderName = senderName,
                            senderNumber = senderNumber.ifBlank { null },
                            matchMethod = matchMethod,
                            priority = prio,
                            reprocessRawSmsId = raw.rawSmsId
                        )
                        smsToCreateSenderFor = null
                    },
                    modifier = Modifier.testTag("save_and_reprocess_sender_button")
                ) {
                    Text("Save & Reprocess SMS")
                }
            },
            dismissButton = {
                Button(onClick = { smsToCreateSenderFor = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * 3. PARSER TESTING LAB TAB
 */
@Composable
private fun ParserTestingTab(viewModel: MainViewModel) {
    val testInput by viewModel.testParserInput.collectAsStateWithLifecycle()
    val testSender by viewModel.testSelectedSender.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "PARSER TESTING LAB",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Test incoming SMS through both Sender Identification and SMS Format Regex matching.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            OutlinedTextField(
                value = testSender,
                onValueChange = { viewModel.testSelectedSender.value = it },
                label = { Text("Sender Name or Shortcode (e.g. telebirr, CBE, 127, BOA)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("admin_test_sender_input")
            )
        }

        item {
            OutlinedTextField(
                value = testInput,
                onValueChange = { viewModel.testParserInput.value = it },
                label = { Text("Paste Raw Bank SMS Here") },
                placeholder = { Text("e.g. Dear Nahom, you have received 1,500.00 ETB from...") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_test_sms_input")
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.runParserTest() },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    modifier = Modifier.testTag("run_parser_test_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run Pipeline Test")
                }

                Button(
                    onClick = {
                        viewModel.testSelectedSender.value = "CBE"
                        viewModel.testParserInput.value =
                            "Dear Customer, your CBE Account 1000******123 has been Credited with ETB 25,000.00 on 25/09/2026. Ref: FT99887711. Available Balance: ETB 95,000.00. Commercial Bank of Ethiopia"
                    }
                ) {
                    Text("Load Sample")
                }
            }
        }

        testResult?.let { res ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("parser_test_results_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Pipeline Evaluation", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (res.verificationStatus == VerificationStatus.VALID) EmeraldPrimary.copy(alpha = 0.2f) else AmberAccent.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = if (res.isUnknownSender) "UNKNOWN_SENDER" else res.verificationStatus.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (res.isUnknownSender) RoseExpense else if (res.verificationStatus == VerificationStatus.VALID) EmeraldPrimary else AmberAccent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        DetailItem("Sender Status", if (res.isUnknownSender) "Unrecognized (Rejected)" else "Matched Financial Institution")
                        DetailItem("Detected Institution", res.detectedSourceName ?: "None (Unrecognized)")
                        DetailItem("Format Module", res.formatName ?: "None")
                        DetailItem("Confidence", "${(res.confidence * 100).toInt()}%")
                        DetailItem("Transaction Type", res.transactionType.name)
                        DetailItem("Amount", res.amount?.let { formatCurrency(it) } ?: "NULL")
                        DetailItem("Party", res.sender ?: res.recipient ?: res.merchant ?: "None")
                        DetailItem("Reference ID", res.referenceNumber ?: "NULL")
                        DetailItem("Balance After", res.balanceAfter?.let { formatCurrency(it) } ?: "NULL")

                        if (res.validationErrors.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Validation Note: ${res.validationErrors.joinToString("; ")}",
                                color = if (res.isUnknownSender) RoseExpense else AmberAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 4. INTERNAL TRANSFERS TAB
 */
@Composable
private fun InternalTransfersTab(viewModel: MainViewModel) {
    val configs by viewModel.allInternalConfigs.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                Text(
                    text = "INTERNAL TRANSFER IDENTITIES",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Identified solely by Receiver / Counterparty Name. Transactions with these names are classified as INTERNAL_TRANSFER and excluded from Net Cash Flow calculation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(configs, key = { it.configId }) { config ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("internal_config_${config.configId}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(config.identityName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = PurpleFintech.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "Receiver Match",
                                        fontSize = 10.sp,
                                        color = PurpleFintech,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (config.aliases.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Name Variations: ${config.aliases}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (config.description.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Note: ${config.description}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = config.isActive,
                                onCheckedChange = { viewModel.updateInternalConfig(config.copy(isActive = it)) }
                            )
                            IconButton(onClick = { viewModel.deleteInternalConfig(config) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RoseExpense)
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = PurpleFintech,
            modifier = Modifier.align(Alignment.BottomEnd).testTag("add_internal_transfer_config_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Receiver Identity")
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var aliases by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Internal Transfer Identity") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the receiver / sender name on your other accounts (e.g. Dagim). All debit/credit SMS matching this name will be classified as internal transfers.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Receiver / Counterparty Name") },
                        placeholder = { Text("e.g. Dagim") },
                        modifier = Modifier.fillMaxWidth().testTag("internal_name_input")
                    )
                    OutlinedTextField(
                        value = aliases,
                        onValueChange = { aliases = it },
                        label = { Text("Name Variations & Aliases (Optional)") },
                        placeholder = { Text("dagim tadele worku, Dagimtadeleworku, DAGIM") },
                        modifier = Modifier.fillMaxWidth().testTag("internal_aliases_input")
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Note / Description (Optional)") },
                        placeholder = { Text("Secondary account / personal wallet") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.addInternalConfig(name, aliases, desc)
                            showAddDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_internal_config_button")
                ) {
                    Text("Add Identity")
                }
            },
            dismissButton = {
                Button(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * 5. FAILED FORMATS TAB
 */
@Composable
private fun FailedFormatsTab(viewModel: MainViewModel) {
    val failedSmsList by viewModel.failedOrUnknownSms.collectAsStateWithLifecycle()
    val formatErrors = remember(failedSmsList) {
        failedSmsList.filter { it.processingStatus == SmsProcessingStatus.UNKNOWN_FORMAT || it.processingStatus == SmsProcessingStatus.FAILED }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("FAILED FORMAT MESSAGES (${formatErrors.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Messages with known sender but unparsed text body.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (formatErrors.isNotEmpty()) {
                Button(
                    onClick = { viewModel.reprocessAllFailed() },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reprocess All", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (formatErrors.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No format error messages in review queue!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(formatErrors, key = { it.rawSmsId }) { raw ->
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("failed_sms_${raw.rawSmsId}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Sender: ${raw.sender} | Device: ${raw.phoneId}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = RoseExpense.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = raw.processingStatus.name,
                                        color = RoseExpense,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = raw.body,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                fontSize = 11.sp
                            )
                            if (raw.processingError != null) {
                                Text(
                                    text = "Reason: ${raw.processingError}",
                                    fontSize = 10.sp,
                                    color = AmberAccent
                                )
                            }
                            Button(
                                onClick = { viewModel.reprocessSms(raw.rawSmsId) },
                                modifier = Modifier.align(Alignment.End),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Reprocess Message", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 6. CATEGORIES TAB
 */
@Composable
private fun CategoriesTab(viewModel: MainViewModel) {
    val categories by viewModel.allCategories.collectAsStateWithLifecycle()
    var showAddCatDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                Text("TRANSACTION CATEGORIES", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Manage user-controlled financial categories.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            items(categories, key = { it.categoryId }) { cat ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(cat.name, fontWeight = FontWeight.Medium)
                        if (cat.isCustom) {
                            IconButton(onClick = { viewModel.deleteCategory(cat) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RoseExpense)
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddCatDialog = true },
            containerColor = EmeraldPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).testTag("add_category_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Category")
        }
    }

    if (showAddCatDialog) {
        var catName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCatDialog = false },
            title = { Text("New Category") },
            text = {
                OutlinedTextField(
                    value = catName,
                    onValueChange = { catName = it },
                    label = { Text("Category Name") },
                    modifier = Modifier.fillMaxWidth().testTag("new_category_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (catName.isNotBlank()) {
                            viewModel.addCategory(catName, "category", "#10B981")
                            showAddCatDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_category_button")
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                Button(onClick = { showAddCatDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * 7. AUDIT TRAIL TAB
 */
@Composable
private fun AuditTrailTab(viewModel: MainViewModel) {
    val auditLogs by viewModel.recentAuditLogs.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("SECURITY & SYSTEM AUDIT TRAIL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Immutable log of incoming SMS ingestion, duplicate detections, parser executions, and sender additions.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        items(auditLogs, key = { it.logId }) { log ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(log.action, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmeraldPrimary)
                        val dateFormatted = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date(log.timestamp))
                        Text(dateFormatted, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(log.details, fontSize = 11.sp)
                    if (log.phoneId != null || log.rawSmsId != null) {
                        Text(
                            text = "Device: ${log.phoneId ?: "N/A"} | SMS Ref: ${log.rawSmsId ?: "N/A"}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SenderIdentifierEditDialog(
    sources: List<FinancialSourceEntity>,
    initialIdentifier: SmsSenderIdentifierEntity?,
    onDismiss: () -> Unit,
    onSave: (sourceId: String, name: String?, number: String?, type: SenderIdentifierType, method: MatchMethod, priority: Int, description: String) -> Unit
) {
    var selectedSourceId by remember { mutableStateOf(initialIdentifier?.sourceId ?: sources.firstOrNull()?.sourceId ?: "cbe") }
    var senderName by remember { mutableStateOf(initialIdentifier?.senderName ?: "") }
    var senderNumber by remember { mutableStateOf(initialIdentifier?.senderNumber ?: "") }
    var identifierType by remember { mutableStateOf(initialIdentifier?.identifierType ?: SenderIdentifierType.NAME) }
    var matchMethod by remember { mutableStateOf(initialIdentifier?.matchMethod ?: MatchMethod.CASE_INSENSITIVE) }
    var priorityStr by remember { mutableStateOf((initialIdentifier?.priority ?: 1).toString()) }
    var desc by remember { mutableStateOf(initialIdentifier?.description ?: "") }

    var sourceMenuExpanded by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var methodMenuExpanded by remember { mutableStateOf(false) }

    val selectedSource = sources.firstOrNull { it.sourceId == selectedSourceId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialIdentifier == null) "Add SMS Sender Identifier" else "Edit Sender Identifier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Source selector
                Box {
                    OutlinedTextField(
                        value = selectedSource?.name ?: selectedSourceId,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Financial Institution") },
                        modifier = Modifier.fillMaxWidth().clickable { sourceMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = sourceMenuExpanded,
                        onDismissRequest = { sourceMenuExpanded = false }
                    ) {
                        sources.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.name) },
                                onClick = {
                                    selectedSourceId = s.sourceId
                                    sourceMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = senderName,
                    onValueChange = { senderName = it },
                    label = { Text("Sender Name (e.g. CBE or telebirr)") },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_sender_name_input")
                )

                OutlinedTextField(
                    value = senderNumber,
                    onValueChange = { senderNumber = it },
                    label = { Text("Sender Number / Shortcode (e.g. 127)") },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_sender_number_input")
                )

                // Match Method dropdown
                Box {
                    OutlinedTextField(
                        value = matchMethod.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Match Method") },
                        modifier = Modifier.fillMaxWidth().clickable { methodMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = methodMenuExpanded,
                        onDismissRequest = { methodMenuExpanded = false }
                    ) {
                        MatchMethod.values().forEach { method ->
                            DropdownMenuItem(
                                text = { Text(method.name) },
                                onClick = {
                                    matchMethod = method
                                    methodMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = priorityStr,
                    onValueChange = { priorityStr = it },
                    label = { Text("Priority (1 = Highest)") },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_sender_priority_input")
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = priorityStr.toIntOrNull() ?: 1
                    onSave(
                        selectedSourceId,
                        senderName.ifBlank { null },
                        senderNumber.ifBlank { null },
                        identifierType,
                        matchMethod,
                        p,
                        desc
                    )
                },
                modifier = Modifier.testTag("confirm_save_sender_identifier_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GeminiSettingsAdminTab(viewModel: MainViewModel) {
    val config by viewModel.geminiConfig.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTestingConnection.collectAsStateWithLifecycle()
    val testResult by viewModel.connectionTestResult.collectAsStateWithLifecycle()
    val usageLogs by viewModel.advisorUsageLogs.collectAsStateWithLifecycle()

    var keyInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 60.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Gemini API Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (config.status) {
                                GeminiConnectionStatus.CONNECTED -> EmeraldPrimary.copy(alpha = 0.15f)
                                GeminiConnectionStatus.NOT_CONFIGURED -> RoseExpense.copy(alpha = 0.15f)
                                else -> AmberAccent.copy(alpha = 0.15f)
                            }
                        ) {
                            Text(
                                text = config.status.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (config.status) {
                                    GeminiConnectionStatus.CONNECTED -> EmeraldPrimary
                                    GeminiConnectionStatus.NOT_CONFIGURED -> RoseExpense
                                    else -> AmberAccent
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Manage server-side Gemini credentials, select AI models, and run diagnostic connection checks for the AI Capital Growth Advisor.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Stored API Key Mask:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = config.maskedKey,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Enter New Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle visibility"
                                )
                            }
                        },
                        singleLine = true
                    )

                    if (keyInput.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.saveGeminiApiKey(keyInput)
                                keyInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Key")
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("Select AI Model:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Box {
                        OutlinedButton(
                            onClick = { modelDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(SupportedGeminiModels.find(config.selectedModel).displayName, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ExpandMore, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = modelDropdownExpanded,
                            onDismissRequest = { modelDropdownExpanded = false }
                        ) {
                            SupportedGeminiModels.ALL.forEach { opt ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(opt.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(opt.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = {
                                        viewModel.setGeminiModel(opt.id)
                                        modelDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { viewModel.testGeminiConnection() },
                        enabled = !isTesting,
                        colors = ButtonDefaults.buttonColors(containerColor = BlueFintech),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Testing Connection...")
                        } else {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Test Gemini Connection")
                        }
                    }

                    if (testResult != null) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (testResult!!.success) EmeraldPrimary.copy(alpha = 0.12f) else RoseExpense.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (testResult!!.success) "✅ Connection Succeeded" else "❌ Connection Failed",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (testResult!!.success) EmeraldPrimary else RoseExpense
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(testResult!!.message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                if (testResult!!.success) {
                                    Text("Latency: ${testResult!!.latencyMs}ms · Model: ${testResult!!.modelUsed}", fontSize = 10.sp, color = EmeraldPrimary)
                                }
                            }
                        }
                    }

                    if (config.isConfigured) {
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showRemoveConfirm = true }) {
                                Text("Remove Stored Key", color = RoseExpense, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("API Request & Token Usage Logs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (usageLogs.isEmpty()) {
                        Text("No API calls logged yet.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        usageLogs.take(10).forEach { log ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("${log.model} · ${log.latencyMs}ms", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        Text(
                                            SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date(log.timestamp)),
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = log.status,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (log.status == "SUCCESS") EmeraldPrimary else RoseExpense
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove Gemini API Key?") },
            text = { Text("Are you sure you want to remove the stored API key? The advisor will not be able to generate AI analyses until a key is configured again.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeGeminiApiKey()
                        showRemoveConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseExpense)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

