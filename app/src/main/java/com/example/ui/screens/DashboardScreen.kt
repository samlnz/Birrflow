package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.example.data.entity.CategoryEntity
import com.example.data.entity.FinancialSourceEntity
import com.example.data.entity.ManualSourceEntity
import com.example.data.entity.ManualSourceKind
import com.example.data.entity.RawSmsEntity
import com.example.data.entity.RegisteredPhoneEntity
import com.example.data.entity.TransactionEntity
import com.example.service.ScanProgress
import com.example.sync.CloudSyncState
import com.example.ui.components.BackgroundSyncBatteryOptimizationCard
import com.example.ui.components.BalanceBoardCard
import com.example.ui.components.BankFilterSelectorBar
import com.example.ui.components.DashboardChartsSection
import com.example.ui.components.ManualSourceDialog
import com.example.ui.components.MetricCard
import com.example.ui.components.MultiPhoneSelectorBar
import com.example.ui.components.SmsPermissionRationaleBanner
import com.example.ui.components.SpendingByCategoryCard
import com.example.ui.components.TransactionCard
import com.example.ui.components.TransactionDetailDialog
import com.example.ui.components.formatCurrency
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BlueFintech
import com.example.ui.theme.CyanIncome
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.PurpleFintech
import com.example.ui.theme.RoseExpense
import com.example.ui.viewmodel.CashAssetDelta
import com.example.ui.viewmodel.CategorySpend
import com.example.ui.viewmodel.CombinedCalculationBreakdown
import com.example.ui.viewmodel.FinancialOverview
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.PhoneMetric
import com.example.ui.viewmodel.RangeExplorerMode
import com.example.ui.viewmodel.SourceMetric
import com.example.ui.viewmodel.SpecificRangeData
import com.example.ui.viewmodel.TimeframeDeltas
import com.example.ui.viewmodel.TotalCashAssetSummary

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onRequestSmsPermission: () -> Unit,
    showPermissionBanner: Boolean,
    onDismissPermissionBanner: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val totalCashAsset by viewModel.totalCashAssetSummary.collectAsStateWithLifecycle()
    val overview by viewModel.financialOverview.collectAsStateWithLifecycle()
    val calculationBreakdown by viewModel.combinedCalculationBreakdown.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()

    val phones by viewModel.allPhones.collectAsStateWithLifecycle()
    val sources by viewModel.allSources.collectAsStateWithLifecycle()
    val balanceBoard by viewModel.balanceBoard.collectAsStateWithLifecycle()
    val categorySpends by viewModel.categorySpends.collectAsStateWithLifecycle()
    val dashboardCharts by viewModel.dashboardCharts.collectAsStateWithLifecycle()
    val categories by viewModel.allCategories.collectAsStateWithLifecycle()
    val cloudSyncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val isScanningInbox by viewModel.isScanningInbox.collectAsStateWithLifecycle()

    val specificRangeData by viewModel.specificRangeData.collectAsStateWithLifecycle()
    val rangeExplorerMode by viewModel.rangeExplorerMode.collectAsStateWithLifecycle()
    val selectedExplorerHour by viewModel.selectedExplorerHour.collectAsStateWithLifecycle()
    val selectedExplorerHourDate by viewModel.selectedExplorerHourDate.collectAsStateWithLifecycle()
    val explorerStartDate by viewModel.explorerStartDate.collectAsStateWithLifecycle()
    val explorerEndDate by viewModel.explorerEndDate.collectAsStateWithLifecycle()
    val explorerWeekOffset by viewModel.explorerWeekOffset.collectAsStateWithLifecycle()
    val explorerMonthOffset by viewModel.explorerMonthOffset.collectAsStateWithLifecycle()

    val selectedPhoneId by viewModel.selectedPhoneId.collectAsStateWithLifecycle()
    val selectedSourceId by viewModel.selectedSourceId.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var showCloudPlansDialog by remember { mutableStateOf(false) }
    var showFullScanConfirmDialog by remember { mutableStateOf(false) }
    var showAddOtherIncomeDialog by remember { mutableStateOf(false) }
    val activeTxFromVm by viewModel.activeTransactionForDetail.collectAsStateWithLifecycle()
    var selectedTransactionForDetail by remember { mutableStateOf<TransactionEntity?>(null) }
    val effectiveTxForDetail = activeTxFromVm ?: selectedTransactionForDetail

    val phoneMap = remember(phones) { phones.associateBy { it.phoneId } }
    val categoryMap = remember(categories) { categories.associateBy { it.categoryId } }

    val currentPhoneName = selectedPhoneId?.let { pid -> phoneMap[pid]?.friendlyName ?: pid } ?: "All Phones Combined"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Permission Rationale Banner & Battery Optimization Banner
        if (showPermissionBanner) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SmsPermissionRationaleBanner(
                        onGrantClick = onRequestSmsPermission,
                        onDismiss = onDismissPermissionBanner
                    )
                }
            }
        }

        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                BackgroundSyncBatteryOptimizationCard()
            }
        }

        // 2. Visible Real-Time Summation of User Total Cash Asset (Header Card)
        item {
            TotalCashAssetHeaderCard(
                summary = totalCashAsset,
                cloudState = cloudSyncState,
                onOpenCloudSync = { showCloudPlansDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        // 3. Calendar Date Control Bar (Daily by default, with calendar picker)
        item {
            DashboardDateControlBar(
                selectedDate = selectedDate,
                onSelectDate = { viewModel.selectDate(it) },
                onPrevDay = { viewModel.goToPreviousDay() },
                onNextDay = { viewModel.goToNextDay() },
                onToday = { viewModel.goToToday() },
                onAllTime = { viewModel.toggleAllTime() }
            )
        }

        // 4. Multi-phone Selector & Bank Filters
        item {
            Column {
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
            }
        }

        // 5. Latest bank balances & activity (SMS + manual other incomes & cash sources) - Placed above Specific & Range Explorer
        item {
            BalanceBoardCard(
                board = balanceBoard,
                phones = phones,
                periodLabel = selectedDate ?: "all time",
                onGroupingChange = { viewModel.balanceGrouping.value = it },
                onFlowFilterChange = { viewModel.balanceFlowFilter.value = it },
                onSourceClick = { srcId ->
                    viewModel.selectedSourceId.value = if (selectedSourceId == srcId) null else srcId
                },
                onSaveManual = { existing, name, kind, balance, phoneId, note ->
                    viewModel.saveManualSource(existing, name, kind, balance, phoneId, note)
                },
                onDeleteManual = { viewModel.deleteManualSource(it) },
                onAdjustManual = { src, delta -> viewModel.adjustManualSourceBalance(src, delta) },
                onToggleDisable = { viewModel.toggleSourceDisabled(it) }
            )
        }

        // 6. Specific & Range Explorer Card (with Full History Scan & Quick SMS Scan buttons)
        item {
            SpecificRangeExplorerCard(
                rangeData = specificRangeData,
                rangeMode = rangeExplorerMode,
                selectedHour = selectedExplorerHour,
                hourDate = selectedExplorerHourDate,
                startDate = explorerStartDate,
                endDate = explorerEndDate,
                weekOffset = explorerWeekOffset,
                monthOffset = explorerMonthOffset,
                onModeChange = { viewModel.setRangeExplorerMode(it) },
                onHourChange = { viewModel.setExplorerHour(it) },
                onHourDateChange = { viewModel.setExplorerHourDate(it) },
                onStartDateChange = { viewModel.setExplorerStartDate(it) },
                onEndDateChange = { viewModel.setExplorerEndDate(it) },
                onWeekOffsetChange = { viewModel.setExplorerWeekOffset(it) },
                onMonthOffsetChange = { viewModel.setExplorerMonthOffset(it) },
                onSelectTransaction = { selectedTransactionForDetail = it },
                onFullHistoryScan = { showFullScanConfirmDialog = true },
                onQuickScan = { viewModel.quickScanLatestSms() },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // 9. Spending by Category (every manual category + uncategorized debits still waiting)
        if (categorySpends.isNotEmpty()) {
            item {
                SpendingByCategoryCard(
                    spends = categorySpends,
                    periodLabel = selectedDate ?: "All time",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // 10. Graphical insights: money in/out bars, net trend, spending mix and income by source
        item {
            DashboardChartsSection(
                data = dashboardCharts,
                scopeLabel = buildString {
                    append(currentPhoneName)
                    selectedSourceId?.let { sid -> append(" · ").append(sources.firstOrNull { it.sourceId == sid }?.name ?: sid) }
                },
                onRangeChange = { viewModel.setChartRange(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }

    // Detail Dialog
    effectiveTxForDetail?.let { tx ->
        val raw by produceState<RawSmsEntity?>(initialValue = null, tx.rawSmsId) { value = viewModel.getRawSms(tx.rawSmsId) }
        val phone = phoneMap[tx.phoneId]

        TransactionDetailDialog(
            transaction = tx,
            rawSms = raw,
            phoneName = phone?.friendlyName ?: tx.phoneId,
            categories = categories,
            onDismiss = {
                selectedTransactionForDetail = null
                viewModel.dismissActiveTransactionDetail()
            },
            onSaveCategoryAndNotes = { newCatId, notes ->
                viewModel.updateTransactionCategory(tx.transactionId, newCatId, notes)
                selectedTransactionForDetail = null
                viewModel.dismissActiveTransactionDetail()
            }
        )
    }

    if (showCloudPlansDialog) {
        MultiPhoneCloudPlansDialog(
            cloudState = cloudSyncState,
            phones = phones,
            onUnlink = { viewModel.unlinkSync() },
            onConfigureFirebase = { pid, key, app -> viewModel.configureFirebase(pid, key, app) },
            onConfigureRailway = { url -> viewModel.configureRailway(url) },
            onDismiss = { showCloudPlansDialog = false }
        )
    }

    if (showFullScanConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showFullScanConfirmDialog = false },
            title = { Text("Order Full Inbox History Scan?") },
            text = {
                Text(
                    "This will read all past historical bank SMS across your entire inbox. " +
                    "To keep the app fast and prevent crashes, routine app launches only read the latest bank alerts."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFullScanConfirmDialog = false
                        viewModel.orderFullHistoricalScan()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Start Full Scan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFullScanConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddOtherIncomeDialog) {
        ManualSourceDialog(
            existing = null,
            phones = phones,
            onDismiss = { showAddOtherIncomeDialog = false },
            onSave = { name, kind, balance, phoneId, note ->
                viewModel.saveManualSource(null, name, kind, balance, phoneId, note)
                showAddOtherIncomeDialog = false
            }
        )
    }

    if (isScanningInbox && scanProgress.isFullScan) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Scanning Complete SMS Inbox History…") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Processing bank messages in background: ${scanProgress.scanned} of ${scanProgress.total}")
                    val ratio = if (scanProgress.total > 0) scanProgress.scanned.toFloat() / scanProgress.total.toFloat() else 0f
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = EmeraldPrimary
                    )
                }
            },
            confirmButton = {}
        )
    }
}

/**
 * Top Header Card showing real-time summation of user's Total Cash Asset across all accounts and timeframe deltas.
 */
@Composable
fun TotalCashAssetHeaderCard(
    summary: TotalCashAssetSummary,
    cloudState: CloudSyncState,
    onOpenCloudSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("total_cash_asset_header"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            EmeraldPrimary.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Top Row: Title + Live Sync Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = EmeraldPrimary,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TOTAL CASH ASSETS",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.3.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (cloudState.linked) BlueFintech.copy(alpha = 0.15f) else AmberAccent.copy(alpha = 0.15f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenCloudSync() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (cloudState.linked) Icons.Default.CloudDone else Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = if (cloudState.linked) BlueFintech else AmberAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (cloudState.linked) "${summary.activePhoneCount} Phones Synced" else "Internet Sync",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (cloudState.linked) BlueFintech else AmberAccent
                            )
                        }
                    }
                }

                // Cumulative Total
                Text(
                    text = formatCurrency(summary.totalCashAsset),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Breakdown Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(12.dp), tint = BlueFintech)
                            Spacer(Modifier.width(4.dp))
                            Text("Banks & Wallets: ${formatCurrency(summary.bankAndWalletBalance)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(12.dp), tint = AmberAccent)
                            Spacer(Modifier.width(4.dp))
                            Text("Other Incomes & Cash: ${formatCurrency(summary.manualAndOtherIncomeBalance)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                // Hourly, Daily, Weekly, and Monthly Increased / Decreased Amount Section
                CashAssetTimeframeDeltasSection(
                    deltas = summary.deltas,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Card containing Specific & Range Explorer Grid plus Full History Scan and Quick SMS Scan buttons.
 */
@Composable
fun SpecificRangeExplorerCard(
    rangeData: SpecificRangeData,
    rangeMode: RangeExplorerMode,
    selectedHour: Int,
    hourDate: String,
    startDate: String,
    endDate: String?,
    weekOffset: Int,
    monthOffset: Int,
    onModeChange: (RangeExplorerMode) -> Unit,
    onHourChange: (Int) -> Unit,
    onHourDateChange: (String) -> Unit,
    onStartDateChange: (String) -> Unit,
    onEndDateChange: (String?) -> Unit,
    onWeekOffsetChange: (Int) -> Unit,
    onMonthOffsetChange: (Int) -> Unit,
    onSelectTransaction: (TransactionEntity) -> Unit,
    onFullHistoryScan: () -> Unit,
    onQuickScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("specific_range_explorer_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpecificRangeExplorerGridSection(
                rangeData = rangeData,
                rangeMode = rangeMode,
                selectedHour = selectedHour,
                hourDate = hourDate,
                startDate = startDate,
                endDate = endDate,
                weekOffset = weekOffset,
                monthOffset = monthOffset,
                onModeChange = onModeChange,
                onHourChange = onHourChange,
                onHourDateChange = onHourDateChange,
                onStartDateChange = onStartDateChange,
                onEndDateChange = onEndDateChange,
                onWeekOffsetChange = onWeekOffsetChange,
                onMonthOffsetChange = onMonthOffsetChange,
                onSelectTransaction = onSelectTransaction,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Action Buttons Row: Full History Scan next to Quick SMS Scan
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onFullHistoryScan,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Full History Scan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onQuickScan,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp), tint = EmeraldPrimary)
                    Spacer(Modifier.width(4.dp))
                    Text("Quick SMS Scan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

enum class DeltaTimeframe(
    val title: String,
    val windowBadge: String,
    val description: String
) {
    HOURLY("Hourly", "Last 1h", "Change in the past 60 minutes"),
    DAILY("Daily", "Today / 24h", "Change today & past 24 hours"),
    WEEKLY("Weekly", "Past 7d", "Cumulative change over the past 7 days"),
    MONTHLY("Monthly", "This Month", "Cumulative change in this calendar month to date")
}

/**
 * Section displaying the hourly, daily, weekly, and monthly cash asset increase/decrease amount
 * directly beneath the Total Cash Assets amount in the dashboard header.
 */
@Composable
fun CashAssetTimeframeDeltasSection(
    deltas: TimeframeDeltas,
    modifier: Modifier = Modifier
) {
    var selectedTimeframe by rememberSaveable { mutableStateOf(DeltaTimeframe.DAILY) }

    Column(
        modifier = modifier.testTag("timeframe_deltas_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "ASSET INCREASE / DECREASE (ALL DEVICES)",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.1.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "All devices combined",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = EmeraldPrimary
            )
        }

        // 4 Timeframe Cards: 2x2 grid for clean mobile display
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TimeframeDeltaCard(
                    timeframe = DeltaTimeframe.HOURLY,
                    delta = deltas.hourly,
                    isSelected = selectedTimeframe == DeltaTimeframe.HOURLY,
                    onClick = { selectedTimeframe = DeltaTimeframe.HOURLY },
                    modifier = Modifier.weight(1f).testTag("delta_card_hourly")
                )
                TimeframeDeltaCard(
                    timeframe = DeltaTimeframe.DAILY,
                    delta = deltas.daily,
                    isSelected = selectedTimeframe == DeltaTimeframe.DAILY,
                    onClick = { selectedTimeframe = DeltaTimeframe.DAILY },
                    modifier = Modifier.weight(1f).testTag("delta_card_daily")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TimeframeDeltaCard(
                    timeframe = DeltaTimeframe.WEEKLY,
                    delta = deltas.weekly,
                    isSelected = selectedTimeframe == DeltaTimeframe.WEEKLY,
                    onClick = { selectedTimeframe = DeltaTimeframe.WEEKLY },
                    modifier = Modifier.weight(1f).testTag("delta_card_weekly")
                )
                TimeframeDeltaCard(
                    timeframe = DeltaTimeframe.MONTHLY,
                    delta = deltas.monthly,
                    isSelected = selectedTimeframe == DeltaTimeframe.MONTHLY,
                    onClick = { selectedTimeframe = DeltaTimeframe.MONTHLY },
                    modifier = Modifier.weight(1f).testTag("delta_card_monthly")
                )
            }
        }

        // Active Timeframe Breakdown Detail Banner
        val currentDelta = when (selectedTimeframe) {
            DeltaTimeframe.HOURLY -> deltas.hourly
            DeltaTimeframe.DAILY -> deltas.daily
            DeltaTimeframe.WEEKLY -> deltas.weekly
            DeltaTimeframe.MONTHLY -> deltas.monthly
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = when {
                    currentDelta.isPositive -> EmeraldPrimary.copy(alpha = 0.4f)
                    currentDelta.isNegative -> RoseExpense.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedTimeframe.title} (${selectedTimeframe.windowBadge})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${currentDelta.transactionCount} transactions recorded",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            currentDelta.isPositive -> "Total Increased:"
                            currentDelta.isNegative -> "Total Decreased:"
                            else -> "Net Asset Change:"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${if (currentDelta.netChange > 0) "+" else ""}${formatCurrency(currentDelta.netChange)}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = when {
                            currentDelta.isPositive -> EmeraldPrimary
                            currentDelta.isNegative -> RoseExpense
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "• Inflow: +${formatCurrency(currentDelta.inflow)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = EmeraldPrimary
                    )
                    Text(
                        text = "• Outflow: -${formatCurrency(currentDelta.outflow)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = RoseExpense
                    )
                }
            }
        }
    }
}

@Composable
fun TimeframeDeltaCard(
    timeframe: DeltaTimeframe,
    delta: CashAssetDelta,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    }

    val borderColor = if (isSelected) {
        when {
            delta.isPositive -> EmeraldPrimary
            delta.isNegative -> RoseExpense
            else -> BlueFintech
        }
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Label + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (timeframe) {
                        DeltaTimeframe.HOURLY -> Icons.Default.Schedule
                        DeltaTimeframe.DAILY -> Icons.Default.Today
                        DeltaTimeframe.WEEKLY -> Icons.Default.DateRange
                        DeltaTimeframe.MONTHLY -> Icons.Default.CalendarMonth
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = timeframe.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = when {
                        delta.isPositive -> EmeraldPrimary.copy(alpha = 0.15f)
                        delta.isNegative -> RoseExpense.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                delta.isPositive -> Icons.Default.ArrowUpward
                                delta.isNegative -> Icons.Default.ArrowDownward
                                else -> Icons.Default.Remove
                            },
                            contentDescription = null,
                            modifier = Modifier.size(9.dp),
                            tint = when {
                                delta.isPositive -> EmeraldPrimary
                                delta.isNegative -> RoseExpense
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = when {
                                delta.isPositive -> "Up"
                                delta.isNegative -> "Down"
                                else -> "Flat"
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                delta.isPositive -> EmeraldPrimary
                                delta.isNegative -> RoseExpense
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            // Delta Amount
            Text(
                text = "${if (delta.netChange > 0) "+" else ""}${formatCurrency(delta.netChange)}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                color = when {
                    delta.isPositive -> EmeraldPrimary
                    delta.isNegative -> RoseExpense
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Flow sub-text
            Text(
                text = "+${formatCurrency(delta.inflow)} / -${formatCurrency(delta.outflow)}",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Grid section that shows specific or custom range interval data (Requirement #3):
 * Allows exploring specific hour, specific date / date range, specific week, and specific month.
 */
@Composable
fun SpecificRangeExplorerGridSection(
    rangeData: SpecificRangeData,
    rangeMode: RangeExplorerMode,
    selectedHour: Int,
    hourDate: String,
    startDate: String,
    endDate: String?,
    weekOffset: Int,
    monthOffset: Int,
    onModeChange: (RangeExplorerMode) -> Unit,
    onHourChange: (Int) -> Unit,
    onHourDateChange: (String) -> Unit,
    onStartDateChange: (String) -> Unit,
    onEndDateChange: (String?) -> Unit,
    onWeekOffsetChange: (Int) -> Unit,
    onMonthOffsetChange: (Int) -> Unit,
    onSelectTransaction: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var showTransactionsList by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.testTag("specific_range_explorer_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = EmeraldPrimary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "SPECIFIC & RANGE EXPLORER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = rangeData.rangeLabel.ifBlank { rangeData.title },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Mode Selector Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RangeExplorerMode.values().forEach { mode ->
                val isSelected = mode == rangeMode
                FilterChip(
                    selected = isSelected,
                    onClick = { onModeChange(mode) },
                    label = { Text(mode.label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = EmeraldPrimary
                    ),
                    modifier = Modifier.height(32.dp).testTag("range_mode_${mode.name.lowercase(Locale.ROOT)}")
                )
            }
        }

        // Mode Controls
        when (rangeMode) {
            RangeExplorerMode.HOUR -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Date preset + Hour Stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val today = remember { MainViewModel.getTodayDateString() }
                            val yesterday = remember {
                                val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                                SimpleDateFormat("dd/MM/yyyy", Locale.US).format(c.time)
                            }
                            FilterChip(
                                selected = hourDate == today,
                                onClick = { onHourDateChange(today) },
                                label = { Text("Today", fontSize = 10.sp) },
                                modifier = Modifier.height(28.dp)
                            )
                            FilterChip(
                                selected = hourDate == yesterday,
                                onClick = { onHourDateChange(yesterday) },
                                label = { Text("Yesterday", fontSize = 10.sp) },
                                modifier = Modifier.height(28.dp)
                            )
                        }

                        // Stepper for Hour
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clickable { onHourChange(if (selectedHour > 0) selectedHour - 1 else 23) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text("-1h", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Text(
                                text = String.format(Locale.US, "%02d:00", selectedHour),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = EmeraldPrimary
                            )

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clickable { onHourChange(if (selectedHour < 23) selectedHour + 1 else 0) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text("+1h", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Quick Hour Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(8, 10, 12, 14, 16, 18, 20, 22).forEach { h ->
                            val active = selectedHour == h
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (active) EmeraldPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (active) androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary) else null,
                                modifier = Modifier
                                    .clickable { onHourChange(h) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%02d:00", h),
                                    fontSize = 10.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    color = if (active) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            RangeExplorerMode.DATE_RANGE -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Range Presets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val today = remember { MainViewModel.getTodayDateString() }
                        FilterChip(
                            selected = startDate == today && endDate == null,
                            onClick = {
                                onStartDateChange(today)
                                onEndDateChange(null)
                            },
                            label = { Text("Today Only", fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                        FilterChip(
                            selected = endDate != null,
                            onClick = {
                                val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
                                onStartDateChange(SimpleDateFormat("dd/MM/yyyy", Locale.US).format(c.time))
                                onEndDateChange(today)
                            },
                            label = { Text("Past 7 Days", fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
                                onStartDateChange(SimpleDateFormat("dd/MM/yyyy", Locale.US).format(c.time))
                                onEndDateChange(today)
                            },
                            label = { Text("Past 30 Days", fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    // Date Input Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startDate,
                            onValueChange = onStartDateChange,
                            label = { Text("Start Date", fontSize = 10.sp) },
                            placeholder = { Text("dd/MM/yyyy", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endDate ?: "",
                            onValueChange = { onEndDateChange(it.ifBlank { null }) },
                            label = { Text("End Date (opt)", fontSize = 10.sp) },
                            placeholder = { Text("dd/MM/yyyy", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            RangeExplorerMode.WEEK -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        0 to "This Week",
                        1 to "Last Week",
                        2 to "2 Wks Ago",
                        3 to "3 Wks Ago",
                        4 to "4 Wks Ago"
                    ).forEach { (off, lbl) ->
                        val isSelected = weekOffset == off
                        FilterChip(
                            selected = isSelected,
                            onClick = { onWeekOffsetChange(off) },
                            label = { Text(lbl, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = EmeraldPrimary
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            RangeExplorerMode.MONTH -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        0 to "This Month",
                        1 to "Last Month",
                        2 to "2 Mos Ago",
                        3 to "3 Mos Ago"
                    ).forEach { (off, lbl) ->
                        val isSelected = monthOffset == off
                        FilterChip(
                            selected = isSelected,
                            onClick = { onMonthOffsetChange(off) },
                            label = { Text(lbl, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = EmeraldPrimary
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        }

        // The 4-Card Interval Grid
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Card 1: Net Change
                Card(
                    modifier = Modifier.weight(1f).testTag("grid_net_change"),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            rangeData.isPositive -> EmeraldPrimary.copy(alpha = 0.12f)
                            rangeData.isNegative -> RoseExpense.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                    )
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("NET DELTA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(
                                imageVector = when {
                                    rangeData.isPositive -> Icons.Default.TrendingUp
                                    rangeData.isNegative -> Icons.Default.TrendingDown
                                    else -> Icons.Default.Remove
                                },
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = when {
                                    rangeData.isPositive -> EmeraldPrimary
                                    rangeData.isNegative -> RoseExpense
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Text(
                            text = "${if (rangeData.netChange > 0) "+" else ""}${formatCurrency(rangeData.netChange)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                rangeData.isPositive -> EmeraldPrimary
                                rangeData.isNegative -> RoseExpense
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = rangeData.statusLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                rangeData.isPositive -> EmeraldPrimary
                                rangeData.isNegative -> RoseExpense
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Card 2: Total Inflow
                Card(
                    modifier = Modifier.weight(1f).testTag("grid_total_inflow"),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("INFLOW", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(12.dp), tint = EmeraldPrimary)
                        }
                        Text(
                            text = "+${formatCurrency(rangeData.totalInflow)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("Credited income", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Card 3: Total Outflow
                Card(
                    modifier = Modifier.weight(1f).testTag("grid_total_outflow"),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("OUTFLOW", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(12.dp), tint = RoseExpense)
                        }
                        Text(
                            text = "-${formatCurrency(rangeData.totalOutflow)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = RoseExpense,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("Debits & fees", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Card 4: Transactions Count
                Card(
                    modifier = Modifier.weight(1f).testTag("grid_tx_count"),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("EVENTS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            text = "${rangeData.transactionCount}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Filtered rows", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * Notice card clarifying Instant Startup Mode and offering full historical scan on-demand.
 */
@Composable
fun InstantModeNoticeCard(
    isScanning: Boolean,
    scanProgress: ScanProgress,
    onOrderFullScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = EmeraldPrimary.copy(alpha = 0.15f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Instant Startup Mode",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Reads latest bank SMS alerts for super fast launch.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TextButton(
                onClick = onOrderFullScan,
                enabled = !isScanning,
                modifier = Modifier.testTag("order_full_history_scan_button")
            ) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp), tint = EmeraldPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Full History Scan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
            }
        }
    }
}

/**
 * Multi-Phone Internet Connection & Cloud Plans Modal Dialog (addresses Requirement #1).
 */
@Composable
fun MultiPhoneCloudPlansDialog(
    cloudState: CloudSyncState,
    phones: List<RegisteredPhoneEntity>,
    onUnlink: () -> Unit = {},
    onConfigureFirebase: (String, String, String) -> Unit = { _, _, _ -> },
    onConfigureRailway: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedPlanTab by remember { mutableStateOf(if (!cloudState.savedDatabaseUrl.isNullOrBlank()) 1 else 0) }
    var joinCodeInput by remember { mutableStateOf("") }
    var railwayUrlInput by remember(cloudState.savedDatabaseUrl) { mutableStateOf(cloudState.savedDatabaseUrl.orEmpty()) }
    var railwayTokenInput by remember { mutableStateOf("") }
    var showJoinDialog by remember { mutableStateOf(false) }

    var fbProjectId by remember(cloudState.savedProjectId) { mutableStateOf(cloudState.savedProjectId.orEmpty()) }
    var fbApiKey by remember(cloudState.savedApiKey) { mutableStateOf(cloudState.savedApiKey.orEmpty()) }
    var fbAppId by remember { mutableStateOf("com.aistudio.banksms.kzetqw") }
    var showSetupGuide by remember { mutableStateOf(false) }
    var copiedPinToast by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = EmeraldPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Internet Multi-Phone Sync", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Connect multiple phones over the internet (no local WiFi required) to aggregate all your bank SMS alerts and cash assets into one live cumulative dashboard.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Plan Tabs
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedPlanTab == 0,
                        onClick = { selectedPlanTab = 0 },
                        label = { Text("Firebase Real-Time Mesh", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(12.dp)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f))
                    )
                    FilterChip(
                        selected = selectedPlanTab == 1,
                        onClick = { selectedPlanTab = 1 },
                        label = { Text("Railway PostgreSQL / REST", fontSize = 11.sp) }
                    )
                }

                if (selectedPlanTab == 0) {
                    // Option 1: Firebase Real-Time Mesh (Active implementation)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Option 1: Firebase Cloud Mesh (100% Free - Spark Tier)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmeraldPrimary)
                            Text(
                                "• 100% Free: Google Firebase Spark plan includes 50,000 document reads & 20,000 writes/day with zero fees or billing required.\n" +
                                "• Automatic mesh sync across all devices configured with the same Project ID & Web API Key.\n" +
                                "• Each device uploads its database table automatically and allows 1-tap real-time data pulling.\n" +
                                "• End-to-end AES-256 encrypted payloads (raw SMS is never uploaded).",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Step-by-Step Setup Guide Accordion
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSetupGuide = !showSetupGuide }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp), tint = BlueFintech)
                                        Spacer(Modifier.width(6.dp))
                                        Text("How to Set Up Multi-Phone Sync (Step-by-Step)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BlueFintech)
                                    }
                                    Icon(
                                        imageVector = if (showSetupGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = BlueFintech
                                    )
                                }
                            }

                            AnimatedVisibility(visible = showSetupGuide) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BlueFintech.copy(alpha = 0.2f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Step 1: Open Free Firebase Console", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = EmeraldPrimary)
                                        Text("1. Open console.firebase.google.com on any browser (100% Free Spark plan, zero billing required).\n2. Create a project -> Build -> Firestore Database -> Create Database (Start in test mode).", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        Text("Step 2: Enter Project ID & Web API Key below", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = EmeraldPrimary)
                                        Text("Copy your Project ID and Web API Key from Firebase Project Settings into the inputs below and tap 'Connect & Sync'.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        Text("Step 3: Save same ID & Key on Phone 2", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = EmeraldPrimary)
                                        Text("Once saved on Phone 2, both phones automatically connect to the cloud mesh, upload their tables, and show up under 'Connected Phones' for 1-tap real-time syncing!", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            // Firebase Credentials Form
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Firebase Cloud Credentials:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    if (!cloudState.savedProjectId.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = EmeraldPrimary.copy(alpha = 0.2f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(10.dp), tint = EmeraldPrimary)
                                                Spacer(Modifier.width(3.dp))
                                                Text("Saved", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                                            }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = fbProjectId,
                                    onValueChange = { fbProjectId = it },
                                    label = { Text("Firebase Project ID", fontSize = 10.sp) },
                                    placeholder = { Text("e.g. my-banksms-project", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("fb_project_id_input")
                                )
                                OutlinedTextField(
                                    value = fbApiKey,
                                    onValueChange = { fbApiKey = it },
                                    label = { Text("Firebase Web API Key", fontSize = 10.sp) },
                                    placeholder = { Text("AIzaSy...", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("fb_api_key_input")
                                )
                                Button(
                                    onClick = { onConfigureFirebase(fbProjectId, fbApiKey, fbAppId) },
                                    enabled = fbProjectId.isNotBlank() && fbApiKey.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    modifier = Modifier.fillMaxWidth().testTag("save_firebase_config_button")
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Connect & Save Credentials", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                } else {
                    // Option 2: Railway PostgreSQL / Custom Backend
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Option 2: Railway PostgreSQL / REST Sync", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BlueFintech)
                            Text(
                                "To sync through your own hosted Railway PostgreSQL instance or custom REST backend, specify your endpoint URL and auth token below:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = railwayUrlInput,
                                onValueChange = { railwayUrlInput = it },
                                label = { Text("Railway Endpoint / DB URL", fontSize = 11.sp) },
                                placeholder = { Text("https://my-app.railway.app/api/sync", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = railwayTokenInput,
                                onValueChange = { railwayTokenInput = it },
                                label = { Text("Auth API Key / Token", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    onConfigureRailway(railwayUrlInput.trim())
                                    onDismiss()
                                },
                                enabled = railwayUrlInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = BlueFintech),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Connect to Railway Instance", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Currently Registered Phones
                Text("Connected Phones (${phones.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    phones.forEach { p ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("• ${p.friendlyName} (${p.modelName})", fontSize = 11.sp)
                            if (p.isCurrentDevice) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = EmeraldPrimary.copy(alpha = 0.2f)
                                ) {
                                    Text("This device", fontSize = 9.sp, color = EmeraldPrimary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Calendar Date Control Bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardDateControlBar(
    selectedDate: String?,
    onSelectDate: (String?) -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onAllTime: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val todayStr = MainViewModel.getTodayDateString()
    val isToday = selectedDate == todayStr
    val isAllTime = selectedDate == null

    val displayText = when {
        isAllTime -> "All Time"
        isToday -> "Today ($selectedDate)"
        else -> selectedDate ?: "Today"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Prev Day Button
                IconButton(
                    onClick = onPrevDay,
                    enabled = !isAllTime,
                    modifier = Modifier.size(36.dp).testTag("calendar_prev_day")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Day",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Date Picker Trigger Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showDatePickerDialog = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("calendar_date_picker_trigger"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Calendar",
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = displayText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Next Day Button
                IconButton(
                    onClick = onNextDay,
                    enabled = !isAllTime,
                    modifier = Modifier.size(36.dp).testTag("calendar_next_day")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Day",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Quick Selector Chips: "Today", "All Time"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = isToday,
                onClick = onToday,
                label = { Text("Today", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                    selectedLabelColor = EmeraldPrimary
                ),
                modifier = Modifier.testTag("chip_today")
            )

            FilterChip(
                selected = isAllTime,
                onClick = onAllTime,
                label = { Text("All Time", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PurpleFintech.copy(alpha = 0.2f),
                    selectedLabelColor = PurpleFintech
                ),
                modifier = Modifier.testTag("chip_all_time")
            )
        }
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US)
                            onSelectDate(sdf.format(java.util.Date(selectedMillis)))
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text("Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
