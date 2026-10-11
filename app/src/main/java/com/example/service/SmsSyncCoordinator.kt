package com.example.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.example.data.repository.FinancialRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the database in step with the SMS inbox so the dashboard (which observes Room flows) is
 * always current without manual refreshes:
 *
 * - live SMS broadcasts are ingested immediately,
 * - a ContentObserver on the SMS provider triggers a cheap incremental scan (only SMS newer than the
 *   last scan) for anything the broadcast missed,
 * - the very first run / a parser upgrade performs one full scan, with progress.
 *
 * Scans never overlap; a request arriving mid-scan is folded into one follow-up incremental scan.
 */
class SmsSyncCoordinator(
    private val context: Context,
    private val repository: FinancialRepository,
    private val scope: CoroutineScope
) {
    private val scanMutex = Mutex()
    private val reader = SmsInboxReader(context, repository)

    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    private val _lastSummary = MutableStateFlow<InboxScanSummary?>(null)
    /** Summary of the last scan the user explicitly asked for (shown as a dialog). */
    val lastSummary: StateFlow<InboxScanSummary?> = _lastSummary.asStateFlow()

    @Volatile private var pendingIncremental = false
    private var observer: ContentObserver? = null
    private var debounceJob: Job? = null
    private var startupJob: Job? = null

    fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** Startup housekeeping (device id, one-time re-parse after an upgrade). Idempotent. */
    fun start(): Job {
        startupJob?.let { return it }
        return scope.launch {
            repository.seedInitialDataIfEmpty()
            repository.ensureLocalDeviceIdentity()
            repository.rebuildFromStoredSmsIfParserChanged()
            CategorizeNotificationHelper.syncUncategorizedDebitNotifications(context, repository)
        }.also { startupJob = it }
    }

    /** Called when SMS permission is available: quick catch-up with the latest bank SMS and watch it from now on. */
    fun onSmsPermissionGranted() {
        registerObserver()
        requestQuickScan()
    }

    /** Fast instant startup scan — only reads the latest few transactional SMS (< 50ms) */
    fun requestQuickScan(userInitiated: Boolean = false, onDone: () -> Unit = {}) {
        if (!hasSmsPermission()) return
        scope.launch {
            start().join()
            if (scanMutex.isLocked) return@launch
            scanMutex.withLock {
                val phoneId = repository.localDeviceId()
                val summary = reader.quickStartupScan(phoneId, limit = 20)
                if (userInitiated) {
                    _lastSummary.value = summary
                }
            }
            onDone()
        }
    }

    /** Full scan of entire historical inbox across all past messages — only run when user orders it */
    fun requestFullHistoricalScan(onProgress: (ScanProgress) -> Unit = {}) {
        if (!hasSmsPermission()) return
        scope.launch {
            start().join()
            scanMutex.withLock {
                val phoneId = repository.localDeviceId()
                val summary = reader.scanInbox(phoneId, fullRescan = true) {
                    _progress.value = it
                    onProgress(it)
                }
                _progress.value = ScanProgress()
                _lastSummary.value = summary
            }
        }
    }

    fun requestScan(fullRescan: Boolean = false, userInitiated: Boolean = true) {
        if (!hasSmsPermission()) return
        scope.launch {
            start().join()
            if (scanMutex.isLocked) {
                pendingIncremental = true
                return@launch
            }
            scanMutex.withLock {
                do {
                    pendingIncremental = false
                    val phoneId = repository.localDeviceId()
                    val summary = if (fullRescan) {
                        reader.scanInbox(phoneId, fullRescan = true) { _progress.value = it }
                    } else {
                        reader.scanInbox(phoneId, fullRescan = false) { _progress.value = it }
                    }
                    _progress.value = ScanProgress()
                    if (userInitiated) _lastSummary.value = summary
                } while (pendingIncremental)
            }
        }
    }

    /** Live broadcast path: store the SMS right away, before the provider even writes it. */
    fun onLiveSms(sender: String, body: String, timestamp: Long, onDone: () -> Unit = {}) {
        scope.launch {
            try {
                start().join()
                val sources = repository.getActiveSources()
                val identifiers = repository.getActiveSenderIdentifiers()
                if (com.example.parser.SmsParserEngine.matchSenderIdentifier(sender, sender, sources, identifiers) != null) {
                    repository.processIncomingSms(repository.localDeviceId(), sender, body, timestamp)
                }
            } finally {
                onDone()
            }
        }
    }

    fun dismissSummary() {
        _lastSummary.value = null
    }

    private fun registerObserver() {
        if (observer != null || !hasSmsPermission()) return
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // The provider fires several notifications per SMS; coalesce them into one scan
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(OBSERVER_DEBOUNCE_MS)
                    requestScan(fullRescan = false, userInitiated = false)
                }
            }
        }
        try {
            context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, obs)
            observer = obs
        } catch (_: SecurityException) {
        }
    }

    companion object {
        private const val OBSERVER_DEBOUNCE_MS = 1500L
    }
}
