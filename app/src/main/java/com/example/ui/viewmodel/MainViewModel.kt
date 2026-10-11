package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.CategoryEntity
import com.example.data.entity.FlowDirection
import com.example.data.entity.ManualSourceEntity
import com.example.data.entity.ManualSourceKind
import com.example.data.entity.FinancialSourceEntity
import com.example.data.entity.IncomeSourceEntity
import com.example.data.entity.InternalTransferConfigEntity
import com.example.data.entity.MatchMethod
import com.example.data.entity.RegisteredPhoneEntity
import com.example.data.entity.SavingsGoalEntity
import com.example.data.entity.SenderIdentifierType
import com.example.data.entity.SmsFormatEntity
import com.example.data.entity.SmsSenderIdentifierEntity
import com.example.data.entity.SourceType
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransactionType
import com.example.data.entity.WealthItemEntity
import com.example.data.repository.FinancialRepository
import com.example.data.repository.IngestionResult
import com.example.parser.ParsedSmsResult
import com.example.parser.SenderTestResult
import com.example.parser.SmsParserEngine
import android.content.Context
import com.example.data.entity.RawSmsEntity
import com.example.service.InboxScanSummary
import com.example.service.ScanProgress
import com.example.service.SmsSyncCoordinator
import com.example.sync.CloudSyncManager
import com.example.sync.CloudSyncState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.example.ai.AdvisorActions
import com.example.ai.AdvisorAgent
import com.example.ai.AdvisorCapitalPlan
import com.example.ai.AdvisorDataView
import com.example.ai.AdvisorIntelligence
import com.example.ai.AdvisorToolExecutor
import com.example.ai.BriefingStore
import com.example.ai.AgentMemoryService
import com.example.ai.AgentMemoryState
import com.example.ai.MemoryKind
import com.example.ai.MemorySource
import com.example.ai.UserProfile
import com.example.ai.UserProfileService
import com.example.ai.CategoryLearner
import com.example.ai.WeeklyBriefing
import com.example.service.WeeklyBriefingScheduler
import com.example.BirrFlowApplication
import com.example.ai.DecisionTracker
import com.example.ai.CategorySuggestion
import com.example.ai.InvestmentCatalog
import com.example.ai.NetWorthCalculator
import com.example.ai.NetWorthSummary
import com.example.ai.MarketDataService
import com.example.ai.MarketIndicators
import com.example.ai.RankingPreferences
import com.example.ai.MarketSnapshot
import com.example.ai.AdvisorChatMessage
import com.example.ai.AdvisorFinancialSnapshot
import com.example.ai.AdvisorOpportunity
import com.example.ai.AdvisorPersistenceService
import com.example.ai.AdvisorUsageLog
import com.example.ai.ConnectionTestResult
import com.example.ai.ExecutiveSummary
import com.example.ai.FinancialSnapshotService
import com.example.ai.GeminiClientService
import com.example.ai.GeminiConfig
import com.example.ai.GeminiConnectionStatus
import com.example.ai.MilestoneStatus
import com.example.ai.PlanMilestone
import com.example.ai.SupportedGeminiModels

enum class DateRangeFilter(val label: String) {
    THIS_MONTH("This Month"),
    PREVIOUS_MONTH("Last Month"),
    LAST_3_MONTHS("Last 3 Months"),
    LAST_6_MONTHS("Last 6 Months"),
    ALL_TIME("All Time")
}

data class CashAssetDelta(
    val netChange: Double = 0.0,
    val inflow: Double = 0.0,
    val outflow: Double = 0.0,
    val transactionCount: Int = 0
) {
    val isPositive: Boolean get() = netChange > 0.0001
    val isNegative: Boolean get() = netChange < -0.0001
    val isNeutral: Boolean get() = !isPositive && !isNegative
    val absNetChange: Double get() = kotlin.math.abs(netChange)
    val statusLabel: String
        get() = when {
            isPositive -> "Increased"
            isNegative -> "Decreased"
            else -> "No change"
        }
}

data class TimeframeDeltas(
    val hourly: CashAssetDelta = CashAssetDelta(),
    val daily: CashAssetDelta = CashAssetDelta(),
    val weekly: CashAssetDelta = CashAssetDelta(),
    val monthly: CashAssetDelta = CashAssetDelta()
)

data class TotalCashAssetSummary(
    val totalCashAsset: Double = 0.0,
    val bankAndWalletBalance: Double = 0.0,
    val manualAndOtherIncomeBalance: Double = 0.0,
    val totalInflowToday: Double = 0.0,
    val totalOutflowToday: Double = 0.0,
    val netFlowToday: Double = 0.0,
    val activePhoneCount: Int = 1,
    val isMultiPhoneLinked: Boolean = false,
    val linkedPhoneNames: List<String> = emptyList(),
    val deltas: TimeframeDeltas = TimeframeDeltas()
)

enum class RangeExplorerMode(val label: String) {
    HOUR("Specific Hour"),
    DATE_RANGE("Specific Date / Range"),
    WEEK("Specific Week"),
    MONTH("Specific Month")
}

data class SpecificRangeData(
    val mode: RangeExplorerMode = RangeExplorerMode.DATE_RANGE,
    val title: String = "Today",
    val rangeLabel: String = "",
    val totalInflow: Double = 0.0,
    val totalOutflow: Double = 0.0,
    val netChange: Double = 0.0,
    val transactionCount: Int = 0,
    val transactions: List<TransactionEntity> = emptyList()
) {
    val isPositive: Boolean get() = netChange > 0.0001
    val isNegative: Boolean get() = netChange < -0.0001
    val isNeutral: Boolean get() = !isPositive && !isNegative
    val statusLabel: String
        get() = when {
            isPositive -> "Increased"
            isNegative -> "Decreased"
            else -> "Flat"
        }
}

data class RangeExplorerParams(
    val hourDate: String,
    val startDate: String,
    val endDate: String?,
    val weekOffset: Int,
    val monthOffset: Int
)

data class FinancialOverview(
    val totalIncome: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val totalInternalTransfers: Double = 0.0,
    val netCashFlow: Double = 0.0,
    val transactionCount: Int = 0
)

data class CombinedCalculationBreakdown(
    val totalCreditedIncome: Double = 0.0,
    val totalDebitedExpense: Double = 0.0,
    val internalTransfersDebit: Double = 0.0,
    val internalTransfersCredit: Double = 0.0,
    val totalInternalTransfers: Double = 0.0,
    val netCashFlow: Double = 0.0,
    val phoneBreakdowns: List<PhoneCalculationDetail> = emptyList(),
    val dateLabel: String = "Today",
    val transactionCount: Int = 0
)

data class PhoneCalculationDetail(
    val phoneId: String,
    val phoneName: String,
    val creditedIncome: Double,
    val debitedExpense: Double,
    val internalDebit: Double,
    val internalCredit: Double,
    val netCashFlow: Double,
    val txCount: Int
)

data class SourceMetric(
    val source: FinancialSourceEntity,
    val income: Double = 0.0,
    val expenses: Double = 0.0,
    val internalTransfers: Double = 0.0,
    val transactionCount: Int = 0,
    val lastKnownBalance: Double? = null
)

data class PhoneMetric(
    val phone: RegisteredPhoneEntity,
    val income: Double = 0.0,
    val expenses: Double = 0.0,
    val internalTransfers: Double = 0.0,
    val transactionCount: Int = 0
)

enum class BalanceGrouping(val label: String) {
    BY_SOURCE("By Bank"),
    BY_PHONE("By Phone")
}

enum class FlowFilter(val label: String) {
    ALL("All"),
    CREDIT("Credit"),
    DEBIT("Debit")
}

/** One line of the "Latest bank balances & activity" card (a bank, a phone, or a manual source). */
data class BalanceRow(
    val key: String,
    val title: String,
    val subtitle: String,
    val balance: Double?,
    val credit: Double = 0.0,
    val debit: Double = 0.0,
    val creditCount: Int = 0,
    val debitCount: Int = 0,
    val recoveredCount: Int = 0,
    val isManual: Boolean = false,
    val manualSource: ManualSourceEntity? = null,
    val sourceId: String? = null,
    val phoneId: String? = null,
    val isDisabled: Boolean = false,
    val children: List<BalanceRow> = emptyList()
)

data class BalanceBoard(
    val rows: List<BalanceRow> = emptyList(),
    val smsBalanceTotal: Double = 0.0,
    val manualBalanceTotal: Double = 0.0,
    val totalCredit: Double = 0.0,
    val totalDebit: Double = 0.0,
    val creditCount: Int = 0,
    val debitCount: Int = 0,
    val grouping: BalanceGrouping = BalanceGrouping.BY_SOURCE,
    val flowFilter: FlowFilter = FlowFilter.ALL
) {
    val totalBalance: Double get() = smsBalanceTotal + manualBalanceTotal
}

data class CategorySpend(
    val category: CategoryEntity,
    val totalAmount: Double,
    val percentage: Float,
    val transactionCount: Int = 0,
    // Synthetic row summing debits that are still waiting for a manual category
    val isUncategorized: Boolean = false
)

/** Placeholder category for the "Uncategorized" row of the spending breakdown. */
val UNCATEGORIZED_SPEND_CATEGORY = CategoryEntity(
    categoryId = "uncategorized",
    name = "Uncategorized",
    iconName = "category",
    colorHex = "#F43F5E",
    isCustom = false
)

/** Period shown by the dashboard charts (independent of the single-day dashboard filter). */
enum class ChartRange(val label: String, val bucketCount: Int, val isMonthly: Boolean) {
    DAYS_7("7 days", 7, false),
    DAYS_30("30 days", 30, false),
    MONTHS_6("6 months", 6, true),
    MONTHS_12("12 months", 12, true)
}

/** Money in / out for one day or month of the chart range. */
data class FlowBucket(
    val label: String,
    val longLabel: String,
    val startMillis: Long,
    val inflow: Double = 0.0,
    val outflow: Double = 0.0,
    val transactionCount: Int = 0
) {
    val net: Double get() = inflow - outflow
}

data class IncomeSlice(
    val name: String,
    val colorHex: String,
    val amount: Double,
    val transactionCount: Int
)

data class DashboardChartData(
    val range: ChartRange = ChartRange.DAYS_7,
    val buckets: List<FlowBucket> = emptyList(),
    // Running total of net cash flow at the end of each bucket
    val cumulativeNet: List<Double> = emptyList(),
    val spending: List<CategorySpend> = emptyList(),
    val incomeBySource: List<IncomeSlice> = emptyList()
) {
    val totalInflow: Double get() = buckets.sumOf { it.inflow }
    val totalOutflow: Double get() = buckets.sumOf { it.outflow }
    val hasData: Boolean get() = buckets.any { it.transactionCount > 0 }
}

/** Advisor state that feeds the snapshot alongside balances and transactions. */
data class AdvisorInputs(
    val plan: AdvisorCapitalPlan,
    val market: MarketSnapshot,
    val wealth: List<WealthItemEntity>,
    val profile: UserProfile
)

class MainViewModel(
    private val repository: FinancialRepository,
    private val smsSync: SmsSyncCoordinator,
    private val cloudSync: CloudSyncManager
) : ViewModel() {

    // Filter states
    val selectedPhoneId = MutableStateFlow<String?>(null) // null = All Phones
    val selectedSourceId = MutableStateFlow<String?>(null) // null = All Sources
    val selectedDateRange = MutableStateFlow(DateRangeFilter.ALL_TIME)
    val selectedTransactionType = MutableStateFlow<TransactionType?>(null)
    val searchQuery = MutableStateFlow("")

    // Date / Calendar State (Defaults to Today: "dd/MM/yyyy")
    val selectedDate = MutableStateFlow<String?>(getTodayDateString())

    // Direct detail dialog modal controller (for notification deep link or in-app selection)
    val activeTransactionForDetail = MutableStateFlow<TransactionEntity?>(null)

    fun openTransactionDetailById(txId: String) {
        viewModelScope.launch {
            val tx = repository.getTransactionById(txId)
            if (tx != null) {
                activeTransactionForDetail.value = tx
            }
        }
    }

    fun dismissActiveTransactionDetail() {
        activeTransactionForDetail.value = null
    }

    // Raw repository flows
    val allPhones = repository.allPhones.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allSources = repository.allSources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allSenderIdentifiers = repository.allSenderIdentifiers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allFormats = repository.allFormats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allCategories = repository.allCategories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allIncomeSources = repository.allIncomeSources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val uncategorizedExpenses = repository.uncategorizedExpenses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allInternalConfigs = repository.allInternalConfigs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allSavingsGoals = repository.allSavingsGoals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val manualSources = repository.manualSources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val wealthItems = repository.wealthItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // One shared Room subscription for every derived dashboard flow (instead of one query per consumer)
    private val allTransactions: StateFlow<List<TransactionEntity>> =
        repository.allTransactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /** Every transaction, ignoring the dashboard's day/phone/bank filters (income source totals, charts). */
    val allTransactionsUnfiltered: StateFlow<List<TransactionEntity>> get() = allTransactions
    val failedOrUnknownSms = repository.getFailedOrUnknownSms().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val unknownSenderSms = repository.unknownSenderSms.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentAuditLogs = repository.recentAuditLogs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Range Explorer states
    val rangeExplorerMode = MutableStateFlow(RangeExplorerMode.DATE_RANGE)
    val selectedExplorerHour = MutableStateFlow(12) // 0..23
    val selectedExplorerHourDate = MutableStateFlow(getTodayDateString()) // dd/MM/yyyy
    val explorerStartDate = MutableStateFlow(getTodayDateString()) // dd/MM/yyyy
    val explorerEndDate = MutableStateFlow<String?>(null) // null = single day
    val explorerWeekOffset = MutableStateFlow(0) // 0 = This Week, 1 = Last Week, etc.
    val explorerMonthOffset = MutableStateFlow(0) // 0 = This Month, 1 = Last Month, etc.

    private val rangeExplorerParams = combine(
        selectedExplorerHourDate,
        explorerStartDate,
        explorerEndDate,
        explorerWeekOffset,
        explorerMonthOffset
    ) { hDate: String, sDate: String, eDate: String?, wOff: Int, mOff: Int ->
        RangeExplorerParams(hDate, sDate, eDate, wOff, mOff)
    }

    val specificRangeData: StateFlow<SpecificRangeData> = combine(
        allTransactions,
        selectedPhoneId,
        rangeExplorerMode,
        selectedExplorerHour,
        rangeExplorerParams
    ) { allTxs: List<TransactionEntity>, phoneId: String?, mode: RangeExplorerMode, hour: Int, params: RangeExplorerParams ->
        val scoped = if (phoneId != null) allTxs.filter { it.phoneId == phoneId } else allTxs
        computeSpecificRangeData(scoped, mode, hour, params)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpecificRangeData())

    // Admin Lab Testing states
    val testSenderName = MutableStateFlow("")
    val testSenderNumber = MutableStateFlow("")
    val testSenderResult = MutableStateFlow<SenderTestResult?>(null)

    val testParserInput = MutableStateFlow("")
    val testSelectedSender = MutableStateFlow("telebirr")
    val testResult = MutableStateFlow<ParsedSmsResult?>(null)

    // Intermediate Filter Combine to avoid >5 flow overload
    private val filterCriteria = combine(
        selectedPhoneId,
        selectedSourceId,
        selectedTransactionType,
        selectedDate,
        searchQuery
    ) { pId, sId, type, date, query ->
        FilterCriteria(pId, sId, type, date, query)
    }

    // Filtered Transactions with Date Filter support
    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        allTransactions,
        filterCriteria
    ) { txList, crit ->
        val day = crit.date?.let { DayMatcher.of(it) }
        txList.filter { tx ->
            val matchPhone = crit.phoneId == null || tx.phoneId == crit.phoneId
            val matchSource = crit.sourceId == null || tx.sourceId == crit.sourceId
            val matchType = crit.type == null || tx.transactionType == crit.type
            val matchDate = day == null || day.matches(tx.transactionDate, tx.createdTimestamp)
            val matchQuery = crit.query.isBlank() ||
                    tx.description.contains(crit.query, ignoreCase = true) ||
                    (tx.referenceNumber?.contains(crit.query, ignoreCase = true) == true) ||
                    (tx.sender?.contains(crit.query, ignoreCase = true) == true) ||
                    (tx.recipient?.contains(crit.query, ignoreCase = true) == true) ||
                    (tx.merchant?.contains(crit.query, ignoreCase = true) == true)
            matchPhone && matchSource && matchType && matchDate && matchQuery
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Financial Overview summary
    val financialOverview: StateFlow<FinancialOverview> = filteredTransactions.combine(selectedPhoneId) { txList, _ ->
        var income = 0.0
        var expenses = 0.0
        var internalTransfers = 0.0

        for (tx in txList) {
            when (tx.transactionType) {
                TransactionType.INCOME, TransactionType.DEPOSIT -> income += tx.amount
                TransactionType.EXPENSE, TransactionType.WITHDRAWAL, TransactionType.FEE -> expenses += (tx.amount + tx.fee)
                TransactionType.INTERNAL_TRANSFER -> internalTransfers += tx.amount
                else -> Unit
            }
        }

        FinancialOverview(
            totalIncome = income,
            totalExpenses = expenses,
            totalInternalTransfers = internalTransfers,
            netCashFlow = income - expenses, // Total Credited - Total Debited (strictly excludes internal transfers)
            transactionCount = txList.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinancialOverview())

    // Combined Calculation Breakdown (How All Phones combined data is calculated)
    val combinedCalculationBreakdown: StateFlow<CombinedCalculationBreakdown> = combine(
        filteredTransactions,
        allPhones,
        selectedDate
    ) { txList, phones, date ->
        var income = 0.0
        var expenses = 0.0
        var internalDebit = 0.0
        var internalCredit = 0.0

        val phoneTxGroups = txList.groupBy { it.phoneId }

        val phoneDetails = phones.map { p ->
            val pTxs = phoneTxGroups[p.phoneId] ?: emptyList()
            var pInc = 0.0
            var pExp = 0.0
            var pIntDeb = 0.0
            var pIntCred = 0.0

            for (tx in pTxs) {
                when (tx.transactionType) {
                    TransactionType.INCOME, TransactionType.DEPOSIT -> pInc += tx.amount
                    TransactionType.EXPENSE, TransactionType.WITHDRAWAL, TransactionType.FEE -> pExp += (tx.amount + tx.fee)
                    TransactionType.INTERNAL_TRANSFER -> {
                        if (isDebitInternalTransfer(tx)) {
                            pIntDeb += tx.amount
                        } else {
                            pIntCred += tx.amount
                        }
                    }
                    else -> Unit
                }
            }

            PhoneCalculationDetail(
                phoneId = p.phoneId,
                phoneName = p.friendlyName,
                creditedIncome = pInc,
                debitedExpense = pExp,
                internalDebit = pIntDeb,
                internalCredit = pIntCred,
                netCashFlow = pInc - pExp, // Net cash flow = total credited - total debited
                txCount = pTxs.size
            )
        }

        for (tx in txList) {
            when (tx.transactionType) {
                TransactionType.INCOME, TransactionType.DEPOSIT -> income += tx.amount
                TransactionType.EXPENSE, TransactionType.WITHDRAWAL, TransactionType.FEE -> expenses += (tx.amount + tx.fee)
                TransactionType.INTERNAL_TRANSFER -> {
                    if (isDebitInternalTransfer(tx)) {
                        internalDebit += tx.amount
                    } else {
                        internalCredit += tx.amount
                    }
                }
                else -> Unit
            }
        }

        val dateStr = if (date == null) "All Time" else if (date == getTodayDateString()) "Today ($date)" else date

        CombinedCalculationBreakdown(
            totalCreditedIncome = income,
            totalDebitedExpense = expenses,
            internalTransfersDebit = internalDebit,
            internalTransfersCredit = internalCredit,
            totalInternalTransfers = internalDebit + internalCredit,
            netCashFlow = income - expenses,
            phoneBreakdowns = phoneDetails,
            dateLabel = dateStr,
            transactionCount = txList.size
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CombinedCalculationBreakdown())

    // Bank / Source Metrics Breakdown
    val sourceMetrics: StateFlow<List<SourceMetric>> = combine(
        allSources,
        allTransactions,
        selectedPhoneId
    ) { sources, transactions, phoneId ->
        val relevantTx = if (phoneId != null) transactions.filter { it.phoneId == phoneId } else transactions
        sources.map { src ->
            val txForSource = relevantTx.filter { it.sourceId == src.sourceId }
            var inc = 0.0
            var exp = 0.0
            var intern = 0.0

            for (tx in txForSource) {
                when (tx.transactionType) {
                    TransactionType.INCOME, TransactionType.DEPOSIT -> inc += tx.amount
                    TransactionType.EXPENSE, TransactionType.WITHDRAWAL, TransactionType.FEE -> exp += (tx.amount + tx.fee)
                    TransactionType.INTERNAL_TRANSFER -> intern += tx.amount
                    else -> Unit
                }
            }

            // Latest balance = sum of the newest end balance of every account (per phone) of this bank
            val lastBal = latestBalances(txForSource).values.takeIf { it.isNotEmpty() }?.sum()

            SourceMetric(
                source = src,
                income = inc,
                expenses = exp,
                internalTransfers = intern,
                transactionCount = txForSource.size,
                lastKnownBalance = lastBal
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Phone Metrics Breakdown
    val phoneMetrics: StateFlow<List<PhoneMetric>> = combine(
        allPhones,
        allTransactions
    ) { phones, transactions ->
        phones.map { phone ->
            val txForPhone = transactions.filter { it.phoneId == phone.phoneId }
            var inc = 0.0
            var exp = 0.0
            var intern = 0.0

            for (tx in txForPhone) {
                when (tx.transactionType) {
                    TransactionType.INCOME, TransactionType.DEPOSIT -> inc += tx.amount
                    TransactionType.EXPENSE, TransactionType.WITHDRAWAL, TransactionType.FEE -> exp += (tx.amount + tx.fee)
                    TransactionType.INTERNAL_TRANSFER -> intern += tx.amount
                    else -> Unit
                }
            }

            PhoneMetric(
                phone = phone,
                income = inc,
                expenses = exp,
                internalTransfers = intern,
                transactionCount = txForPhone.size
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Category Spending Breakdown: every manual category with its amount, plus the debits still uncategorized
    val categorySpends: StateFlow<List<CategorySpend>> = combine(
        allCategories,
        filteredTransactions
    ) { categories, transactions ->
        computeCategorySpends(categories, transactions)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dashboard charts: last N days/months for the selected phone & bank (ignores the single-day filter)
    val chartRange = MutableStateFlow(ChartRange.DAYS_7)
    fun setChartRange(range: ChartRange) { chartRange.value = range }

    val dashboardCharts: StateFlow<DashboardChartData> = combine(
        allTransactions,
        combine(selectedPhoneId, selectedSourceId) { phoneId, sourceId -> phoneId to sourceId },
        chartRange,
        allCategories,
        allIncomeSources
    ) { txs, scope, range, categories, incomeSources ->
        val (phoneId, sourceId) = scope
        val scoped = txs.filter { (phoneId == null || it.phoneId == phoneId) && (sourceId == null || it.sourceId == sourceId) }
        computeDashboardCharts(scoped, categories, incomeSources, range)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardChartData())

    // Learned category suggestions for the pending debits (retrained whenever anything is categorized)
    val categorySuggestions: StateFlow<Map<String, List<CategorySuggestion>>> = combine(
        uncategorizedExpenses,
        allTransactions,
        allCategories
    ) { pending, txs, categories ->
        if (pending.isEmpty() || categories.isEmpty()) {
            emptyMap()
        } else {
            val learner = CategoryLearner.train(txs, categories)
            pending.take(300).associate { it.transactionId to learner.suggest(it) }.filterValues { it.isNotEmpty() }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val autoFileEnabled = MutableStateFlow(false)
    // Top-suggestion hits / total choices, shown as the learner's accuracy
    val suggestionStats = MutableStateFlow(0 to 0)

    fun setAutoFileEnabled(enabled: Boolean) {
        autoFileEnabled.value = enabled
        viewModelScope.launch { repository.setAutoFileEnabled(enabled) }
    }

    // Inbox Scanner state (driven by the app-wide coordinator: incremental, observer-triggered)
    val scanProgress: StateFlow<ScanProgress> = smsSync.progress
    val isScanningInbox: StateFlow<Boolean> = smsSync.progress.map { it.isRunning }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val inboxScanSummary: StateFlow<InboxScanSummary?> = smsSync.lastSummary

    @Suppress("UNUSED_PARAMETER")
    fun scanPhoneInbox(context: Context? = null) = smsSync.requestScan(fullRescan = false, userInitiated = true)

    /** Re-reads the whole inbox (dedupe keeps it safe); normally never needed. */
    fun fullRescanInbox() = smsSync.requestScan(fullRescan = true, userInitiated = true)

    fun onSmsPermissionGranted() = smsSync.onSmsPermissionGranted()

    fun dismissInboxScanSummary() = smsSync.dismissSummary()

    suspend fun getRawSms(rawSmsId: String): RawSmsEntity? = repository.getRawSmsById(rawSmsId)

    // ------------------------------------------------------------------
    // Latest bank balances & activity board
    // ------------------------------------------------------------------
    val balanceGrouping = MutableStateFlow(BalanceGrouping.BY_SOURCE)
    val balanceFlowFilter = MutableStateFlow(FlowFilter.ALL)
    val disabledSourceIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch {
            val savedDisabled = repository.getState("DISABLED_SOURCE_IDS")
            if (!savedDisabled.isNullOrBlank()) {
                disabledSourceIds.value = savedDisabled.split(",").filter { it.isNotBlank() }.toSet()
            }
        }
    }

    fun toggleSourceDisabled(sourceKey: String) {
        disabledSourceIds.update { set ->
            val updated = if (sourceKey in set) set - sourceKey else set + sourceKey
            viewModelScope.launch {
                repository.putState("DISABLED_SOURCE_IDS", updated.joinToString(","))
            }
            updated
        }
    }

    private val boardInputs = combine(
        combine(allSources, allPhones, manualSources) { s, p, m -> Triple(s, p, m) },
        combine(balanceGrouping, balanceFlowFilter, disabledSourceIds) { g, f, d -> Triple(g, f, d) }
    ) { (s, p, m), (g, f, d) ->
        BoardInputs(s, p, m, g, f, d)
    }

    val balanceBoard: StateFlow<BalanceBoard> = combine(
        allTransactions,
        boardInputs,
        selectedPhoneId,
        selectedDate
    ) { txs, inputs, phoneId, date ->
        buildBalanceBoard(txs, inputs, phoneId, date)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BalanceBoard())

    val totalCashAssetSummary: StateFlow<TotalCashAssetSummary> = combine(
        combine(balanceBoard, allTransactions, allPhones) { b, txs, p -> Triple(b, txs, p) },
        combine(cloudSync.state, disabledSourceIds) { s, d -> Pair(s, d) }
    ) { (board, allTxs, phones), (syncState, disabledIds) ->
        // Asset increase/decrease shows the summation of ALL devices data
        val validTxs = allTxs.filter { tx ->
            val sid = tx.sourceId
            val pid = tx.phoneId
            sid !in disabledIds &&
            pid !in disabledIds &&
            "$sid|$pid" !in disabledIds &&
            "$pid|$sid" !in disabledIds
        }
        val deltas = computeTimeframeDeltas(validTxs)

        var inflowToday = 0.0
        var outflowToday = 0.0
        val todayStr = getTodayDateString()
        val startOfToday = getStartOfTodayMillis()

        for (tx in validTxs) {
            val isToday = tx.transactionDate == todayStr || tx.createdTimestamp >= startOfToday
            if (isToday) {
                when (tx.transactionType) {
                    TransactionType.INCOME, TransactionType.DEPOSIT -> inflowToday += tx.amount
                    TransactionType.EXPENSE, TransactionType.WITHDRAWAL, TransactionType.FEE -> outflowToday += (tx.amount + tx.fee)
                    else -> Unit
                }
            }
        }

        TotalCashAssetSummary(
            totalCashAsset = board.totalBalance,
            bankAndWalletBalance = board.smsBalanceTotal,
            manualAndOtherIncomeBalance = board.manualBalanceTotal,
            totalInflowToday = inflowToday,
            totalOutflowToday = outflowToday,
            netFlowToday = inflowToday - outflowToday,
            activePhoneCount = phones.size.coerceAtLeast(1),
            isMultiPhoneLinked = syncState.linked,
            linkedPhoneNames = phones.map { it.friendlyName },
            deltas = deltas
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TotalCashAssetSummary())

    fun saveManualSource(
        existing: ManualSourceEntity?,
        name: String,
        kind: ManualSourceKind,
        balance: Double,
        phoneId: String?,
        note: String
    ) {
        viewModelScope.launch {
            val base = existing ?: ManualSourceEntity(
                manualSourceId = "MAN-" + java.util.UUID.randomUUID().toString().replace("-", "").take(12).uppercase(Locale.ROOT),
                name = name
            )
            repository.upsertManualSource(
                base.copy(name = name.trim(), kind = kind, balance = balance, phoneId = phoneId, note = note.trim(), updatedAt = System.currentTimeMillis())
            )
        }
    }

    fun adjustManualSourceBalance(source: ManualSourceEntity, delta: Double) {
        viewModelScope.launch {
            val newBalance = (source.balance + delta).coerceAtLeast(0.0)
            repository.upsertManualSource(
                source.copy(
                    balance = newBalance,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteManualSource(source: ManualSourceEntity) {
        viewModelScope.launch { repository.deleteManualSource(source.manualSourceId) }
    }

    fun quickScanLatestSms(userInitiated: Boolean = true) = smsSync.requestQuickScan(userInitiated = userInitiated)
    fun orderFullHistoricalScan(onProgress: (ScanProgress) -> Unit = {}) = smsSync.requestFullHistoricalScan(onProgress)

    /** Pulls new data from all synced phones, pushes any local pending new transactions, and scans latest local SMS */
    fun syncAllDevicesData() {
        cloudSync.pushPendingNow()
        syncAllDevices()
        quickScanLatestSms(userInitiated = false)
    }

    // ------------------------------------------------------------------
    // Real-time cloud sync between the user's phones
    // ------------------------------------------------------------------
    val cloudSyncState: StateFlow<CloudSyncState> = cloudSync.state

    fun triggerSyncAndUpload() { cloudSync.triggerSyncAndUpload() }
    fun syncDevice(remoteDeviceId: String) { cloudSync.syncDevice(remoteDeviceId) }
    fun unsyncDevice(remoteDeviceId: String) { cloudSync.unsyncDevice(remoteDeviceId) }
    fun syncAllDevices() { cloudSync.syncAllDevices() }
    fun unlinkSync() { cloudSync.unlink() }
    fun clearSyncMessage() = cloudSync.clearMessage()
    fun configureFirebase(projectId: String, apiKey: String, appId: String) {
        viewModelScope.launch { cloudSync.configureFirebase(projectId, apiKey, appId) }
    }
    fun configureRailway(databaseUrl: String) {
        viewModelScope.launch { cloudSync.configureRailway(databaseUrl) }
    }

    // Range Explorer setters
    fun setRangeExplorerMode(mode: RangeExplorerMode) { rangeExplorerMode.value = mode }
    fun setExplorerHour(hour: Int) { selectedExplorerHour.value = hour.coerceIn(0, 23) }
    fun setExplorerHourDate(date: String) { selectedExplorerHourDate.value = date }
    fun setExplorerStartDate(date: String) { explorerStartDate.value = date }
    fun setExplorerEndDate(date: String?) { explorerEndDate.value = date }
    fun setExplorerWeekOffset(offset: Int) { explorerWeekOffset.value = offset.coerceAtLeast(0) }
    fun setExplorerMonthOffset(offset: Int) { explorerMonthOffset.value = offset.coerceAtLeast(0) }

    // Calendar Navigation Functions
    fun selectDate(date: String?) {
        selectedDate.value = date
    }

    fun selectDateFromEpoch(millis: Long) {
        val d = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(millis))
        selectedDate.value = d
    }

    fun goToPreviousDay() {
        val current = selectedDate.value ?: getTodayDateString()
        try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
            val d = sdf.parse(current) ?: Date()
            val cal = Calendar.getInstance().apply {
                time = d
                add(Calendar.DAY_OF_YEAR, -1)
            }
            selectedDate.value = sdf.format(cal.time)
        } catch (_: Exception) {
            selectedDate.value = getTodayDateString()
        }
    }

    fun goToNextDay() {
        val current = selectedDate.value ?: getTodayDateString()
        try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
            val d = sdf.parse(current) ?: Date()
            val cal = Calendar.getInstance().apply {
                time = d
                add(Calendar.DAY_OF_YEAR, 1)
            }
            selectedDate.value = sdf.format(cal.time)
        } catch (_: Exception) {
            selectedDate.value = getTodayDateString()
        }
    }

    fun goToToday() {
        selectedDate.value = getTodayDateString()
    }

    fun toggleAllTime() {
        selectedDate.value = if (selectedDate.value == null) getTodayDateString() else null
    }

    fun testSenderTargeting(senderName: String, senderNumber: String?): SenderTestResult {
        return SmsParserEngine.testSender(
            senderName = senderName,
            senderNumber = senderNumber,
            sources = allSources.value,
            identifiers = allSenderIdentifiers.value
        )
    }

    fun runSenderTest() {
        testSenderResult.value = testSenderTargeting(testSenderName.value, testSenderNumber.value)
    }

    fun runParserTest() {
        testResult.value = SmsParserEngine.parseSms(
            sender = testSelectedSender.value,
            body = testParserInput.value,
            sources = allSources.value,
            formats = allFormats.value,
            identifiers = allSenderIdentifiers.value,
            internalConfigs = allInternalConfigs.value
        )
    }

    fun testParserRule(body: String, format: SmsFormatEntity): ParsedSmsResult? {
        val testSource = allSources.value.firstOrNull { it.sourceId == format.sourceId }
        val testSources = if (testSource != null) listOf(testSource) else allSources.value
        return SmsParserEngine.parseSms(
            sender = format.sourceId,
            body = body,
            sources = testSources,
            formats = listOf(format),
            identifiers = allSenderIdentifiers.value,
            internalConfigs = allInternalConfigs.value
        )
    }

    fun addSenderIdentifier(
        sourceId: String,
        senderName: String?,
        senderNumber: String?,
        identifierType: SenderIdentifierType = SenderIdentifierType.NAME,
        matchMethod: MatchMethod = MatchMethod.CASE_INSENSITIVE,
        priority: Int = 1,
        description: String = ""
    ) {
        viewModelScope.launch {
            val id = "SND-" + System.currentTimeMillis().toString().takeLast(6)
            repository.insertSenderIdentifier(
                SmsSenderIdentifierEntity(
                    id = id,
                    sourceId = sourceId,
                    senderName = senderName?.trim()?.ifEmpty { null },
                    senderNumber = senderNumber?.trim()?.ifEmpty { null },
                    identifierType = if (!senderNumber.isNullOrBlank() && senderName.isNullOrBlank()) SenderIdentifierType.PHONE_NUMBER else identifierType,
                    matchMethod = matchMethod,
                    priority = priority,
                    description = description.trim()
                )
            )
        }
    }

    fun updateSenderIdentifier(identifier: SmsSenderIdentifierEntity) {
        viewModelScope.launch {
            repository.updateSenderIdentifier(identifier)
        }
    }

    fun deleteSenderIdentifier(identifier: SmsSenderIdentifierEntity) {
        viewModelScope.launch {
            repository.deleteSenderIdentifier(identifier)
        }
    }

    fun createSenderFromUnknownSms(
        sourceId: String,
        senderName: String?,
        senderNumber: String?,
        matchMethod: MatchMethod,
        priority: Int,
        reprocessRawSmsId: String?,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val id = "SND-" + System.currentTimeMillis().toString().takeLast(6)
            val ident = SmsSenderIdentifierEntity(
                id = id,
                sourceId = sourceId,
                senderName = senderName?.trim()?.ifEmpty { null },
                senderNumber = senderNumber?.trim()?.ifEmpty { null },
                identifierType = if (!senderNumber.isNullOrBlank() && senderName.isNullOrBlank()) SenderIdentifierType.PHONE_NUMBER else SenderIdentifierType.NAME,
                matchMethod = matchMethod,
                priority = priority,
                description = "Configured from raw unknown SMS ($reprocessRawSmsId)"
            )
            repository.insertSenderIdentifier(ident)

            if (reprocessRawSmsId != null) {
                repository.reprocessRawSms(reprocessRawSmsId)
            }
            onDone()
        }
    }

    // Ingest SMS action (from simulator, broadcast, or manual input)
    fun ingestSms(phoneId: String, sender: String, body: String, onResult: (IngestionResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.processIncomingSms(phoneId, sender, body)
            onResult(result)
        }
    }

    // Reprocess SMS
    fun reprocessSms(rawSmsId: String, onResult: (IngestionResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.reprocessRawSms(rawSmsId)
            onResult(result)
        }
    }

    // Reprocess all failed/unknown SMS
    fun reprocessAllFailed() {
        viewModelScope.launch {
            for (sms in failedOrUnknownSms.value) {
                repository.reprocessRawSms(sms.rawSmsId)
            }
        }
    }

    // Edit transaction category manually
    fun updateTransactionCategory(txId: String, categoryId: String?, notes: String? = null) {
        viewModelScope.launch {
            repository.updateTransactionCategory(txId, categoryId, notes)
        }
    }

    fun quickCategorizeExpense(txId: String, categoryId: String) {
        val suggested = categorySuggestions.value[txId]?.firstOrNull()?.categoryId
        viewModelScope.launch {
            repository.updateTransactionCategory(txId, categoryId, null)
            if (suggested != null) {
                repository.recordSuggestionOutcome(hit = suggested == categoryId)
                suggestionStats.value = repository.suggestionStats()
            }
        }
    }

    // Phone registration
    fun registerPhone(phoneId: String, friendlyName: String, model: String, phoneNum: String) {
        viewModelScope.launch {
            repository.registerPhone(
                RegisteredPhoneEntity(
                    phoneId = phoneId,
                    friendlyName = friendlyName,
                    modelName = model,
                    phoneNumber = phoneNum
                )
            )
        }
    }

    fun updatePhone(phone: RegisteredPhoneEntity) {
        viewModelScope.launch {
            repository.updatePhone(phone)
        }
    }

    fun deletePhone(phone: RegisteredPhoneEntity) {
        viewModelScope.launch {
            repository.deletePhone(phone)
        }
    }

    // Internal Transfer Config (Configured by Receiver / Counterparty Name Only)
    fun addInternalConfig(receiverName: String, aliases: String = "", description: String = "") {
        viewModelScope.launch {
            val id = "INT-" + System.currentTimeMillis().toString().takeLast(6)
            repository.insertInternalConfig(
                InternalTransferConfigEntity(
                    configId = id,
                    identityName = receiverName.trim(),
                    identifierValue = receiverName.trim(),
                    financialInstitution = "All Banks",
                    aliases = aliases.trim(),
                    isActive = true,
                    description = description.trim()
                )
            )
        }
    }

    fun updateInternalConfig(config: InternalTransferConfigEntity) {
        viewModelScope.launch {
            repository.updateInternalConfig(config)
        }
    }

    fun deleteInternalConfig(config: InternalTransferConfigEntity) {
        viewModelScope.launch {
            repository.deleteInternalConfig(config)
        }
    }

    // Financial Sources CRUD
    // Financial Sources CRUD
    fun addFinancialSource(
        sourceId: String,
        name: String,
        type: SourceType = SourceType.BANK,
        institution: String = "",
        senderIds: String = "",
        colorHex: String = "#3B82F6"
    ) {
        viewModelScope.launch {
            repository.insertSource(
                FinancialSourceEntity(
                    sourceId = sourceId,
                    name = name,
                    type = type,
                    institutionName = institution.ifBlank { name },
                    isActive = true,
                    senderIdentifiers = senderIds,
                    colorHex = colorHex
                )
            )
        }
    }

    fun updateFinancialSource(source: FinancialSourceEntity) {
        viewModelScope.launch {
            repository.updateSource(source)
        }
    }

    // Format CRUD
    fun addFormat(format: SmsFormatEntity) {
        viewModelScope.launch {
            repository.insertFormat(format)
        }
    }

    fun updateFormat(format: SmsFormatEntity) {
        viewModelScope.launch {
            repository.updateFormat(format)
        }
    }

    // Category CRUD
    fun addCategory(name: String, icon: String = "category", color: String = "#10B981", description: String = "") {
        viewModelScope.launch {
            val id = "cat_" + System.currentTimeMillis().toString().takeLast(6)
            repository.insertCategory(
                CategoryEntity(
                    categoryId = id,
                    name = name,
                    iconName = icon,
                    colorHex = color,
                    isCustom = true,
                    description = description.trim()
                )
            )
        }
    }

    fun addCategory(cat: CategoryEntity) {
        viewModelScope.launch {
            repository.insertCategory(cat)
        }
    }

    fun updateCategory(cat: CategoryEntity) {
        viewModelScope.launch {
            repository.updateCategory(cat)
        }
    }

    fun deleteCategory(cat: CategoryEntity) {
        viewModelScope.launch {
            repository.deleteCategory(cat)
        }
    }

    // Phone Income Source Assignment
    fun assignPhoneToIncomeSource(phoneId: String, incomeSourceId: String?) {
        viewModelScope.launch {
            repository.assignPhoneToIncomeSource(phoneId, incomeSourceId)
        }
    }

    // Income Sources CRUD
    fun addIncomeSource(name: String, colorHex: String = "#10B981", iconName: String = "storefront", phoneId: String? = null, note: String = "") {
        viewModelScope.launch {
            val sourceId = "inc_" + System.currentTimeMillis().toString().takeLast(6)
            repository.insertIncomeSource(
                IncomeSourceEntity(
                    sourceId = sourceId,
                    name = name,
                    colorHex = colorHex,
                    iconName = iconName,
                    note = note
                )
            )
            if (phoneId != null) {
                repository.assignPhoneToIncomeSource(phoneId, sourceId)
            }
        }
    }

    fun updateIncomeSource(source: IncomeSourceEntity) {
        viewModelScope.launch {
            repository.updateIncomeSource(source)
        }
    }

    fun deleteIncomeSource(source: IncomeSourceEntity) {
        viewModelScope.launch {
            repository.deleteIncomeSource(source)
        }
    }

    // Savings Goals
    fun addSavingsGoal(
        goalName: String,
        targetAmount: Double,
        targetDate: String,
        startingAmount: Double = 0.0,
        notes: String = ""
    ) {
        viewModelScope.launch {
            val id = "goal_" + System.currentTimeMillis().toString().takeLast(6)
            repository.insertSavingsGoal(
                SavingsGoalEntity(
                    goalId = id,
                    goalName = goalName,
                    targetAmount = targetAmount,
                    currentAmount = startingAmount,
                    targetDate = targetDate,
                    startingAmount = startingAmount,
                    notes = notes
                )
            )
        }
    }

    fun addSavingsGoal(goal: SavingsGoalEntity) {
        viewModelScope.launch {
            repository.insertSavingsGoal(goal)
        }
    }

    fun updateSavingsGoal(goal: SavingsGoalEntity) {
        viewModelScope.launch {
            repository.updateSavingsGoal(goal)
        }
    }

    fun deleteSavingsGoal(goal: SavingsGoalEntity) {
        viewModelScope.launch {
            repository.deleteSavingsGoal(goal)
        }
    }

    fun contributeToGoal(goal: SavingsGoalEntity, amount: Double) {
        viewModelScope.launch {
            val updated = goal.copy(
                currentAmount = (goal.currentAmount + amount).coerceAtMost(goal.targetAmount)
            )
            repository.updateSavingsGoal(updated)
        }
    }

    // ------------------------------------------------------------------
    // AI Capital Growth Advisor (Gemini API & Financial Snapshot Engine)
    // ------------------------------------------------------------------
    val geminiConfig = MutableStateFlow(GeminiConfig())
    val advisorFinancialSnapshot = MutableStateFlow(AdvisorFinancialSnapshot())
    val advisorExecutiveSummary = MutableStateFlow<ExecutiveSummary?>(null)
    val advisorOpportunities = MutableStateFlow<List<AdvisorOpportunity>>(emptyList())
    val advisorCapitalPlan = MutableStateFlow(AdvisorCapitalPlan())
    // Every plan change goes through this lock; the first one waits for the saved plan to load, so a
    // tap during startup can no longer be overwritten by the late initial load.
    private val capitalPlanMutex = Mutex()
    private var capitalPlanLoaded = false

    // Everything the user told the app about themselves (About Me)
    val userProfile = MutableStateFlow(UserProfile())

    // The advisor's long-term memory (facts, preferences, lessons, conversation summary)
    val agentMemory = MutableStateFlow(AgentMemoryState())
    private val memoryMutex = Mutex()

    // Weekly money briefing (built on the phone; also posted as a notification by the background service)
    val latestBriefing = MutableStateFlow<WeeklyBriefing?>(null)
    val isBriefingRunning = MutableStateFlow(false)

    // Ethiopian market figures (rates, inflation, FX, gold...) with dates and sources
    val marketData = MutableStateFlow(MarketSnapshot())
    val isMarketRefreshing = MutableStateFlow(false)

    // Net worth: tracked balances plus the ledger (equb, gold, shares, loans...), valued with market data
    val netWorth: StateFlow<NetWorthSummary> = combine(totalCashAssetSummary, wealthItems, marketData) { asset, items, market ->
        NetWorthCalculator.summarize(asset.totalCashAsset, items, market)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetWorthSummary())
    val advisorChatMessages = MutableStateFlow<List<AdvisorChatMessage>>(emptyList())
    val advisorUsageLogs = MutableStateFlow<List<AdvisorUsageLog>>(emptyList())

    val isTestingConnection = MutableStateFlow(false)
    val connectionTestResult = MutableStateFlow<ConnectionTestResult?>(null)
    val isAdvisorLoading = MutableStateFlow(false)
    val isChatSending = MutableStateFlow(false)
    val advisorError = MutableStateFlow<String?>(null)

    // Legacy fields for backward compatibility
    val advisorApiKey = MutableStateFlow("")
    val advisorResponse = MutableStateFlow<String?>(null)
    val advisorUserQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            marketData.value = MarketDataService.load(repository)
            userProfile.value = UserProfileService.load(repository)
            agentMemory.value = AgentMemoryService.load(repository)
            autoFileEnabled.value = repository.isAutoFileEnabled()
            latestBriefing.value = BriefingStore.lastBriefing(repository)
            suggestionStats.value = repository.suggestionStats()

            val cfg = AdvisorPersistenceService.loadGeminiConfig(repository)
            geminiConfig.value = cfg
            advisorApiKey.value = cfg.resolveEffectiveKey()

            val summary = AdvisorPersistenceService.loadExecutiveSummary(repository)
            advisorExecutiveSummary.value = summary

            val opps = AdvisorPersistenceService.loadOpportunities(repository)
            advisorOpportunities.value = opps

            capitalPlanMutex.withLock { ensureCapitalPlanLoaded() }

            val chats = AdvisorPersistenceService.loadChatMessages(repository)
            advisorChatMessages.value = chats

            val logs = AdvisorPersistenceService.loadUsageLogs(repository)
            advisorUsageLogs.value = logs

            recomputeAdvisorSnapshot()
        }

        // Recompute the advisor snapshot (headline figures + deep context) whenever the data changes.
        // It uses every transaction, not the dashboard's single-day filter, so monthly figures are real.
        viewModelScope.launch {
            combine(
                totalCashAssetSummary,
                allTransactions,
                combine(allCategories, allIncomeSources, allSavingsGoals) { cats, incomes, goals -> Triple(cats, incomes, goals) },
                combine(advisorCapitalPlan, marketData, wealthItems, userProfile) { plan, market, wealth, profile ->
                    AdvisorInputs(plan, market, wealth, profile)
                }
            ) { assetSummary, txs, refs, state ->
                buildAdvisorSnapshot(assetSummary, txs, refs.first, refs.second, refs.third, state.plan, state.market, state.wealth, state.profile)
            }.flowOn(Dispatchers.Default).collect { snapshot ->
                advisorFinancialSnapshot.value = snapshot
            }
        }
    }

    private fun buildAdvisorSnapshot(
        assetSummary: TotalCashAssetSummary,
        txs: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        incomeSources: List<IncomeSourceEntity>,
        goals: List<SavingsGoalEntity>,
        plan: AdvisorCapitalPlan,
        market: MarketSnapshot,
        wealth: List<WealthItemEntity>,
        profile: UserProfile,
        nowMillis: Long = System.currentTimeMillis()
    ): AdvisorFinancialSnapshot {
        val monthStart = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        // Month-to-date spending per category (internal transfers are not spending)
        val monthSpends = computeCategorySpends(
            categories,
            txs.filter { it.createdTimestamp >= monthStart && it.transactionType != TransactionType.INTERNAL_TRANSFER }
        )
        val base = FinancialSnapshotService.computeSnapshot(
            bankBalance = assetSummary.bankAndWalletBalance,
            manualBalance = assetSummary.manualAndOtherIncomeBalance,
            transactions = txs,
            categorySpends = monthSpends,
            activeSourcesCount = allSources.value.count { it.isActive },
            activePhonesCount = assetSummary.activePhoneCount,
            nowMillis = nowMillis,
            emergencyMonths = profile.emergencyMonths
        )
        val deep = AdvisorIntelligence.analyze(txs, categories, incomeSources, goals, plan, base.totalRecordedBalance, nowMillis)
        val ranked = InvestmentCatalog.rank(market, base.availableLiquidCash, currentRankingPreferences(wealth, profile))
        val worth = NetWorthCalculator.summarize(assetSummary.totalCashAsset, wealth, market)
        return base.copy(deepContext = deep, market = market, rankedOptions = ranked, netWorth = worth, profile = profile)
    }

    /** What the user allows when options are ranked (risk, lock-up, interest-free, debts, business numbers). */
    private fun currentRankingPreferences(
        wealth: List<WealthItemEntity> = wealthItems.value,
        profile: UserProfile = userProfile.value
    ): RankingPreferences = profile.rankingPreferences(
        hasDebt = wealth.any { it.kind.isLiability && !it.isDeleted },
        debtRatePct = NetWorthCalculator.highestDebtRatePct(wealth)
    )

    fun saveUserProfile(profile: UserProfile) {
        userProfile.value = profile
        viewModelScope.launch { UserProfileService.save(repository, profile) }
    }

    /** Fresh snapshot computed off the main thread (used right before every AI request). */
    private suspend fun currentAdvisorSnapshot(): AdvisorFinancialSnapshot {
        val snapshot = withContext(Dispatchers.Default) {
            buildAdvisorSnapshot(
                totalCashAssetSummary.value,
                allTransactions.value,
                allCategories.value,
                allIncomeSources.value,
                allSavingsGoals.value,
                advisorCapitalPlan.value,
                marketData.value,
                wealthItems.value,
                userProfile.value
            )
        }
        advisorFinancialSnapshot.value = snapshot
        return snapshot
    }

    fun recomputeAdvisorSnapshot() {
        viewModelScope.launch { currentAdvisorSnapshot() }
    }

    /** Refreshes market figures from the web (Google Search grounding); needs a Gemini key. */
    fun refreshMarketData(force: Boolean = true) {
        if (isMarketRefreshing.value) return
        val cfg = geminiConfig.value
        val key = cfg.resolveEffectiveKey()
        if (key.isBlank()) {
            if (force) advisorNotice.value = "Add your Gemini API key to refresh market data from the web."
            return
        }
        if (!force && !MarketDataService.isStale(marketData.value)) return
        viewModelScope.launch {
            isMarketRefreshing.value = true
            try {
                val refreshed = MarketDataService.refreshFromWeb(key, cfg.selectedModel, marketData.value)
                marketData.value = refreshed
                MarketDataService.save(repository, refreshed)
                if (force) advisorNotice.value = "Market data updated from the web"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failed = marketData.value.copy(lastWebRefreshAt = System.currentTimeMillis(), lastRefreshError = e.message)
                marketData.value = failed
                MarketDataService.save(repository, failed)
                if (force) advisorNotice.value = "Market refresh failed: ${e.message}"
            } finally {
                isMarketRefreshing.value = false
            }
        }
    }

    /** Lets the user correct or add a market figure (e.g. the rate their bank quoted them). */
    fun setMarketValue(key: String, value: Double, asOf: String, note: String = "") {
        viewModelScope.launch {
            val updated = MarketDataService.withUserValue(marketData.value, key, value, asOf, note)
            marketData.value = updated
            MarketDataService.save(repository, updated)
        }
    }

    fun saveGeminiApiKey(key: String) {
        val clean = key.trim()
        val current = geminiConfig.value
        val updated = current.copy(
            apiKey = clean,
            status = if (clean.isNotBlank()) GeminiConnectionStatus.CONFIGURED_UNTESTED else GeminiConnectionStatus.NOT_CONFIGURED,
            updatedAt = System.currentTimeMillis()
        )
        geminiConfig.value = updated
        advisorApiKey.value = clean
        viewModelScope.launch {
            AdvisorPersistenceService.saveGeminiConfig(repository, updated)
        }
    }

    fun setGeminiModel(modelId: String) {
        val current = geminiConfig.value
        val updated = current.copy(
            selectedModel = modelId,
            updatedAt = System.currentTimeMillis()
        )
        geminiConfig.value = updated
        viewModelScope.launch {
            AdvisorPersistenceService.saveGeminiConfig(repository, updated)
        }
    }

    fun removeGeminiApiKey() {
        val updated = GeminiConfig(
            apiKey = "",
            selectedModel = geminiConfig.value.selectedModel,
            status = GeminiConnectionStatus.NOT_CONFIGURED,
            updatedAt = System.currentTimeMillis()
        )
        geminiConfig.value = updated
        advisorApiKey.value = ""
        connectionTestResult.value = null
        viewModelScope.launch {
            AdvisorPersistenceService.saveGeminiConfig(repository, updated)
        }
    }

    fun testGeminiConnection() {
        val cfg = geminiConfig.value
        val effectiveKey = cfg.resolveEffectiveKey()
        if (effectiveKey.isBlank()) {
            connectionTestResult.value = ConnectionTestResult(
                success = false,
                status = GeminiConnectionStatus.NOT_CONFIGURED,
                latencyMs = 0L,
                modelUsed = cfg.selectedModel,
                message = "No API key configured. Please enter your Gemini API Key."
            )
            return
        }

        viewModelScope.launch {
            isTestingConnection.value = true
            val startTime = System.currentTimeMillis()
            val result = GeminiClientService.testConnection(effectiveKey, cfg.selectedModel)
            isTestingConnection.value = false

            result.fold(
                onSuccess = { res ->
                    connectionTestResult.value = res
                    val updatedCfg = cfg.copy(
                        status = res.status,
                        lastTestedAt = res.timestamp,
                        lastLatencyMs = res.latencyMs,
                        lastError = if (!res.success) res.message else null
                    )
                    geminiConfig.value = updatedCfg
                    AdvisorPersistenceService.saveGeminiConfig(repository, updatedCfg)

                    // Log usage
                    val log = AdvisorUsageLog(
                        id = UUID.randomUUID().toString(),
                        timestamp = res.timestamp,
                        model = res.modelUsed,
                        latencyMs = res.latencyMs,
                        status = if (res.success) "SUCCESS" else "FAILED",
                        errorMessage = if (!res.success) res.message else null
                    )
                    AdvisorPersistenceService.appendUsageLog(repository, log)
                    advisorUsageLogs.value = AdvisorPersistenceService.loadUsageLogs(repository)
                },
                onFailure = { err ->
                    val latency = System.currentTimeMillis() - startTime
                    val res = ConnectionTestResult(
                        success = false,
                        status = GeminiConnectionStatus.SERVICE_UNAVAILABLE,
                        latencyMs = latency,
                        modelUsed = cfg.selectedModel,
                        message = err.localizedMessage ?: "Connection test error."
                    )
                    connectionTestResult.value = res
                }
            )
        }
    }

    fun runFullCapitalAnalysis(userPreferences: String = "") {
        val cfg = geminiConfig.value
        val effectiveKey = cfg.resolveEffectiveKey()
        if (effectiveKey.isBlank()) {
            advisorError.value = "Please configure your Gemini API Key in settings to run Capital Growth Analysis."
            return
        }

        viewModelScope.launch {
            isAdvisorLoading.value = true
            advisorError.value = null
            val snapshot = currentAdvisorSnapshot()
            val startTime = System.currentTimeMillis()

            val result = GeminiClientService.generateExecutiveSummaryAndOpportunities(
                apiKey = effectiveKey,
                model = cfg.selectedModel,
                snapshot = snapshot,
                userPreferencesText = userPreferences,
                extraContext = AgentMemoryService.toPromptSection(agentMemory.value, "capital growth analysis opportunities $userPreferences")
            )

            val latency = System.currentTimeMillis() - startTime
            isAdvisorLoading.value = false

            result.fold(
                onSuccess = { (summary, rawOpps) ->
                    val inflation = snapshot.market.value(MarketIndicators.INFLATION)
                    val opps = rawOpps.map { it.copy(realReturnPct = opportunityRealReturnPct(it, inflation)) }
                    advisorExecutiveSummary.value = summary
                    advisorOpportunities.value = opps
                    advisorError.value = null
                    advisorResponse.value = summary.overview

                    // Update config connection status
                    val updatedCfg = cfg.copy(
                        status = GeminiConnectionStatus.CONNECTED,
                        lastTestedAt = System.currentTimeMillis(),
                        lastLatencyMs = latency,
                        lastError = null
                    )
                    geminiConfig.value = updatedCfg
                    AdvisorPersistenceService.saveGeminiConfig(repository, updatedCfg)

                    // Persist results
                    AdvisorPersistenceService.saveExecutiveSummary(repository, summary)
                    AdvisorPersistenceService.saveOpportunities(repository, opps)

                    // Append usage log
                    val log = AdvisorUsageLog(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        model = cfg.selectedModel,
                        latencyMs = latency,
                        status = "SUCCESS",
                        promptTokens = GeminiClientService.lastUsage?.first,
                        completionTokens = GeminiClientService.lastUsage?.second
                    )
                    AdvisorPersistenceService.appendUsageLog(repository, log)
                    advisorUsageLogs.value = AdvisorPersistenceService.loadUsageLogs(repository)
                },
                onFailure = { err ->
                    advisorError.value = err.message ?: "Analysis failed."
                    val log = AdvisorUsageLog(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        model = cfg.selectedModel,
                        latencyMs = latency,
                        status = "FAILED",
                        errorMessage = err.message
                    )
                    AdvisorPersistenceService.appendUsageLog(repository, log)
                    advisorUsageLogs.value = AdvisorPersistenceService.loadUsageLogs(repository)
                }
            )
        }
    }

    /** Live web mode: answers are grounded with Google Search instead of the in-app tools. */
    val advisorWebMode = MutableStateFlow(false)
    fun setAdvisorWebMode(enabled: Boolean) { advisorWebMode.value = enabled }

    /** What the chat agent may change, always on the user's explicit request. */
    private val advisorActions = object : AdvisorActions {
        override suspend fun addMilestone(
            title: String,
            description: String,
            targetBenefitEtb: Double,
            deadlineMillis: Long,
            trackCategory: String?,
            trackKeyword: String?
        ): String = addMilestoneNow(title, description, targetBenefitEtb, deadlineMillis, sourceOpportunityId = null, trackCategory = trackCategory, trackKeyword = trackKeyword).id

        override suspend fun updateMilestoneStatus(milestoneId: String, status: MilestoneStatus) {
            updateMilestoneStatusNow(milestoneId, status, null)
        }

        override suspend fun createSavingsGoal(name: String, targetAmount: Double, targetDate: String, notes: String) {
            repository.insertSavingsGoal(
                SavingsGoalEntity(
                    goalId = "goal_" + System.currentTimeMillis().toString().takeLast(6),
                    goalName = name,
                    targetAmount = targetAmount,
                    currentAmount = 0.0,
                    targetDate = targetDate,
                    notes = notes
                )
            )
        }

        override suspend fun categorizeTransactions(transactionIds: List<String>, categoryId: String): Int {
            transactionIds.forEach { repository.updateTransactionCategory(it, categoryId, null) }
            return transactionIds.size
        }

        override suspend fun recordWealthItem(item: WealthItemEntity): String {
            repository.upsertWealthItem(item)
            return item.itemId
        }

        override suspend fun remember(text: String, kind: MemoryKind): String {
            var id = ""
            mutateMemory { state ->
                val (updated, item) = AgentMemoryService.remember(state, text, kind, MemorySource.AGENT)
                id = item.id
                updated
            }
            return id
        }

        override suspend fun forget(memoryId: String): Boolean {
            var found = false
            mutateMemory { state ->
                found = state.items.any { it.id == memoryId }
                AgentMemoryService.forget(state, memoryId)
            }
            return found
        }

        override suspend fun updateProfileField(field: String, value: String): Boolean {
            val updated = UserProfileService.withField(userProfile.value, field, value) ?: return false
            userProfile.value = updated
            UserProfileService.save(repository, updated)
            return true
        }
    }

    // Net-worth ledger
    fun saveWealthItem(item: WealthItemEntity) {
        viewModelScope.launch { repository.upsertWealthItem(item) }
    }

    fun deleteWealthItem(itemId: String) {
        viewModelScope.launch { repository.deleteWealthItem(itemId) }
    }

    private suspend fun mutateMemory(transform: (AgentMemoryState) -> AgentMemoryState) {
        memoryMutex.withLock {
            val updated = transform(agentMemory.value)
            agentMemory.value = updated
            AgentMemoryService.save(repository, updated)
        }
    }

    /** Older chat turns are folded into a running summary (and durable facts) so the agent keeps context. */
    private suspend fun summarizeOlderTurns(apiKey: String, model: String, messages: List<AdvisorChatMessage>) {
        val pending = AgentMemoryService.pendingForSummary(agentMemory.value, messages)
        if (pending.size < AgentMemoryService.SUMMARY_BATCH) return
        try {
            val (summary, facts) = GeminiClientService.summarizeConversation(apiKey, model, agentMemory.value.conversationSummary, pending)
            mutateMemory { state ->
                var next = state.copy(conversationSummary = summary, summarizedUpTo = pending.maxOf { it.timestamp })
                facts.forEach { (text, kind) -> next = AgentMemoryService.remember(next, text, kind, MemorySource.SYSTEM).first }
                next
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Summary is best-effort; the next reply tries again
        }
    }

    // Memory management from the "Me" screen
    fun addMemory(text: String, kind: MemoryKind) {
        if (text.isBlank()) return
        viewModelScope.launch { mutateMemory { AgentMemoryService.remember(it, text, kind, MemorySource.USER).first } }
    }

    fun forgetMemory(id: String) {
        viewModelScope.launch { mutateMemory { AgentMemoryService.forget(it, id) } }
    }

    fun togglePinMemory(id: String) {
        viewModelScope.launch { mutateMemory { AgentMemoryService.togglePin(it, id) } }
    }

    fun clearMemory() {
        viewModelScope.launch { mutateMemory { AgentMemoryState() } }
    }

    /** 👍/👎 on an advisor answer; a 👎 reason becomes a lesson in memory. */
    fun rateAnswer(messageId: String, helpful: Boolean, reason: String = "") {
        val messages = advisorChatMessages.value
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0 || messages[index].rating != 0) return
        val question = messages.take(index).lastOrNull { it.isUser }?.message ?: ""
        val updated = messages.toMutableList().also { it[index] = it[index].copy(rating = if (helpful) 1 else -1) }
        advisorChatMessages.value = updated
        viewModelScope.launch {
            AdvisorPersistenceService.saveChatMessages(repository, updated)
            mutateMemory { AgentMemoryService.recordFeedback(it, helpful, question, reason) }
        }
    }

    fun sendAdvisorChatMessage(query: String) {
        val text = query.trim()
        if (text.isBlank()) return

        val cfg = geminiConfig.value
        val effectiveKey = cfg.resolveEffectiveKey()
        if (effectiveKey.isBlank()) {
            advisorError.value = "Please configure your Gemini API Key in settings to chat with the advisor."
            return
        }

        val userMsg = AdvisorChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            message = text,
            timestamp = System.currentTimeMillis()
        )

        val previous = advisorChatMessages.value
        advisorChatMessages.value = previous + userMsg
        val useWeb = advisorWebMode.value

        viewModelScope.launch {
            isChatSending.value = true
            advisorError.value = null
            val snapshot = currentAdvisorSnapshot()
            val startTime = System.currentTimeMillis()

            val data = AdvisorDataView(
                transactions = allTransactions.value,
                categories = allCategories.value,
                incomeSources = allIncomeSources.value,
                phoneNames = allPhones.value.associate { it.phoneId to it.friendlyName },
                bankNames = allSources.value.associate { it.sourceId to it.name },
                plan = advisorCapitalPlan.value,
                memory = agentMemory.value,
                deep = snapshot.deepContext,
                market = marketData.value,
                deployableCash = snapshot.availableLiquidCash,
                rankingPreferences = currentRankingPreferences(),
                netWorth = snapshot.netWorth,
                profile = userProfile.value
            )
            val result = try {
                Result.success(
                    AdvisorAgent.chat(
                        apiKey = effectiveKey,
                        model = cfg.selectedModel,
                        systemInstruction = GeminiClientService.systemInstructionFor(
                            snapshot,
                            forAgent = !useWeb,
                            extraContext = AgentMemoryService.toPromptSection(agentMemory.value, text)
                        ),
                        history = previous.takeLast(AgentMemoryService.RECENT_TURNS),
                        userQuery = text,
                        tools = AdvisorToolExecutor(data, advisorActions),
                        useWebSearch = useWeb
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }

            val latency = System.currentTimeMillis() - startTime
            isChatSending.value = false

            result.fold(
                onSuccess = { reply ->
                    val replyMsg = AdvisorChatMessage(
                        id = UUID.randomUUID().toString(),
                        isUser = false,
                        message = reply.text,
                        timestamp = System.currentTimeMillis(),
                        suggestedActions = GeminiClientService.extractSuggestedActions(reply.text),
                        toolActions = reply.toolActions,
                        sources = reply.sources
                    )
                    val finalList = advisorChatMessages.value + replyMsg
                    advisorChatMessages.value = finalList
                    AdvisorPersistenceService.saveChatMessages(repository, finalList)
                    summarizeOlderTurns(effectiveKey, cfg.selectedModel, finalList)

                    val log = AdvisorUsageLog(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        model = cfg.selectedModel,
                        latencyMs = latency,
                        status = "SUCCESS",
                        promptTokens = reply.promptTokens,
                        completionTokens = reply.completionTokens
                    )
                    AdvisorPersistenceService.appendUsageLog(repository, log)
                    advisorUsageLogs.value = AdvisorPersistenceService.loadUsageLogs(repository)
                },
                onFailure = { err ->
                    advisorError.value = err.message ?: "Chat response failed."
                    val errorBubble = AdvisorChatMessage(
                        id = UUID.randomUUID().toString(),
                        isUser = false,
                        message = "⚠️ Could not generate response: ${err.message}",
                        timestamp = System.currentTimeMillis()
                    )
                    advisorChatMessages.value = advisorChatMessages.value + errorBubble
                    val log = AdvisorUsageLog(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        model = cfg.selectedModel,
                        latencyMs = latency,
                        status = "FAILED",
                        errorMessage = err.message
                    )
                    AdvisorPersistenceService.appendUsageLog(repository, log)
                    advisorUsageLogs.value = AdvisorPersistenceService.loadUsageLogs(repository)
                }
            )
        }
    }

    private suspend fun ensureCapitalPlanLoaded() {
        if (capitalPlanLoaded) return
        advisorCapitalPlan.value = AdvisorPersistenceService.loadCapitalPlan(repository, totalCashAssetSummary.value.totalCashAsset)
        capitalPlanLoaded = true
    }

    private suspend fun mutateCapitalPlan(transform: (AdvisorCapitalPlan) -> AdvisorCapitalPlan): AdvisorCapitalPlan =
        capitalPlanMutex.withLock {
            ensureCapitalPlanLoaded()
            val updated = withRecomputedProgress(transform(advisorCapitalPlan.value))
            advisorCapitalPlan.value = updated
            AdvisorPersistenceService.saveCapitalPlan(repository, updated)
            updated
        }

    private fun withRecomputedProgress(plan: AdvisorCapitalPlan): AdvisorCapitalPlan {
        val completedCount = plan.milestones.count { it.status == MilestoneStatus.COMPLETED }
        val progress = if (plan.milestones.isNotEmpty()) completedCount.toFloat() / plan.milestones.size else 0f
        return plan.copy(progressPercentage = progress, lastReviewedAt = System.currentTimeMillis())
    }

    /** One-off message for the advisor screen (shown as a toast, then cleared). */
    val advisorNotice = MutableStateFlow<String?>(null)
    fun consumeAdvisorNotice() { advisorNotice.value = null }

    fun addCapitalPlanMilestone(
        title: String,
        description: String,
        targetBenefitEtb: Double,
        deadlineMillis: Long
    ) {
        viewModelScope.launch {
            addMilestoneNow(title, description, targetBenefitEtb, deadlineMillis, sourceOpportunityId = null)
        }
    }

    private suspend fun addMilestoneNow(
        title: String,
        description: String,
        targetBenefitEtb: Double,
        deadlineMillis: Long,
        sourceOpportunityId: String?,
        trackCategory: String? = null,
        trackKeyword: String? = null
    ): PlanMilestone {
        val newMilestone = PlanMilestone(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            targetBenefitEtb = targetBenefitEtb,
            deadlineMillis = deadlineMillis,
            status = MilestoneStatus.PENDING,
            sourceOpportunityId = sourceOpportunityId,
            trackCategory = trackCategory,
            trackKeyword = trackKeyword,
            startedAt = System.currentTimeMillis()
        )
        mutateCapitalPlan { plan -> plan.copy(milestones = plan.milestones + newMilestone) }
        return newMilestone
    }

    fun updateMilestoneStatus(
        milestoneId: String,
        status: MilestoneStatus,
        verifiedOutcome: Double? = null
    ) {
        viewModelScope.launch { updateMilestoneStatusNow(milestoneId, status, verifiedOutcome) }
    }

    private suspend fun updateMilestoneStatusNow(milestoneId: String, status: MilestoneStatus, verifiedOutcome: Double?) {
        mutateCapitalPlan { plan ->
            plan.copy(milestones = plan.milestones.map { m ->
                if (m.id == milestoneId) {
                    m.copy(
                        status = status,
                        verifiedOutcomeEtb = verifiedOutcome ?: m.verifiedOutcomeEtb,
                        completedAt = if (status == MilestoneStatus.COMPLETED) System.currentTimeMillis() else m.completedAt
                    )
                } else m
            })
        }
    }

    /** Builds the weekly briefing now (same code the background service runs once a week). */
    fun generateBriefingNow() {
        if (isBriefingRunning.value) return
        val app = runCatching { BirrFlowApplication.instance }.getOrNull() ?: return
        viewModelScope.launch {
            isBriefingRunning.value = true
            try {
                // Persist the in-memory plan first so the briefing measures the latest milestones
                capitalPlanMutex.withLock {
                    ensureCapitalPlanLoaded()
                    AdvisorPersistenceService.saveCapitalPlan(repository, advisorCapitalPlan.value)
                }
                val briefing = withContext(Dispatchers.IO) { WeeklyBriefingScheduler.runIfDue(app, force = true) }
                latestBriefing.value = briefing
                capitalPlanMutex.withLock {
                    advisorCapitalPlan.value = AdvisorPersistenceService.loadCapitalPlan(repository, totalCashAssetSummary.value.totalCashAsset)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                advisorNotice.value = "Briefing failed: ${e.message}"
            } finally {
                isBriefingRunning.value = false
            }
        }
    }

    /** Measures tracked milestones against real transactions (spending cuts, payments made). */
    fun checkPlanOutcomes() {
        viewModelScope.launch {
            val txs = allTransactions.value
            val categories = allCategories.value
            mutateCapitalPlan { plan -> DecisionTracker.evaluate(plan, txs, categories) }
        }
    }

    /** True when the opportunity already has a milestone in the capital plan. */
    fun isOpportunityInPlan(opp: AdvisorOpportunity, plan: AdvisorCapitalPlan = advisorCapitalPlan.value): Boolean =
        plan.milestones.any { it.sourceOpportunityId == opp.id || (it.sourceOpportunityId == null && it.title == opp.title) }

    fun convertOpportunityToMilestone(opp: AdvisorOpportunity) {
        viewModelScope.launch {
            val description = buildString {
                append(opp.summary)
                if (opp.recommendedNextStep.isNotBlank()) append("\nNext step: ").append(opp.recommendedNextStep)
                if (opp.capitalRequiredEtb > 0) append("\nCapital required: ").append(com.example.ui.components.formatCurrency(opp.capitalRequiredEtb))
            }
            val milestone = PlanMilestone(
                id = UUID.randomUUID().toString(),
                title = opp.title,
                description = description,
                targetBenefitEtb = opp.estimatedBenefitEtb,
                deadlineMillis = deadlineFromTimeframe(opp.timeframe),
                status = MilestoneStatus.PENDING,
                sourceOpportunityId = opp.id,
                // Spending cuts naming one of the user's categories are measured automatically
                trackCategory = DecisionTracker.inferTrackCategory(opp, allCategories.value),
                startedAt = System.currentTimeMillis()
            )
            // Checked under the plan lock so a double tap cannot add the same opportunity twice
            var added = false
            mutateCapitalPlan { plan ->
                if (isOpportunityInPlan(opp, plan)) {
                    plan
                } else {
                    added = true
                    plan.copy(milestones = plan.milestones + milestone)
                }
            }
            if (!added) {
                advisorNotice.value = "\"${opp.title}\" is already in your Capital Plan"
                return@launch
            }

            // Mark opp as PLANNED
            val updatedOpps = advisorOpportunities.value.map { o ->
                if (o.id == opp.id) o.copy(status = com.example.ai.OpportunityStatus.PLANNED) else o
            }
            advisorOpportunities.value = updatedOpps
            AdvisorPersistenceService.saveOpportunities(repository, updatedOpps)
            advisorNotice.value = "Added \"${opp.title}\" to your Capital Plan"
        }
    }

    fun clearChatHistory() {
        advisorChatMessages.value = emptyList()
        viewModelScope.launch {
            AdvisorPersistenceService.saveChatMessages(repository, emptyList())
        }
    }

    // Legacy method wrappers
    fun saveAdvisorApiKey(key: String) = saveGeminiApiKey(key)
    fun consultBusinessAdvisor(customPrompt: String? = null) {
        val prompt = customPrompt ?: advisorUserQuery.value
        if (prompt.isNotBlank()) {
            sendAdvisorChatMessage(prompt)
        } else {
            runFullCapitalAnalysis()
        }
    }

    companion object {
        fun getTodayDateString(): String = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date())

        fun getStartOfTodayMillis(): Long {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun computeTimeframeDeltas(
            txList: List<TransactionEntity>,
            nowMillis: Long = System.currentTimeMillis()
        ): TimeframeDeltas {
            val oneHourAgo = nowMillis - 3_600_000L
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfToday = cal.timeInMillis
            val dailyCutoff = startOfToday // Reset every 24h at 00:00 (12:00 AM)
            val oneWeekAgo = nowMillis - 7L * 86_400_000L
            val oneMonthAgo = nowMillis - 30L * 86_400_000L
            
            val calMonth = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfCurrentMonth = calMonth.timeInMillis
            val monthlyCutoff = minOf(startOfCurrentMonth, oneMonthAgo)

            var hIn = 0.0; var hOut = 0.0; var hCount = 0
            var dIn = 0.0; var dOut = 0.0; var dCount = 0
            var wIn = 0.0; var wOut = 0.0; var wCount = 0
            var mIn = 0.0; var mOut = 0.0; var mCount = 0

            val todayStr = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(nowMillis))

            for (tx in txList) {
                val txTs = if (tx.createdTimestamp > 0) tx.createdTimestamp else {
                    try {
                        SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(tx.transactionDate)?.time ?: 0L
                    } catch (_: Exception) { 0L }
                }

                var credit = 0.0
                var debit = 0.0

                if (tx.transactionType == TransactionType.INTERNAL_TRANSFER) {
                    if (tx.fee > 0.0) {
                        debit += tx.fee
                    }
                } else {
                    when {
                        tx.direction == FlowDirection.CREDIT ||
                            tx.transactionType == TransactionType.INCOME ||
                            tx.transactionType == TransactionType.DEPOSIT -> {
                            credit += tx.amount
                        }
                        tx.direction == FlowDirection.DEBIT ||
                            tx.transactionType == TransactionType.EXPENSE ||
                            tx.transactionType == TransactionType.WITHDRAWAL ||
                            tx.transactionType == TransactionType.FEE -> {
                            debit += (tx.amount + tx.fee)
                        }
                    }
                }

                if (credit == 0.0 && debit == 0.0) continue

                // Hourly: last 60 minutes
                if (txTs >= oneHourAgo) {
                    hIn += credit
                    hOut += debit
                    hCount++
                }

                // Daily: today or last 24h
                if (txTs >= dailyCutoff || tx.transactionDate == todayStr) {
                    dIn += credit
                    dOut += debit
                    dCount++
                }

                // Weekly: past 7 days
                if (txTs >= oneWeekAgo) {
                    wIn += credit
                    wOut += debit
                    wCount++
                }

                // Monthly: past 30 days or current calendar month to date
                if (txTs >= monthlyCutoff) {
                    mIn += credit
                    mOut += debit
                    mCount++
                }
            }

            return TimeframeDeltas(
                hourly = CashAssetDelta(netChange = hIn - hOut, inflow = hIn, outflow = hOut, transactionCount = hCount),
                daily = CashAssetDelta(netChange = dIn - dOut, inflow = dIn, outflow = dOut, transactionCount = dCount),
                weekly = CashAssetDelta(netChange = wIn - wOut, inflow = wIn, outflow = wOut, transactionCount = wCount),
                monthly = CashAssetDelta(netChange = mIn - mOut, inflow = mIn, outflow = mOut, transactionCount = mCount)
            )
        }

        fun computeSpecificRangeData(
            scopedTxs: List<TransactionEntity>,
            mode: RangeExplorerMode,
            hour: Int,
            params: RangeExplorerParams
        ): SpecificRangeData {
            val matching = when (mode) {
                RangeExplorerMode.HOUR -> {
                    val cal = Calendar.getInstance()
                    val targetDay = DayMatcher.of(params.hourDate)
                    val parts = params.hourDate.split('/', '-', '.')
                    if (parts.size == 3) {
                        val d = parts[0].toIntOrNull() ?: cal.get(Calendar.DAY_OF_MONTH)
                        val m = parts[1].toIntOrNull() ?: (cal.get(Calendar.MONTH) + 1)
                        var y = parts[2].toIntOrNull() ?: cal.get(Calendar.YEAR)
                        if (y < 100) y += 2000
                        cal.set(y, m - 1, d, hour, 0, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                    } else {
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                    }
                    val hourStart = cal.timeInMillis
                    val hourEnd = hourStart + 3_600_000L - 1L
                    val hourStr = String.format(Locale.US, "%02d", hour)

                    scopedTxs.filter { tx ->
                        if (tx.createdTimestamp in hourStart..hourEnd) return@filter true
                        val matchesDay = targetDay?.matches(tx.transactionDate, tx.createdTimestamp) ?: (tx.transactionDate == params.hourDate)
                        matchesDay && tx.transactionTime.startsWith(hourStr)
                    }
                }
                RangeExplorerMode.DATE_RANGE -> {
                    if (params.endDate == null || params.endDate.isBlank() || params.endDate == params.startDate) {
                        val day = DayMatcher.of(params.startDate)
                        scopedTxs.filter { tx ->
                            day?.matches(tx.transactionDate, tx.createdTimestamp) ?: (tx.transactionDate == params.startDate)
                        }
                    } else {
                        val calS = Calendar.getInstance()
                        val calE = Calendar.getInstance()
                        val pS = params.startDate.split('/', '-', '.')
                        val pE = params.endDate.split('/', '-', '.')
                        if (pS.size == 3 && pE.size == 3) {
                            val dS = pS[0].toIntOrNull() ?: 1
                            val mS = pS[1].toIntOrNull() ?: 1
                            var yS = pS[2].toIntOrNull() ?: 2026
                            if (yS < 100) yS += 2000
                            calS.set(yS, mS - 1, dS, 0, 0, 0)
                            calS.set(Calendar.MILLISECOND, 0)

                            val dE = pE[0].toIntOrNull() ?: 1
                            val mE = pE[1].toIntOrNull() ?: 1
                            var yE = pE[2].toIntOrNull() ?: 2026
                            if (yE < 100) yE += 2000
                            calE.set(yE, mE - 1, dE, 23, 59, 59)
                            calE.set(Calendar.MILLISECOND, 999)
                        }
                        var sMillis = calS.timeInMillis
                        var eMillis = calE.timeInMillis
                        if (sMillis > eMillis) {
                            val temp = sMillis; sMillis = eMillis; eMillis = temp
                        }
                        scopedTxs.filter { tx ->
                            val ts = if (tx.createdTimestamp > 0) tx.createdTimestamp else {
                                try { SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(tx.transactionDate)?.time ?: 0L } catch (_: Exception) { 0L }
                            }
                            ts in sMillis..eMillis
                        }
                    }
                }
                RangeExplorerMode.WEEK -> {
                    val cal = Calendar.getInstance()
                    cal.firstDayOfWeek = Calendar.MONDAY
                    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cal.add(Calendar.WEEK_OF_YEAR, -params.weekOffset)
                    val weekStart = cal.timeInMillis
                    cal.add(Calendar.DAY_OF_YEAR, 7)
                    val weekEnd = cal.timeInMillis - 1L
                    scopedTxs.filter { tx ->
                        val ts = if (tx.createdTimestamp > 0) tx.createdTimestamp else {
                            try { SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(tx.transactionDate)?.time ?: 0L } catch (_: Exception) { 0L }
                        }
                        ts in weekStart..weekEnd
                    }
                }
                RangeExplorerMode.MONTH -> {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cal.add(Calendar.MONTH, -params.monthOffset)
                    val monthStart = cal.timeInMillis
                    cal.add(Calendar.MONTH, 1)
                    val monthEnd = cal.timeInMillis - 1L
                    scopedTxs.filter { tx ->
                        val ts = if (tx.createdTimestamp > 0) tx.createdTimestamp else {
                            try { SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(tx.transactionDate)?.time ?: 0L } catch (_: Exception) { 0L }
                        }
                        ts in monthStart..monthEnd
                    }
                }
            }

            var inFlow = 0.0
            var outFlow = 0.0

            for (tx in matching) {
                if (tx.transactionType == TransactionType.INTERNAL_TRANSFER) {
                    if (tx.fee > 0.0) outFlow += tx.fee
                } else {
                    when {
                        tx.direction == FlowDirection.CREDIT ||
                            tx.transactionType == TransactionType.INCOME ||
                            tx.transactionType == TransactionType.DEPOSIT -> {
                            inFlow += tx.amount
                        }
                        tx.direction == FlowDirection.DEBIT ||
                            tx.transactionType == TransactionType.EXPENSE ||
                            tx.transactionType == TransactionType.WITHDRAWAL ||
                            tx.transactionType == TransactionType.FEE -> {
                            outFlow += (tx.amount + tx.fee)
                        }
                    }
                }
            }

            val (title, rangeLabel) = when (mode) {
                RangeExplorerMode.HOUR -> {
                    val hStr = String.format(Locale.US, "%02d:00 - %02d:59", hour, hour)
                    "Specific Hour ($hStr)" to "${params.hourDate} at $hStr"
                }
                RangeExplorerMode.DATE_RANGE -> {
                    if (params.endDate == null || params.endDate.isBlank() || params.endDate == params.startDate) {
                        "Specific Date" to params.startDate
                    } else {
                        "Date Range" to "${params.startDate} → ${params.endDate}"
                    }
                }
                RangeExplorerMode.WEEK -> {
                    val cal = Calendar.getInstance()
                    cal.firstDayOfWeek = Calendar.MONDAY
                    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    cal.add(Calendar.WEEK_OF_YEAR, -params.weekOffset)
                    val s = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(cal.time)
                    cal.add(Calendar.DAY_OF_YEAR, 6)
                    val e = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(cal.time)
                    val wTitle = when (params.weekOffset) {
                        0 -> "This Week"
                        1 -> "Last Week"
                        else -> "${params.weekOffset} Weeks Ago"
                    }
                    wTitle to "$s → $e"
                }
                RangeExplorerMode.MONTH -> {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.add(Calendar.MONTH, -params.monthOffset)
                    val monthName = SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
                    val mTitle = when (params.monthOffset) {
                        0 -> "This Month ($monthName)"
                        1 -> "Last Month ($monthName)"
                        else -> monthName
                    }
                    mTitle to monthName
                }
            }

            return SpecificRangeData(
                mode = mode,
                title = title,
                rangeLabel = rangeLabel,
                totalInflow = inFlow,
                totalOutflow = outFlow,
                netChange = inFlow - outFlow,
                transactionCount = matching.size,
                transactions = matching.sortedByDescending { it.createdTimestamp }
            )
        }

        /** Newest end balance of every (phone, account) pair; reconciliation rows carry balances too. */
        fun latestBalances(txs: List<TransactionEntity>): Map<Pair<String, String>, Double> {
            val latest = HashMap<Pair<String, String>, TransactionEntity>()
            for (tx in txs) {
                if (tx.balanceAfter == null) continue
                val key = tx.phoneId to tx.financialAccountId
                val cur = latest[key]
                if (cur == null || tx.createdTimestamp > cur.createdTimestamp ||
                    (tx.createdTimestamp == cur.createdTimestamp && !tx.isReconciliation && cur.isReconciliation)
                ) latest[key] = tx
            }
            return latest.mapValues { it.value.balanceAfter ?: 0.0 }
        }

        fun buildBalanceBoard(
            txs: List<TransactionEntity>,
            inputs: BoardInputs,
            phoneId: String?,
            date: String?
        ): BalanceBoard {
            val scoped = if (phoneId != null) txs.filter { it.phoneId == phoneId } else txs
            val balances = latestBalances(scoped)
            val day = date?.let { DayMatcher.of(it) }
            val activity = if (day == null) scoped else scoped.filter { day.matches(it.transactionDate, it.createdTimestamp) }
            val phoneNames = inputs.phones.associate { it.phoneId to it.friendlyName }
            val sourceNames = inputs.sources.associate { it.sourceId to it.name }
            val manual = inputs.manualSources.filter { phoneId == null || it.phoneId == phoneId }
            val filter = inputs.flowFilter

            fun row(
                key: String,
                title: String,
                subtitle: String,
                rowTxs: List<TransactionEntity>,
                rowBalances: Collection<Double>,
                sourceId: String? = null,
                rowPhoneId: String? = null,
                children: List<BalanceRow> = emptyList()
            ): BalanceRow {
                var credit = 0.0
                var debit = 0.0
                var cCount = 0
                var dCount = 0
                var recovered = 0
                for (t in rowTxs) {
                    when (t.direction) {
                        FlowDirection.CREDIT -> { credit += t.amount; cCount++ }
                        FlowDirection.DEBIT -> { debit += t.amount + t.fee; dCount++ }
                        FlowDirection.UNKNOWN -> Unit
                    }
                    if (t.isReconciliation) recovered++
                }
                val disabled = key in inputs.disabledIds || (sourceId != null && sourceId in inputs.disabledIds)
                return BalanceRow(
                    key = key, title = title, subtitle = subtitle,
                    balance = rowBalances.takeIf { it.isNotEmpty() }?.sum(),
                    credit = credit, debit = debit, creditCount = cCount, debitCount = dCount, recoveredCount = recovered,
                    sourceId = sourceId, phoneId = rowPhoneId, isDisabled = disabled, children = children
                )
            }

            fun manualRow(m: ManualSourceEntity): BalanceRow {
                val mKey = "manual:${m.manualSourceId}"
                val disabled = mKey in inputs.disabledIds || m.manualSourceId in inputs.disabledIds
                return BalanceRow(
                    key = mKey,
                    title = m.name,
                    subtitle = "Manual · ${m.kind.name.lowercase().replaceFirstChar { it.uppercase() }}" +
                        (m.phoneId?.let { " · ${phoneNames[it] ?: it}" } ?: ""),
                    balance = m.balance,
                    isManual = true,
                    manualSource = m,
                    phoneId = m.phoneId,
                    isDisabled = disabled
                )
            }

            val activityBySource = activity.groupBy { it.sourceId }
            val activityByPhone = activity.groupBy { it.phoneId }
            val smsRows: List<BalanceRow>
            val manualRows: List<BalanceRow>

            when (inputs.grouping) {
                BalanceGrouping.BY_SOURCE -> {
                    val sourceIds = (inputs.sources.map { it.sourceId } + balances.keys.map { sourceIdOf(it.second) } + activityBySource.keys).distinct()
                    smsRows = sourceIds.mapNotNull { sid ->
                        val sTx = activityBySource[sid].orEmpty()
                        val sBal = balances.filterKeys { sourceIdOf(it.second) == sid }
                        if (sTx.isEmpty() && sBal.isEmpty() && inputs.sources.none { it.sourceId == sid && it.isActive }) return@mapNotNull null
                        val phonesInSource = (sBal.keys.map { it.first } + sTx.map { it.phoneId }).distinct()
                        val children = phonesInSource.map { pid ->
                            row("$sid|$pid", phoneNames[pid] ?: pid, accountsLabel(sBal.keys.filter { it.first == pid }.map { it.second }),
                                sTx.filter { it.phoneId == pid }, sBal.filterKeys { it.first == pid }.values, sid, pid)
                        }
                        row(sid, sourceNames[sid] ?: sid.uppercase(),
                            "${sTx.size} transactions · ${phonesInSource.size} phone(s)", sTx, sBal.values, sid, null, children)
                    }
                    manualRows = manual.map { manualRow(it) }
                }
                BalanceGrouping.BY_PHONE -> {
                    val phoneIds = (inputs.phones.map { it.phoneId }.filter { phoneId == null || it == phoneId } +
                        balances.keys.map { it.first } + activityByPhone.keys).distinct()
                    smsRows = phoneIds.map { pid ->
                        val pTx = activityByPhone[pid].orEmpty()
                        val pBal = balances.filterKeys { it.first == pid }
                        val sourcesInPhone = (pBal.keys.map { sourceIdOf(it.second) } + pTx.map { it.sourceId }).distinct()
                        val children = sourcesInPhone.map { sid ->
                            row("$pid|$sid", sourceNames[sid] ?: sid.uppercase(), accountsLabel(pBal.keys.filter { sourceIdOf(it.second) == sid }.map { it.second }),
                                pTx.filter { it.sourceId == sid }, pBal.filterKeys { sourceIdOf(it.second) == sid }.values, sid, pid)
                        } + manual.filter { it.phoneId == pid }.map { manualRow(it) }
                        val manualSum = manual.filter { it.phoneId == pid && "manual:${it.manualSourceId}" !in inputs.disabledIds && it.manualSourceId !in inputs.disabledIds }.sumOf { it.balance }
                        val base = row(pid, phoneNames[pid] ?: pid, "${pTx.size} transactions · ${sourcesInPhone.size} source(s)", pTx, pBal.values, null, pid, children)
                        val hasManual = manual.any { it.phoneId == pid }
                        base.copy(balance = if (base.balance == null && !hasManual) null else (base.balance ?: 0.0) + manualSum)
                    }
                    // Manual sources not tied to a phone are listed on their own
                    manualRows = manual.filter { it.phoneId == null || it.phoneId !in phoneIds }.map { manualRow(it) }
                }
            }

            val rows = (smsRows + manualRows).let { list ->
                when (filter) {
                    FlowFilter.ALL -> list
                    FlowFilter.CREDIT -> list.sortedByDescending { it.credit }
                    FlowFilter.DEBIT -> list.sortedByDescending { it.debit }
                }
            }

            // Exclude disabled sources from totals
            val enabledSmsBalances = balances.filterKeys { k ->
                val sid = sourceIdOf(k.second)
                val pid = k.first
                sid !in inputs.disabledIds &&
                pid !in inputs.disabledIds &&
                "${sid}|${pid}" !in inputs.disabledIds &&
                "${pid}|${sid}" !in inputs.disabledIds
            }.values.sum()

            val enabledManualTotal = manual.filter { m ->
                m.manualSourceId !in inputs.disabledIds &&
                "manual:${m.manualSourceId}" !in inputs.disabledIds &&
                (m.phoneId == null || m.phoneId !in inputs.disabledIds)
            }.sumOf { it.balance }

            val enabledCredits = activity.filter {
                val sid = it.sourceId
                val pid = it.phoneId
                it.direction == FlowDirection.CREDIT &&
                sid !in inputs.disabledIds &&
                pid !in inputs.disabledIds &&
                "${sid}|${pid}" !in inputs.disabledIds &&
                "${pid}|${sid}" !in inputs.disabledIds
            }
            val enabledDebits = activity.filter {
                val sid = it.sourceId
                val pid = it.phoneId
                it.direction == FlowDirection.DEBIT &&
                sid !in inputs.disabledIds &&
                pid !in inputs.disabledIds &&
                "${sid}|${pid}" !in inputs.disabledIds &&
                "${pid}|${sid}" !in inputs.disabledIds
            }

            return BalanceBoard(
                rows = rows,
                smsBalanceTotal = enabledSmsBalances,
                manualBalanceTotal = enabledManualTotal,
                totalCredit = enabledCredits.sumOf { it.amount },
                totalDebit = enabledDebits.sumOf { it.amount + it.fee },
                creditCount = enabledCredits.size,
                debitCount = enabledDebits.size,
                grouping = inputs.grouping,
                flowFilter = filter
            )
        }

        private fun sourceIdOf(accountId: String) = accountId.substringBefore(':')

        private fun accountsLabel(accountIds: List<String>): String {
            val tails = accountIds.mapNotNull { id -> id.substringAfter(':', "").takeIf { it.isNotEmpty() } }
            return when {
                tails.isNotEmpty() -> "Account(s) ••" + tails.joinToString(", ••")
                accountIds.isNotEmpty() -> "Wallet"
                else -> "No balance SMS yet"
            }
        }

        fun isSameDay(txDateStr: String, txTimestamp: Long, targetDateStr: String): Boolean {
            val targetClean = targetDateStr.trim()
            if (txDateStr.trim() == targetClean) return true

            val partsTarget = targetClean.split("/", "-", ".")
            val partsTx = txDateStr.split("/", "-", ".")
            if (partsTarget.size == 3 && partsTx.size == 3) {
                val dMatch = partsTarget[0].toIntOrNull() == partsTx[0].toIntOrNull()
                val mMatch = partsTarget[1].toIntOrNull() == partsTx[1].toIntOrNull()
                val yTarget = partsTarget[2].toIntOrNull() ?: 0
                val yTx = partsTx[2].toIntOrNull() ?: 0
                val yMatch = yTarget == yTx || (yTarget % 100) == (yTx % 100)
                if (dMatch && mMatch && yMatch) return true
            }

            if (txTimestamp > 0) {
                val dateFromTs = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(txTimestamp))
                if (dateFromTs == targetClean) return true
                val dateFromTs2 = SimpleDateFormat("dd/MM/yy", Locale.US).format(Date(txTimestamp))
                if (dateFromTs2 == targetClean) return true
            }

            return false
        }

        /**
         * Money in / out of one transaction for charts, using the timeframe-delta rule: internal transfers
         * only count their fee, credits are inflow, debits (incl. fee) are outflow.
         */
        fun cashFlowOf(tx: TransactionEntity): Pair<Double, Double> {
            if (tx.transactionType == TransactionType.INTERNAL_TRANSFER) return 0.0 to tx.fee.coerceAtLeast(0.0)
            return when {
                tx.direction == FlowDirection.CREDIT ||
                    tx.transactionType == TransactionType.INCOME ||
                    tx.transactionType == TransactionType.DEPOSIT -> tx.amount to 0.0
                tx.direction == FlowDirection.DEBIT ||
                    tx.transactionType == TransactionType.EXPENSE ||
                    tx.transactionType == TransactionType.WITHDRAWAL ||
                    tx.transactionType == TransactionType.FEE -> 0.0 to (tx.amount + tx.fee)
                else -> 0.0 to 0.0
            }
        }

        fun computeDashboardCharts(
            txs: List<TransactionEntity>,
            categories: List<CategoryEntity>,
            incomeSources: List<IncomeSourceEntity>,
            range: ChartRange,
            nowMillis: Long = System.currentTimeMillis()
        ): DashboardChartData {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (range.isMonthly) set(Calendar.DAY_OF_MONTH, 1)
                add(if (range.isMonthly) Calendar.MONTH else Calendar.DAY_OF_YEAR, -(range.bucketCount - 1))
            }
            val shortFmt = SimpleDateFormat(
                when {
                    range.isMonthly -> "MMM"
                    range.bucketCount <= 7 -> "EEE"
                    else -> "d/M"
                },
                Locale.US
            )
            val longFmt = SimpleDateFormat(if (range.isMonthly) "MMMM yyyy" else "EEE, d MMM yyyy", Locale.US)
            val starts = LongArray(range.bucketCount + 1)
            val shortLabels = ArrayList<String>(range.bucketCount)
            val longLabels = ArrayList<String>(range.bucketCount)
            for (i in 0..range.bucketCount) {
                starts[i] = cal.timeInMillis
                if (i < range.bucketCount) {
                    shortLabels += shortFmt.format(cal.time)
                    longLabels += longFmt.format(cal.time)
                }
                cal.add(if (range.isMonthly) Calendar.MONTH else Calendar.DAY_OF_YEAR, 1)
            }
            val rangeStart = starts.first()
            val rangeEnd = starts.last()

            val inflow = DoubleArray(range.bucketCount)
            val outflow = DoubleArray(range.bucketCount)
            val counts = IntArray(range.bucketCount)
            val inRange = ArrayList<TransactionEntity>()
            for (tx in txs) {
                val ts = tx.createdTimestamp
                if (ts < rangeStart || ts >= rangeEnd) continue
                inRange += tx
                // Buckets are contiguous and sorted; find the last start <= ts
                var idx = java.util.Arrays.binarySearch(starts, ts)
                if (idx < 0) idx = -idx - 2
                idx = idx.coerceIn(0, range.bucketCount - 1)
                val (credit, debit) = cashFlowOf(tx)
                if (credit == 0.0 && debit == 0.0) continue
                inflow[idx] += credit
                outflow[idx] += debit
                counts[idx]++
            }

            val buckets = (0 until range.bucketCount).map { i ->
                FlowBucket(shortLabels[i], longLabels[i], starts[i], inflow[i], outflow[i], counts[i])
            }
            var running = 0.0
            val cumulative = buckets.map { running += it.net; running }

            val sourceById = incomeSources.associateBy { it.sourceId }
            val incomeGroups = inRange
                .filter { it.transactionType != TransactionType.INTERNAL_TRANSFER && cashFlowOf(it).first > 0.0 }
                .groupBy { tx -> tx.categoryId?.takeIf { it in sourceById } }
            val incomeBySource = incomeGroups.map { (sourceId, list) ->
                val src = sourceId?.let { sourceById[it] }
                IncomeSlice(
                    name = src?.name ?: "Unassigned income",
                    colorHex = src?.colorHex ?: "#94A3B8",
                    amount = list.sumOf { it.amount },
                    transactionCount = list.size
                )
            }.sortedByDescending { it.amount }

            return DashboardChartData(
                range = range,
                buckets = buckets,
                cumulativeNet = cumulative,
                // Moving money between own accounts is not spending
                spending = computeCategorySpends(categories, inRange.filter { it.transactionType != TransactionType.INTERNAL_TRANSFER })
                    .filter { it.totalAmount > 0 },
                incomeBySource = incomeBySource
            )
        }

        /**
         * Turns an AI timeframe ("1-2 weeks", "1 month", "Immediate", "90 days") into a milestone deadline,
         * using the upper bound of a range. Unknown wording falls back to 30 days.
         */
        fun deadlineFromTimeframe(timeframe: String, nowMillis: Long = System.currentTimeMillis()): Long {
            val text = timeframe.lowercase(Locale.ROOT)
            val dayMs = 24L * 3600 * 1000
            if (text.contains("immediate") || text.contains("today") || text.contains("now")) return nowMillis + 7 * dayMs
            val number = Regex("\\d+").findAll(text).mapNotNull { it.value.toIntOrNull() }.maxOrNull() ?: 1
            val unitDays = when {
                text.contains("year") -> 365
                text.contains("month") -> 30
                text.contains("week") -> 7
                text.contains("day") -> 1
                else -> return nowMillis + 30 * dayMs
            }
            return nowMillis + (number.coerceIn(1, 520) * unitDays) * dayMs
        }

        /**
         * Yearly return on an opportunity's capital after inflation, from its estimated benefit; null when
         * it needs no capital (pure cost cuts) or inflation is unknown.
         */
        fun opportunityRealReturnPct(opp: AdvisorOpportunity, inflationPct: Double?): Double? {
            if (inflationPct == null || opp.capitalRequiredEtb <= 0.0 || opp.estimatedBenefitEtb <= 0.0) return null
            val period = opp.benefitPeriod.lowercase(Locale.ROOT)
            val yearlyBenefit = when {
                period.contains("month") -> opp.estimatedBenefitEtb * 12
                period.contains("week") -> opp.estimatedBenefitEtb * 52
                else -> opp.estimatedBenefitEtb
            }
            return InvestmentCatalog.realReturnPct(yearlyBenefit / opp.capitalRequiredEtb * 100.0, 0.0, 0.0, inflationPct)
        }

        /** Same debit rule as the categories tab's "uncategorized expenses" list. */
        fun isSpendingDebit(tx: TransactionEntity): Boolean =
            tx.direction == FlowDirection.DEBIT ||
                tx.transactionType == TransactionType.EXPENSE ||
                tx.transactionType == TransactionType.WITHDRAWAL ||
                tx.transactionType == TransactionType.FEE ||
                tx.transactionType == TransactionType.TRANSFER

        /**
         * Spending per manual category (all of them, even at zero) and one "Uncategorized" row with the
         * sum of debits that have no known category yet. Percentages are shares of all debits.
         */
        fun computeCategorySpends(categories: List<CategoryEntity>, transactions: List<TransactionEntity>): List<CategorySpend> {
            val debits = transactions.filter { isSpendingDebit(it) }
            val totalExpense = debits.sumOf { it.amount + it.fee }
            val knownIds = categories.map { it.categoryId }.toSet()
            val byCategory = debits.groupBy { tx ->
                tx.categoryId?.takeIf { it.isNotBlank() && it in knownIds }
            }
            fun share(amount: Double) = if (totalExpense > 0) (amount / totalExpense).toFloat() else 0f

            val rows = categories.map { cat ->
                val txs = byCategory[cat.categoryId].orEmpty()
                val amount = txs.sumOf { it.amount + it.fee }
                CategorySpend(category = cat, totalAmount = amount, percentage = share(amount), transactionCount = txs.size)
            }.sortedByDescending { it.totalAmount }

            val pending = byCategory[null].orEmpty()
            if (pending.isEmpty()) return rows
            val pendingAmount = pending.sumOf { it.amount + it.fee }
            return listOf(
                CategorySpend(
                    category = UNCATEGORIZED_SPEND_CATEGORY,
                    totalAmount = pendingAmount,
                    percentage = share(pendingAmount),
                    transactionCount = pending.size,
                    isUncategorized = true
                )
            ) + rows
        }

        fun isDebitInternalTransfer(tx: TransactionEntity): Boolean {
            if (tx.direction != FlowDirection.UNKNOWN) return tx.direction == FlowDirection.DEBIT
            val desc = tx.description.lowercase(Locale.ROOT)
            return desc.contains("sent") || desc.contains("transferred") || desc.contains("debit") || desc.contains("payment") || tx.recipient != null
        }
    }
}

data class FilterCriteria(
    val phoneId: String?,
    val sourceId: String?,
    val type: TransactionType?,
    val date: String?,
    val query: String
)

data class BoardInputs(
    val sources: List<FinancialSourceEntity>,
    val phones: List<RegisteredPhoneEntity>,
    val manualSources: List<ManualSourceEntity>,
    val grouping: BalanceGrouping,
    val flowFilter: FlowFilter,
    val disabledIds: Set<String>
)

/**
 * Fast "is this transaction on the selected day" check. Formatting a SimpleDateFormat per transaction
 * (the old approach) was the main cost of every dashboard recomposition with thousands of rows.
 */
class DayMatcher private constructor(
    private val day: Int,
    private val month: Int,
    private val year: Int,
    private val startMillis: Long,
    private val endMillis: Long
) {
    fun matches(txDate: String, timestamp: Long): Boolean {
        if (timestamp in startMillis until endMillis) return true
        val parts = txDate.split('/', '-', '.')
        if (parts.size == 3) {
            val p0 = parts[0].trim().toIntOrNull() ?: -1
            val p1 = parts[1].trim().toIntOrNull() ?: -1
            val p2 = parts[2].trim().take(4).toIntOrNull() ?: -1
            if (p0 > 31) {
                // yyyy-MM-dd
                val y = p0
                val m = p1
                val d = p2
                if (d == day && m == month && (y == year || y % 100 == year % 100)) return true
            } else {
                // dd/MM/yyyy
                val d = p0
                val m = p1
                val y = p2
                if (d == day && m == month && (y == year || y % 100 == year % 100)) return true
            }
        }
        return false
    }

    companion object {
        fun of(date: String): DayMatcher? {
            val parts = date.trim().split('/', '-', '.')
            if (parts.size != 3) return null
            val d = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            var y = parts[2].toIntOrNull() ?: return null
            if (y < 100) y += 2000
            val cal = Calendar.getInstance().apply {
                clear()
                set(y, m - 1, d, 0, 0, 0)
            }
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            return DayMatcher(d, m, y, start, cal.timeInMillis)
        }
    }
}

class MainViewModelFactory(
    private val repository: FinancialRepository,
    private val smsSync: SmsSyncCoordinator,
    private val cloudSync: CloudSyncManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, smsSync, cloudSync) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
