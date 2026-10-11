package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AdvisorCapitalPlan
import com.example.ai.AdvisorChatMessage
import com.example.ai.AdvisorFinancialSnapshot
import com.example.ai.AdvisorOpportunity
import com.example.ai.AdvisorPillar
import com.example.ai.ConnectionTestResult
import com.example.ai.ExecutiveSummary
import com.example.ai.GeminiConfig
import com.example.ai.GeminiConnectionStatus
import com.example.ai.MilestoneStatus
import com.example.ai.PlanMilestone
import com.example.ai.SupportedGeminiModels
import com.example.ui.components.formatCurrency
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BlueFintech
import com.example.ui.theme.CyanIncome
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.PurpleFintech
import com.example.ui.theme.RoseExpense
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BusinessAdvisorScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onOpenProfile: () -> Unit = {}
) {
    val config by viewModel.geminiConfig.collectAsStateWithLifecycle()
    val snapshot by viewModel.advisorFinancialSnapshot.collectAsStateWithLifecycle()
    val executiveSummary by viewModel.advisorExecutiveSummary.collectAsStateWithLifecycle()
    val opportunities by viewModel.advisorOpportunities.collectAsStateWithLifecycle()
    val capitalPlan by viewModel.advisorCapitalPlan.collectAsStateWithLifecycle()
    val chatMessages by viewModel.advisorChatMessages.collectAsStateWithLifecycle()
    val usageLogs by viewModel.advisorUsageLogs.collectAsStateWithLifecycle()

    val isAnalyzing by viewModel.isAdvisorLoading.collectAsStateWithLifecycle()
    val isChatSending by viewModel.isChatSending.collectAsStateWithLifecycle()
    val isTestingConnection by viewModel.isTestingConnection.collectAsStateWithLifecycle()
    val connectionResult by viewModel.connectionTestResult.collectAsStateWithLifecycle()
    val errorMsg by viewModel.advisorError.collectAsStateWithLifecycle()
    val advisorNotice by viewModel.advisorNotice.collectAsStateWithLifecycle()
    val webMode by viewModel.advisorWebMode.collectAsStateWithLifecycle()
    val market by viewModel.marketData.collectAsStateWithLifecycle()
    var feedbackFor by remember { mutableStateOf<AdvisorChatMessage?>(null) }
    val latestBriefing by viewModel.latestBriefing.collectAsStateWithLifecycle()
    val isBriefingRunning by viewModel.isBriefingRunning.collectAsStateWithLifecycle()

    // Measure tracked milestones whenever the advisor opens
    LaunchedEffect(Unit) { viewModel.checkPlanOutcomes() }
    val isMarketRefreshing by viewModel.isMarketRefreshing.collectAsStateWithLifecycle()
    var marketEdit by remember { mutableStateOf<Pair<com.example.ai.MarketIndicators.Indicator, com.example.ai.MarketRate?>?>(null) }

    // Keep market figures fresh (at most one web check a day, only with a Gemini key)
    LaunchedEffect(config.isConfigured) {
        if (config.isConfigured) viewModel.refreshMarketData(force = false)
    }
    val context = LocalContext.current

    LaunchedEffect(advisorNotice) {
        advisorNotice?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeAdvisorNotice()
        }
    }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAddMilestoneDialog by remember { mutableStateOf(false) }
    var selectedPillarFilter by remember { mutableStateOf<AdvisorPillar?>(null) }
    var chatInput by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Overview & Opportunities, 1: Capital Plan, 2: AI Consultation, 3: Diagnostics

    val filteredOpps = remember(opportunities, selectedPillarFilter) {
        if (selectedPillarFilter == null) opportunities else opportunities.filter { it.pillar == selectedPillarFilter }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("ai_capital_growth_advisor_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header with Controls & Connection Status
        item {
            AdvisorHeaderCard(
                config = config,
                snapshot = snapshot,
                isAnalyzing = isAnalyzing,
                onOpenSettings = { showSettingsDialog = true },
                onRefreshSnapshot = { viewModel.recomputeAdvisorSnapshot() },
                onRunAnalysis = { viewModel.runFullCapitalAnalysis() }
            )
        }

        // Error Banner if present
        if (!errorMsg.isNullOrBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = RoseExpense.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = RoseExpense, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMsg ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = RoseExpense,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Financial Command Center (KPI Cards)
        item {
            FinancialOverviewKpis(snapshot = snapshot)
        }

        // Navigation Tabs for Sub-sections
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Executive & Opps", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Plan & Milestones", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (capitalPlan.milestones.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Badge(containerColor = EmeraldPrimary) {
                                    Text("${capitalPlan.milestones.size}", fontSize = 9.sp)
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Consultation", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Diagnostics", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                // TAB 0: Smart alerts & forecast (on-device), Executive Summary & Ranked Opportunities
                item {
                    WeeklyBriefingCard(
                        briefing = latestBriefing,
                        isGenerating = isBriefingRunning,
                        onGenerate = { viewModel.generateBriefingNow() }
                    )
                }
                item {
                    SmartAlertsCard(
                        alerts = snapshot.deepContext.alerts,
                        onAsk = { question ->
                            selectedTab = 2
                            viewModel.sendAdvisorChatMessage(question)
                        }
                    )
                }
                item {
                    ForecastAndPatternsCard(deep = snapshot.deepContext)
                }
                item {
                    OutlinedButton(
                        onClick = onOpenProfile,
                        modifier = Modifier.fillMaxWidth().testTag("advisor_open_profile"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Net worth ${formatCurrency(snapshot.netWorth.netWorth)} · update what you own & owe →",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                item {
                    RealReturnRankingCard(
                        ranked = snapshot.rankedOptions,
                        amountEtb = snapshot.availableLiquidCash,
                        inflation = market.get(com.example.ai.MarketIndicators.INFLATION)
                    )
                }
                item {
                    MarketDataCard(
                        market = market,
                        isRefreshing = isMarketRefreshing,
                        onRefresh = { viewModel.refreshMarketData(force = true) },
                        onEdit = { indicator, rate -> marketEdit = indicator to rate }
                    )
                }

                if (executiveSummary != null) {
                    item {
                        ExecutiveSummaryCard(summary = executiveSummary!!)
                    }
                } else {
                    item {
                        EmptyAnalysisCard(
                            config = config,
                            isAnalyzing = isAnalyzing,
                            onAnalyze = { viewModel.runFullCapitalAnalysis() },
                            onOpenSettings = { showSettingsDialog = true }
                        )
                    }
                }

                item {
                    OpportunityFilterChips(
                        selectedPillar = selectedPillarFilter,
                        onSelectPillar = { selectedPillarFilter = it }
                    )
                }

                if (filteredOpps.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No opportunities in this category yet", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Tap 'Run AI Analysis' above to generate fresh opportunities.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(filteredOpps, key = { it.id }) { opp ->
                        OpportunityCard(
                            opportunity = opp,
                            isInPlan = viewModel.isOpportunityInPlan(opp, capitalPlan),
                            onAddToPlan = { viewModel.convertOpportunityToMilestone(opp) },
                            onViewPlan = { selectedTab = 1 }
                        )
                    }
                }
            }

            1 -> {
                // TAB 1: Capital Growth Plan & Milestones Tracker
                item {
                    CapitalPlanHeaderCard(
                        plan = capitalPlan,
                        onAddMilestoneClick = { showAddMilestoneDialog = true }
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { viewModel.checkPlanOutcomes() },
                        modifier = Modifier.fillMaxWidth().testTag("check_plan_outcomes"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("📏 Measure progress from my transactions", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (capitalPlan.milestones.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("No milestones added to plan yet", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text("Add action items from the Opportunities tab or create your own custom milestone.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = { showAddMilestoneDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Custom Milestone")
                                }
                            }
                        }
                    }
                } else {
                    items(capitalPlan.milestones, key = { it.id }) { milestone ->
                        MilestoneItemCard(
                            milestone = milestone,
                            onUpdateStatus = { newStatus, verifiedOutcome ->
                                viewModel.updateMilestoneStatus(milestone.id, newStatus, verifiedOutcome)
                            }
                        )
                    }
                }
            }

            2 -> {
                // TAB 2: Interactive Gemini Financial Consultation Chat
                item {
                    ChatSuggestedQuestionsRow(
                        onSelectQuestion = { query ->
                            chatInput = query
                            viewModel.sendAdvisorChatMessage(query)
                        }
                    )
                }

                if (chatMessages.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = PurpleFintech, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Ask Your Financial Advisor Anything", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "Your advisor is grounded in your actual multi-bank balance (${formatCurrency(snapshot.totalRecordedBalance)}) and Ethiopian market dynamics.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                } else {
                    items(chatMessages, key = { it.id }) { msg ->
                        ChatBubble(
                            message = msg,
                            onRate = { helpful ->
                                if (helpful) {
                                    viewModel.rateAnswer(msg.id, true)
                                } else {
                                    feedbackFor = msg
                                }
                            }
                        )
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { viewModel.clearChatHistory() }) {
                                Text("Clear History", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                item {
                    AdvisorModeBar(
                        webMode = webMode,
                        onWebModeChange = { viewModel.setAdvisorWebMode(it) }
                    )
                }

                item {
                    ChatInputBar(
                        query = chatInput,
                        isLoading = isChatSending,
                        onQueryChange = { chatInput = it },
                        onSend = {
                            viewModel.sendAdvisorChatMessage(chatInput)
                            chatInput = ""
                        }
                    )
                }
            }

            3 -> {
                // TAB 3: API Usage & Diagnostics
                item {
                    DiagnosticsCard(
                        config = config,
                        logs = usageLogs,
                        onOpenSettings = { showSettingsDialog = true }
                    )
                }
            }
        }
    }

    feedbackFor?.let { msg ->
        AnswerFeedbackDialog(
            onDismiss = { feedbackFor = null },
            onSubmit = { reason ->
                viewModel.rateAnswer(msg.id, false, reason)
                feedbackFor = null
            }
        )
    }

    marketEdit?.let { (indicator, rate) ->
        MarketValueDialog(
            indicator = indicator,
            current = rate,
            onDismiss = { marketEdit = null },
            onSave = { value, asOf, note ->
                viewModel.setMarketValue(indicator.key, value, asOf, note)
                marketEdit = null
            }
        )
    }

    // Gemini Settings Dialog
    if (showSettingsDialog) {
        GeminiApiSettingsDialog(
            config = config,
            isTesting = isTestingConnection,
            testResult = connectionResult,
            onDismiss = { showSettingsDialog = false },
            onSaveKey = { viewModel.saveGeminiApiKey(it) },
            onSelectModel = { viewModel.setGeminiModel(it) },
            onRemoveKey = { viewModel.removeGeminiApiKey() },
            onTestConnection = { viewModel.testGeminiConnection() }
        )
    }

    // Add Custom Milestone Dialog
    if (showAddMilestoneDialog) {
        AddMilestoneDialog(
            onDismiss = { showAddMilestoneDialog = false },
            onAdd = { title, desc, benefit, deadline ->
                viewModel.addCapitalPlanMilestone(title, desc, benefit, deadline)
                showAddMilestoneDialog = false
            }
        )
    }
}

// --------------------------------------------------------------------------
// Sub-components
// --------------------------------------------------------------------------

@Composable
fun AdvisorHeaderCard(
    config: GeminiConfig,
    snapshot: AdvisorFinancialSnapshot,
    isAnalyzing: Boolean,
    onOpenSettings: () -> Unit,
    onRefreshSnapshot: () -> Unit,
    onRunAnalysis: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = PurpleFintech.copy(alpha = 0.18f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = PurpleFintech, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AI CAPITAL GROWTH ADVISOR",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Evidence-Driven Wealth Multiplier (Ethiopia)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Connection status chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (config.status) {
                        GeminiConnectionStatus.CONNECTED -> EmeraldPrimary.copy(alpha = 0.15f)
                        GeminiConnectionStatus.NOT_CONFIGURED -> RoseExpense.copy(alpha = 0.15f)
                        else -> AmberAccent.copy(alpha = 0.15f)
                    },
                    modifier = Modifier.clickable { onOpenSettings() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (config.status) {
                                        GeminiConnectionStatus.CONNECTED -> EmeraldPrimary
                                        GeminiConnectionStatus.NOT_CONFIGURED -> RoseExpense
                                        else -> AmberAccent
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (config.status == GeminiConnectionStatus.CONNECTED) "Connected" else "Settings",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (config.status) {
                                GeminiConnectionStatus.CONNECTED -> EmeraldPrimary
                                GeminiConnectionStatus.NOT_CONFIGURED -> RoseExpense
                                else -> AmberAccent
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Grounded in ${formatCurrency(snapshot.totalRecordedBalance)} total liquid assets across ${snapshot.activeSourcesCount} sources and ${snapshot.activePhonesCount} registered devices.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "Model: ${config.selectedModel}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRefreshSnapshot, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Data", modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onRunAnalysis,
                        enabled = !isAnalyzing,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Analyzing...", fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Run AI Analysis", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FinancialOverviewKpis(snapshot: AdvisorFinancialSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiCard(
                title = "Total Recorded Balance",
                value = formatCurrency(snapshot.totalRecordedBalance),
                subtitle = "Banks & cash in hand",
                tint = EmeraldPrimary,
                icon = Icons.Default.AccountBalance,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Available Liquid Cash",
                value = formatCurrency(snapshot.availableLiquidCash),
                subtitle = "After 3-mo reserve",
                tint = CyanIncome,
                icon = Icons.Default.Savings,
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiCard(
                title = "Month Net Cash Flow",
                value = (if (snapshot.netCashFlow >= 0) "+" else "") + formatCurrency(snapshot.netCashFlow),
                subtitle = "Inflow - Outflow",
                tint = if (snapshot.netCashFlow >= 0) EmeraldPrimary else RoseExpense,
                icon = if (snapshot.netCashFlow >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.Default.TrendingDown,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Emergency Runway",
                value = "${String.format("%.1f", snapshot.runwayMonths)} Months",
                subtitle = snapshot.emergencyReserveStatus,
                tint = if (snapshot.runwayMonths >= 3.0) EmeraldPrimary else AmberAccent,
                icon = Icons.Default.Security,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    tint: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 10.sp, color = tint, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ExecutiveSummaryCard(summary: ExecutiveSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PurpleFintech, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Executive Strategy Summary", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    text = SimpleDateFormat("dd MMM, HH:mm", Locale.US).format(Date(summary.analysisTimestamp)),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = summary.overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(10.dp))

            // Barriers & Opportunities
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("⚠️ Cash Growth Barriers", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RoseExpense)
                    Spacer(Modifier.height(4.dp))
                    summary.cashGrowthBarriers.take(3).forEach { b ->
                        Text("• $b", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("🚀 Top Growth Vectors", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                    Spacer(Modifier.height(4.dp))
                    summary.topOpportunities.take(3).forEach { o ->
                        Text("• $o", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
            }

            if (summary.priorityActions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldPrimary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Immediate Recommended Steps:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                        Spacer(Modifier.height(4.dp))
                        summary.priorityActions.forEachIndexed { idx, act ->
                            Text("${idx + 1}. $act", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyAnalysisCard(
    config: GeminiConfig,
    isAnalyzing: Boolean,
    onAnalyze: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Psychology, contentDescription = null, tint = PurpleFintech, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(10.dp))
            Text("No AI Analysis Run Yet", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Execute a comprehensive evaluation across all 6 financial pillars to discover high-yield opportunities and generate an executive roadmap.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            if (!config.isConfigured) {
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleFintech)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Configure Gemini API Key")
                }
            } else {
                Button(
                    onClick = onAnalyze,
                    enabled = !isAnalyzing,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Analyzing Ethiopian Market...")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Run Capital Growth Analysis")
                    }
                }
            }
        }
    }
}

@Composable
fun OpportunityFilterChips(
    selectedPillar: AdvisorPillar?,
    onSelectPillar: (AdvisorPillar?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedPillar == null,
            onClick = { onSelectPillar(null) },
            label = { Text("All Pillars", fontSize = 11.sp) },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f))
        )
        AdvisorPillar.values().forEach { pillar ->
            FilterChip(
                selected = selectedPillar == pillar,
                onClick = { onSelectPillar(if (selectedPillar == pillar) null else pillar) },
                label = { Text(pillar.displayName, fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(android.graphics.Color.parseColor(pillar.colorHex)).copy(alpha = 0.2f)
                )
            )
        }
    }
}

@Composable
fun OpportunityCard(
    opportunity: AdvisorOpportunity,
    isInPlan: Boolean = false,
    onAddToPlan: () -> Unit,
    onViewPlan: () -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }
    val pillarColor = try {
        Color(android.graphics.Color.parseColor(opportunity.pillar.colorHex))
    } catch (_: Exception) {
        EmeraldPrimary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = pillarColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = opportunity.pillar.displayName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = pillarColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (opportunity.riskLevel.lowercase()) {
                            "low" -> EmeraldPrimary.copy(alpha = 0.12f)
                            "medium" -> AmberAccent.copy(alpha = 0.12f)
                            else -> RoseExpense.copy(alpha = 0.12f)
                        }
                    ) {
                        Text(
                            text = "${opportunity.riskLevel} Risk",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = when (opportunity.riskLevel.lowercase()) {
                                "low" -> EmeraldPrimary
                                "medium" -> AmberAccent
                                else -> RoseExpense
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${opportunity.confidenceScore}% Conf",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(opportunity.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            opportunity.realReturnPct?.let { real ->
                Text(
                    text = String.format(Locale.US, "Real return ≈ %+.1f%% a year after inflation", real),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (real >= 0) EmeraldPrimary else RoseExpense
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(opportunity.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Est. Net Benefit", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "+${formatCurrency(opportunity.estimatedBenefitEtb)} (${opportunity.benefitPeriod})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Capital Req.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (opportunity.capitalRequiredEtb > 0) formatCurrency(opportunity.capitalRequiredEtb) else "0 ETB",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))
                    Text("Recommended Next Step:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                    Text(opportunity.recommendedNextStep, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)

                    if (opportunity.assumptions.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Assumptions & Sources:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        opportunity.assumptions.forEach {
                            Text("• $it", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { isExpanded = !isExpanded }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (isExpanded) "Less details ▲" else "More details ▼", fontSize = 11.sp)
                }

                if (isInPlan) {
                    OutlinedButton(
                        onClick = onViewPlan,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("opportunity_in_plan_${opportunity.id}")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("In Capital Plan · View", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                    }
                } else {
                    Button(
                        onClick = onAddToPlan,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("opportunity_add_to_plan_${opportunity.id}")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add to Capital Plan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CapitalPlanHeaderCard(
    plan: AdvisorCapitalPlan,
    onAddMilestoneClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("90-Day Capital Growth Goal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Target: ${formatCurrency(plan.targetCash)} by ${SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(plan.targetDateMillis))}", fontSize = 11.sp, color = EmeraldPrimary)
                }

                Button(
                    onClick = onAddMilestoneClick,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Milestone", fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { plan.progressPercentage.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = EmeraldPrimary,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Progress: ${(plan.progressPercentage * 100).toInt()}% completed", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${plan.milestones.count { it.status == MilestoneStatus.COMPLETED }}/${plan.milestones.size} actions", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MilestoneItemCard(
    milestone: PlanMilestone,
    onUpdateStatus: (MilestoneStatus, Double?) -> Unit
) {
    var showVerifyDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (milestone.status == MilestoneStatus.COMPLETED) EmeraldPrimary.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(
                        onClick = {
                            if (milestone.status != MilestoneStatus.COMPLETED) {
                                showVerifyDialog = true
                            } else {
                                onUpdateStatus(MilestoneStatus.PENDING, null)
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (milestone.status == MilestoneStatus.COMPLETED) Icons.Default.CheckCircle else Icons.Default.Check,
                            contentDescription = "Toggle status",
                            tint = if (milestone.status == MilestoneStatus.COMPLETED) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = milestone.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (milestone.status == MilestoneStatus.COMPLETED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Target: +${formatCurrency(milestone.targetBenefitEtb)} · Due: ${SimpleDateFormat("dd MMM", Locale.US).format(Date(milestone.deadlineMillis))}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        milestone.autoNote?.let { note ->
                            Text(
                                text = "📏 $note",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if ((milestone.autoOutcomeEtb ?: 0.0) > 0) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (milestone.status) {
                        MilestoneStatus.COMPLETED -> EmeraldPrimary.copy(alpha = 0.15f)
                        MilestoneStatus.IN_PROGRESS -> BlueFintech.copy(alpha = 0.15f)
                        MilestoneStatus.POSTPONED -> AmberAccent.copy(alpha = 0.15f)
                        MilestoneStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = milestone.status.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (milestone.status) {
                            MilestoneStatus.COMPLETED -> EmeraldPrimary
                            MilestoneStatus.IN_PROGRESS -> BlueFintech
                            MilestoneStatus.POSTPONED -> AmberAccent
                            MilestoneStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (milestone.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(milestone.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (milestone.verifiedOutcomeEtb != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "✅ Verified Outcome: +${formatCurrency(milestone.verifiedOutcomeEtb)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary
                )
            }
        }
    }

    if (showVerifyDialog) {
        var outcomeText by remember { mutableStateOf(milestone.targetBenefitEtb.toString()) }
        AlertDialog(
            onDismissRequest = { showVerifyDialog = false },
            title = { Text("Complete & Verify Outcome") },
            text = {
                Column {
                    Text("Enter verified financial benefit achieved from this action:", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = outcomeText,
                        onValueChange = { outcomeText = it },
                        label = { Text("Verified Benefit (ETB)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val outcome = outcomeText.toDoubleOrNull() ?: milestone.targetBenefitEtb
                        onUpdateStatus(MilestoneStatus.COMPLETED, outcome)
                        showVerifyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Confirm Complete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVerifyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ChatSuggestedQuestionsRow(onSelectQuestion: (String) -> Unit) {
    val suggestions = listOf(
        "Categorize my uncategorized expenses",
        "What are my recurring payments and which can I cut?",
        "Forecast my balance for the next 90 days",
        "How can I increase cash by 50,000 ETB?",
        "What is my biggest cash leak right now?",
        "How much can I safely invest?",
        "Compare CBE special deposit vs T-Bills",
        "Create a 3-month capital allocation plan"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { q ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.clickable { onSelectQuestion(q) }
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PurpleFintech, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(q, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: AdvisorChatMessage, onRate: ((Boolean) -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (message.isUser) 14.dp else 2.dp,
                bottomEnd = if (message.isUser) 2.dp else 14.dp
            ),
            color = if (message.isUser) EmeraldPrimary else MaterialTheme.colorScheme.surface,
            tonalElevation = if (message.isUser) 0.dp else 2.dp,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.US).format(Date(message.timestamp)),
                    fontSize = 9.sp,
                    color = if (message.isUser) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
        if (!message.isUser) {
            AgentTraceChips(toolActions = message.toolActions, sources = message.sources)
            if (onRate != null && !message.message.startsWith("⚠️")) {
                AnswerFeedbackRow(rating = message.rating, onRate = onRate)
            }
        }
    }
}

@Composable
fun ChatInputBar(
    query: String,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Ask your advisor anything...", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = query.isNotBlank() && !isLoading,
                modifier = Modifier
                    .size(44.dp)
                    .background(if (query.isNotBlank()) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (query.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticsCard(
    config: GeminiConfig,
    logs: List<com.example.ai.AdvisorUsageLog>,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Gemini API Diagnostics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Settings", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Active Model:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(config.selectedModel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("API Status:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(config.status.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (config.lastLatencyMs != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Last Latency:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${config.lastLatencyMs} ms", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Recent API Activity Logs (${logs.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))

            if (logs.isEmpty()) {
                Text("No API calls recorded yet.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                logs.take(5).forEach { log ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${log.model} · ${log.latencyMs}ms",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(log.timestamp)),
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

// --------------------------------------------------------------------------
// Dialogs
// --------------------------------------------------------------------------

@Composable
fun GeminiApiSettingsDialog(
    config: GeminiConfig,
    isTesting: Boolean,
    testResult: ConnectionTestResult?,
    onDismiss: () -> Unit,
    onSaveKey: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onRemoveKey: () -> Unit,
    onTestConnection: () -> Unit
) {
    var keyInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = EmeraldPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Gemini API Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Configure your Google Gemini API key to power your AI Capital Growth Advisor. Keys are stored locally on-device and never shared.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(14.dp))

                // Currently stored key info
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Stored Key:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = config.maskedKey,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Enter new key
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("Enter Gemini API Key") },
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
                            onSaveKey(keyInput)
                            keyInput = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save New Key")
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Model Selector
                Text("Gemini Model:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                    onSelectModel(opt.id)
                                    modelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Connection Test Button & Results
                Button(
                    onClick = onTestConnection,
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
                        color = if (testResult.success) EmeraldPrimary.copy(alpha = 0.12f) else RoseExpense.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (testResult.success) "✅ Connection Succeeded" else "❌ Connection Failed",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (testResult.success) EmeraldPrimary else RoseExpense
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(testResult.message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            if (testResult.success) {
                                Text("Latency: ${testResult.latencyMs}ms · Model: ${testResult.modelUsed}", fontSize = 10.sp, color = EmeraldPrimary)
                            }
                        }
                    }
                }

                if (config.isConfigured) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = { showRemoveConfirm = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Remove API Key", color = RoseExpense, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove Gemini API Key?") },
            text = { Text("Are you sure you want to remove the stored API key? The advisor will not be able to generate AI analyses until a key is provided again.") },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveKey()
                        showRemoveConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseExpense)
                ) {
                    Text("Remove Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AddMilestoneDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, description: String, benefit: Double, deadline: Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var benefitText by remember { mutableStateOf("10000") }
    var daysText by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Capital Plan Milestone", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Milestone Title") },
                    placeholder = { Text("e.g. Move funds to 14% high-yield deposit") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description / Strategy") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                OutlinedTextField(
                    value = benefitText,
                    onValueChange = { benefitText = it },
                    label = { Text("Target Net Benefit (ETB)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = { Text("Target Deadline (Days from now)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val benefit = benefitText.toDoubleOrNull() ?: 5000.0
                        val days = daysText.toLongOrNull() ?: 30L
                        val deadline = System.currentTimeMillis() + (days * 24 * 3600 * 1000L)
                        onAdd(title, desc, benefit, deadline)
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Add Milestone")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
