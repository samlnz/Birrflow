package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.RoseExpense
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AdminHubScreen
import com.example.ui.screens.IncomeAndExpenseCategoriesScreen
import com.example.ui.screens.BusinessAdvisorScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.MultiPhoneScreen
import com.example.ui.screens.ProfileHubScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PurpleFintech
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.MainViewModelFactory
import com.example.receiver.CategorizeActionReceiver

import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Psychology

enum class AppDestination(val title: String, val icon: ImageVector, val inBottomBar: Boolean = true) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    TRANSACTIONS("Transactions", Icons.Filled.ReceiptLong),
    PHONES("Phones", Icons.Default.PhoneAndroid),
    SOURCES("Categories", Icons.Default.Category),
    ADVISOR("AI Advisor", Icons.Default.Psychology),
    ADMIN("Admin Hub", Icons.Default.AdminPanelSettings),
    // Opened from the person icon in the top bar and from the AI Advisor
    ME("Me", Icons.Default.Person, inBottomBar = false)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start background sync service
        com.example.service.BirrFlowSyncService.startService(this)

        setContent {
            MyApplicationTheme {
                BirrFlowApp(intent = intent)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirrFlowApp(intent: android.content.Intent? = null) {
    val context = LocalContext.current
    val app = context.applicationContext as BirrFlowApplication
    val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(app.repository, app.smsSync, app.cloudSync))

    var currentScreen by remember { mutableStateOf(AppDestination.DASHBOARD) }

    // Handle deep link / categorization notification intent
    LaunchedEffect(intent) {
        val txId = intent?.getStringExtra(CategorizeActionReceiver.EXTRA_TX_ID)
        val shouldOpen = intent?.getBooleanExtra(CategorizeActionReceiver.EXTRA_OPEN_CATEGORIZE_POPUP, false) == true
        if (!txId.isNullOrBlank() && shouldOpen) {
            currentScreen = AppDestination.SOURCES
            viewModel.openTransactionDetailById(txId)
        }
        if (intent?.getBooleanExtra(com.example.service.WeeklyBriefingScheduler.EXTRA_OPEN_ADVISOR, false) == true) {
            currentScreen = AppDestination.ADVISOR
        }
    }

    val phones by viewModel.allPhones.collectAsStateWithLifecycle()
    val failedSmsList by viewModel.failedOrUnknownSms.collectAsStateWithLifecycle()
    val uncategorizedExpenses by viewModel.uncategorizedExpenses.collectAsStateWithLifecycle()
    val selectedPhoneId by viewModel.selectedPhoneId.collectAsStateWithLifecycle()

    val isScanningInbox by viewModel.isScanningInbox.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val scanSummary by viewModel.inboxScanSummary.collectAsStateWithLifecycle()
    val cloudSyncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()

    // Permission state check
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showPermissionBanner by remember { mutableStateOf(!hasSmsPermission) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.RECEIVE_SMS] == true || permissions[Manifest.permission.READ_SMS] == true
        hasSmsPermission = granted
        if (granted) {
            showPermissionBanner = false
            // First run: full index with progress; afterwards the inbox is watched live
            viewModel.onSmsPermissionGranted()
        }
        com.example.service.BirrFlowSyncService.startService(context)
    }

    LaunchedEffect(hasSmsPermission) {
        if (hasSmsPermission) {
            // Cheap incremental catch-up (only SMS newer than the last scan)
            viewModel.onSmsPermissionGranted()
        }
    }

    // BackHandler: return to Dashboard if on another screen
    BackHandler(enabled = currentScreen != AppDestination.DASHBOARD) {
        currentScreen = AppDestination.DASHBOARD
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("B", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BirrFlow",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = EmeraldPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "REAL-TIME SMS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { currentScreen = AppDestination.ME },
                        modifier = Modifier.testTag("appbar_me_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "About me, net worth and AI memory",
                            tint = if (currentScreen == AppDestination.ME) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.syncAllDevicesData()
                            if (!hasSmsPermission) {
                                permissionLauncher.launch(
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        arrayOf(
                                            Manifest.permission.RECEIVE_SMS,
                                            Manifest.permission.READ_SMS,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    } else {
                                        arrayOf(
                                            Manifest.permission.RECEIVE_SMS,
                                            Manifest.permission.READ_SMS
                                        )
                                    }
                                )
                            }
                        },
                        modifier = Modifier.testTag("appbar_scan_inbox_button")
                    ) {
                        val isBusySyncing = isScanningInbox || cloudSyncState.isUploading || cloudSyncState.isPulling || cloudSyncState.busy != null
                        if (isBusySyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = EmeraldPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync All Devices Data",
                                tint = EmeraldPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("main_bottom_nav"),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp
            ) {
                AppDestination.values().filter { it.inBottomBar }.forEach { destination ->
                    val isSelected = currentScreen == destination
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = destination },
                        icon = {
                            if (destination == AppDestination.ADMIN && failedSmsList.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text("${failedSmsList.size}") } }) {
                                    Icon(destination.icon, contentDescription = destination.title)
                                }
                            } else if (destination == AppDestination.SOURCES && uncategorizedExpenses.isNotEmpty()) {
                                BadgedBox(badge = { Badge(containerColor = RoseExpense) { Text("${uncategorizedExpenses.size}") } }) {
                                    Icon(destination.icon, contentDescription = destination.title)
                                }
                            } else {
                                Icon(destination.icon, contentDescription = destination.title)
                            }
                        },
                        label = {
                            Text(
                                text = destination.title,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EmeraldPrimary,
                            selectedTextColor = EmeraldPrimary,
                            indicatorColor = EmeraldPrimary.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("nav_item_${destination.name}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (scanProgress.isRunning && scanProgress.total > 0) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    LinearProgressIndicator(
                        progress = { scanProgress.scanned.toFloat() / scanProgress.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                        color = EmeraldPrimary
                    )
                    Text(
                        text = (if (scanProgress.isFullScan) "Indexing bank SMS " else "Checking new bank SMS ") +
                            "${scanProgress.scanned}/${scanProgress.total} · ${scanProgress.newTransactions} transactions",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when (currentScreen) {
                    AppDestination.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        onRequestSmsPermission = {
                            permissionLauncher.launch(
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    arrayOf(
                                        Manifest.permission.RECEIVE_SMS,
                                        Manifest.permission.READ_SMS,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                } else {
                                    arrayOf(
                                        Manifest.permission.RECEIVE_SMS,
                                        Manifest.permission.READ_SMS
                                    )
                                }
                            )
                        },
                        showPermissionBanner = showPermissionBanner,
                        onDismissPermissionBanner = { showPermissionBanner = false },
                        onNavigateToTransactions = { currentScreen = AppDestination.TRANSACTIONS }
                    )
                    AppDestination.TRANSACTIONS -> TransactionsScreen(
                        viewModel = viewModel
                    )
                    AppDestination.PHONES -> MultiPhoneScreen(
                        viewModel = viewModel
                    )
                    AppDestination.SOURCES -> IncomeAndExpenseCategoriesScreen(
                        viewModel = viewModel
                    )
                    AppDestination.ADVISOR -> BusinessAdvisorScreen(
                        viewModel = viewModel,
                        onOpenProfile = { currentScreen = AppDestination.ME }
                    )
                    AppDestination.ME -> ProfileHubScreen(
                        viewModel = viewModel
                    )
                    AppDestination.ADMIN -> AdminHubScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    scanSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissInboxScanSummary() },
            title = { Text(if (summary.isIncremental) "Up to date" else "Phone SMS Inbox Scan Complete") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        (if (summary.isIncremental) "New bank SMS read: " else "Bank SMS read from inbox: ") +
                            "${summary.totalSmsRead} (${summary.durationMs} ms)"
                    )
                    Text("New bank transactions imported: ${summary.newTransactions}", fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                    Text("Duplicates skipped: ${summary.duplicatesSkipped}")
                    if (summary.unknownSenders > 0) {
                        Text("Unknown senders flagged for review: ${summary.unknownSenders}", color = AmberAccent)
                    }
                    if (summary.unknownFormats > 0) {
                        Text("Unrecognized formats flagged: ${summary.unknownFormats}", color = AmberAccent)
                    }
                    if (summary.errorMessage != null) {
                        Text("Notice: ${summary.errorMessage}", color = RoseExpense)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissInboxScanSummary() }) {
                    Text("Done")
                }
            }
        )
    }
}
