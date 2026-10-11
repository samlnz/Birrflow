package com.example.sync

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.entity.RegisteredPhoneEntity
import com.example.data.repository.FinancialRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DiscoveredDevice(
    val deviceId: String,
    val friendlyName: String,
    val modelName: String,
    val isCurrentDevice: Boolean = false,
    val isSynced: Boolean = false,
    val lastActive: Long = 0L,
    val transactionCount: Int = 0
)

data class CloudSyncState(
    val available: Boolean = true,
    val connected: Boolean = false,
    val online: Boolean = false,
    val pendingUploads: Int = 0,
    val uploadProgressCount: Int = 0,
    val uploadProgressTotal: Int = 0,
    val isUploading: Boolean = false,
    val pullProgressCount: Int = 0,
    val pullProgressTotal: Int = 0,
    val isPulling: Boolean = false,
    val pullingDeviceName: String? = null,
    val lastReceivedAt: Long? = null,
    val lastUploadedAt: Long? = null,
    val busy: String? = null,
    val error: String? = null,
    val lastMergeInfo: String? = null,
    val needsFirebaseConfig: Boolean = false,
    val backendType: String = "RAILWAY", // "RAILWAY" or "FIREBASE"
    val savedDatabaseUrl: String? = null,
    val savedProjectId: String? = null,
    val savedApiKey: String? = null,
    val activeSyncedDeviceIds: Set<String> = emptySet(),
    val discoveredDevices: List<DiscoveredDevice> = emptyList()
) {
    val linked: Boolean get() = connected || !savedDatabaseUrl.isNullOrBlank() || !savedProjectId.isNullOrBlank()
    val linkCode: String? get() = savedDatabaseUrl ?: savedProjectId
    val uploadPercent: Int get() = if (uploadProgressTotal > 0) ((uploadProgressCount.toFloat() / uploadProgressTotal) * 100).toInt().coerceIn(0, 100) else 100
    val pullPercent: Int get() = if (pullProgressTotal > 0) ((pullProgressCount.toFloat() / pullProgressTotal) * 100).toInt().coerceIn(0, 100) else 100
}

/**
 * Universal Multi-Phone Cloud Mesh supporting both Railway Database URLs (High-volume 19,000+ items) and Firebase.
 */
@OptIn(FlowPreview::class)
class CloudSyncManager(
    private val context: Context,
    private val repository: FinancialRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(CloudSyncState())
    val state: StateFlow<CloudSyncState> = _state.asStateFlow()

    private val pushMutex = Mutex()
    private val sessionMutex = Mutex()
    private var discoveryListener: ListenerRegistration? = null
    private val pullListeners = ConcurrentHashMap<String, ListenerRegistration>()
    private var pushJob: Job? = null
    private var discoveryJob: Job? = null

    @Volatile private var deviceId: String = ""
    @Volatile private var groupKey: ByteArray? = null

    private suspend fun isFirebaseReady(): Boolean {
        if (FirebaseApp.getApps(context).isNotEmpty()) return true
        val projId = repository.getState("FIREBASE_PROJECT_ID")
        val apiKey = repository.getState("FIREBASE_API_KEY")
        val appId = repository.getState("FIREBASE_APP_ID") ?: "com.aistudio.banksms.kzetqw"
        if (!projId.isNullOrBlank() && !apiKey.isNullOrBlank()) {
            return try {
                val options = com.google.firebase.FirebaseOptions.Builder()
                    .setApiKey(apiKey)
                    .setProjectId(projId)
                    .setApplicationId(appId)
                    .build()
                FirebaseApp.initializeApp(context, options)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Dynamic Firebase initialization failed", e)
                false
            }
        }
        return false
    }

    suspend fun configureRailway(databaseUrl: String): Boolean {
        val cleanUrl = databaseUrl.trim()
        if (cleanUrl.isBlank()) {
            _state.update { it.copy(error = "Please enter a valid Railway Public Database URL.") }
            return false
        }
        repository.putState("RAILWAY_DATABASE_URL", cleanUrl)
        repository.putState("BACKEND_TYPE", "RAILWAY")
        _state.update { it.copy(savedDatabaseUrl = cleanUrl, backendType = "RAILWAY") }
        return try {
            _state.update { it.copy(busy = "Connecting & initializing Railway PostgreSQL database…", error = null) }
            RailwayDatabaseEngine.initSchema(cleanUrl)
            _state.update {
                it.copy(
                    available = true,
                    error = null,
                    needsFirebaseConfig = false,
                    lastMergeInfo = "Connected to Railway database! Ready for high-volume 19,000+ transaction sync."
                )
            }
            startCloudSync()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Railway database initialization failed", e)
            _state.update { it.copy(busy = null, error = "Railway Connection Error: ${e.localizedMessage}") }
            false
        }
    }

    suspend fun configureFirebase(projectId: String, apiKey: String, appId: String): Boolean {
        val cleanPid = projectId.trim()
        val cleanKey = apiKey.trim()
        val cleanAppId = appId.trim()
        repository.putState("FIREBASE_PROJECT_ID", cleanPid)
        repository.putState("FIREBASE_API_KEY", cleanKey)
        repository.putState("FIREBASE_APP_ID", cleanAppId)
        repository.putState("BACKEND_TYPE", "FIREBASE")
        _state.update { it.copy(savedProjectId = cleanPid, savedApiKey = cleanKey, backendType = "FIREBASE") }
        val ok = isFirebaseReady()
        if (ok) {
            _state.update {
                it.copy(
                    available = true,
                    error = null,
                    needsFirebaseConfig = false,
                    lastMergeInfo = "Connected to Firebase Project '$cleanPid'! Discovering connected phones…"
                )
            }
            startCloudSync()
        } else {
            _state.update { it.copy(error = "Could not connect to Firebase with Project ID '$cleanPid'. Please check your credentials.") }
        }
        return ok
    }

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    /** Called once at app start: resumes cloud sync and device discovery. */
    fun start() {
        scope.launch {
            val dbUrl = repository.getState("RAILWAY_DATABASE_URL")
            val savedPid = repository.getState("FIREBASE_PROJECT_ID")
            val savedKey = repository.getState("FIREBASE_API_KEY")
            val savedBackend = repository.getState("BACKEND_TYPE") ?: if (!dbUrl.isNullOrBlank()) "RAILWAY" else "FIREBASE"

            val ready = if (savedBackend == "RAILWAY") !dbUrl.isNullOrBlank() else isFirebaseReady()
            _state.update {
                it.copy(
                    available = true,
                    needsFirebaseConfig = !ready,
                    backendType = savedBackend,
                    savedDatabaseUrl = dbUrl,
                    savedProjectId = savedPid,
                    savedApiKey = savedKey
                )
            }
            repository.pendingUploads.collect { n -> _state.update { it.copy(pendingUploads = n) } }
        }
        scope.launch {
            startCloudSync()
        }
    }

    /** Connects to Railway or Firebase, publishes local device identity, pushes outbox, and starts discovery. */
    fun startCloudSync() = scope.launch {
        sessionMutex.withLock {
            val dbUrl = repository.getState("RAILWAY_DATABASE_URL")
            val savedBackend = repository.getState("BACKEND_TYPE") ?: if (!dbUrl.isNullOrBlank()) "RAILWAY" else "FIREBASE"
            val ready = if (savedBackend == "RAILWAY") !dbUrl.isNullOrBlank() else isFirebaseReady()

            if (!ready) {
                _state.update { it.copy(needsFirebaseConfig = true, connected = false) }
                return@launch
            }

            try {
                _state.update { it.copy(busy = "Connecting to $savedBackend database…", error = null) }
                deviceId = repository.localDeviceId()
                repository.ensureLocalDeviceIdentity()
                val phone = repository.phoneById(deviceId)
                val model = Build.MODEL.ifBlank { "Android Phone" }
                val devName = phone?.friendlyName ?: "Primary Phone ($model)"

                if (savedBackend == "RAILWAY" && !dbUrl.isNullOrBlank()) {
                    groupKey = SyncCodec.projectKeyFor(dbUrl)
                    RailwayDatabaseEngine.initSchema(dbUrl)
                    RailwayDatabaseEngine.publishDevice(dbUrl, deviceId, devName, model)
                } else {
                    ensureSignedIn()
                    val projId = repository.getState("FIREBASE_PROJECT_ID") ?: "default"
                    groupKey = SyncCodec.projectKeyFor(projId)
                    val devDoc = mapOf(
                        "deviceId" to deviceId,
                        "friendlyName" to devName,
                        "modelName" to model,
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "lastActive" to System.currentTimeMillis()
                    )
                    db.collection("devices").document(deviceId).set(devDoc).awaitTask()
                }

                // Enable local outbox queuing and start pusher
                repository.setCloudSyncEnabled(true)
                pushJob?.cancel()
                pushJob = scope.launch {
                    repository.outboxSignal.debounce(PUSH_DEBOUNCE_MS).collect { pushNow(rewriteRemote = false) }
                }

                val savedSynced = repository.getSyncedDeviceIds()
                _state.update {
                    it.copy(
                        connected = true,
                        online = true,
                        activeSyncedDeviceIds = savedSynced,
                        busy = null,
                        error = null
                    )
                }

                startDeviceDiscovery()
                refreshPullListeners()

            } catch (e: Exception) {
                Log.e(TAG, "startCloudSync failed", e)
                _state.update { it.copy(busy = null, error = friendlyError(e)) }
            }
        }
    }

    private fun startDeviceDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = scope.launch(Dispatchers.IO) {
            val dbUrl = repository.getState("RAILWAY_DATABASE_URL")
            val savedBackend = repository.getState("BACKEND_TYPE") ?: if (!dbUrl.isNullOrBlank()) "RAILWAY" else "FIREBASE"

            if (savedBackend == "RAILWAY" && !dbUrl.isNullOrBlank()) {
                while (isActive) {
                    try {
                        if (!_state.value.isUploading && !_state.value.isPulling) {
                            // Automatically upload any new pending local transactions to Railway cloud
                            val pending = repository.getOutboxCount()
                            if (pending > 0) {
                                pushNow(rewriteRemote = false)
                            }

                            val currentDbUrl = repository.getState("RAILWAY_DATABASE_URL") ?: dbUrl
                            val syncedIds = _state.value.activeSyncedDeviceIds
                            val list = RailwayDatabaseEngine.fetchDiscoveredDevices(currentDbUrl, deviceId, syncedIds)
                            for (dev in list) {
                                if (!dev.isCurrentDevice) {
                                    repository.registerRemotePhone(dev.deviceId, dev.friendlyName, dev.modelName)
                                }
                            }
                            _state.update { it.copy(discoveredDevices = list, online = true) }

                            // Automatically pull any updates from all actively synced remote phones ONLY if initial pull is completed!
                            val activeRemotes = syncedIds.filter { it != deviceId }
                            for (rem in activeRemotes) {
                                if (repository.getState("INITIAL_PULL_COMPLETED_FOR_$rem") == "1") {
                                    pullRemoteDeviceData(rem, isPeriodicPoll = true)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Railway device discovery notice", e)
                    }
                    delay(15_000L) // Poll Railway cloud database every 15 seconds
                }
            } else {
                discoveryListener?.remove()
                discoveryListener = db.collection("devices").addSnapshotListener { snap, err ->
                    if (err != null) {
                        Log.w(TAG, "Device discovery listener error", err)
                        _state.update { it.copy(online = false) }
                        return@addSnapshotListener
                    }
                    if (snap == null) return@addSnapshotListener
                    val syncedIds = _state.value.activeSyncedDeviceIds
                    val localId = deviceId
                    val list = snap.documents.mapNotNull { doc ->
                        val id = doc.getString("deviceId") ?: doc.id
                        val name = doc.getString("friendlyName") ?: "Synced Phone (${id.takeLast(6)})"
                        val model = doc.getString("modelName") ?: "Android Phone"
                        val lastAct = doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
                        if (id != localId && id.isNotBlank()) {
                            scope.launch(Dispatchers.IO) {
                                repository.registerRemotePhone(id, name, model)
                            }
                        }
                        DiscoveredDevice(
                            deviceId = id,
                            friendlyName = name,
                            modelName = model,
                            isCurrentDevice = (id == localId),
                            isSynced = (id == localId || id in syncedIds),
                            lastActive = lastAct
                        )
                    }
                    _state.update { it.copy(discoveredDevices = list, online = true) }
                }
            }
        }
    }

    /** Enables real-time / batch pulling for a specific remote phone with live Progress Bar and %. */
    fun syncDevice(remoteDeviceId: String) = scope.launch {
        val updated = repository.setSyncedDeviceId(remoteDeviceId, sync = true)
        val dev = _state.value.discoveredDevices.firstOrNull { it.deviceId == remoteDeviceId }
        if (dev != null) {
            repository.registerRemotePhone(dev.deviceId, dev.friendlyName, dev.modelName)
        }
        _state.update { s ->
            s.copy(
                activeSyncedDeviceIds = updated,
                discoveredDevices = s.discoveredDevices.map { d ->
                    if (d.deviceId == remoteDeviceId) d.copy(isSynced = true) else d
                },
                lastMergeInfo = "Sync enabled for ${dev?.friendlyName ?: remoteDeviceId}!"
            )
        }
        pullRemoteDeviceData(remoteDeviceId, isPeriodicPoll = false)
    }

    fun unsyncDevice(remoteDeviceId: String) = scope.launch {
        val updated = repository.setSyncedDeviceId(remoteDeviceId, sync = false)
        _state.update { s ->
            s.copy(
                activeSyncedDeviceIds = updated,
                discoveredDevices = s.discoveredDevices.map { d ->
                    if (d.deviceId == remoteDeviceId) d.copy(isSynced = false) else d
                }
            )
        }
        refreshPullListeners()
    }

    fun syncAllDevices() = scope.launch {
        val allRemotes = _state.value.discoveredDevices.filter { !it.isCurrentDevice }
        var current = _state.value.activeSyncedDeviceIds
        for (dev in allRemotes) {
            repository.registerRemotePhone(dev.deviceId, dev.friendlyName, dev.modelName)
            current = repository.setSyncedDeviceId(dev.deviceId, sync = true)
        }
        _state.update { s ->
            s.copy(
                activeSyncedDeviceIds = current,
                discoveredDevices = s.discoveredDevices.map { d ->
                    if (!d.isCurrentDevice) d.copy(isSynced = true) else d
                },
                lastMergeInfo = "Sync enabled for all connected phones!"
            )
        }
        for (dev in allRemotes) {
            pullRemoteDeviceData(dev.deviceId, isPeriodicPoll = false)
        }
    }

    /** Requirement 2: Pulls remote phone data with live Progress Bar & Percentage. */
    private fun pullRemoteDeviceData(
        remoteDeviceId: String,
        isPeriodicPoll: Boolean = false,
        forceFullPull: Boolean = false
    ) = scope.launch(Dispatchers.IO) {
        val key = groupKey ?: return@launch
        val dev = _state.value.discoveredDevices.firstOrNull { it.deviceId == remoteDeviceId }
        val devName = dev?.friendlyName ?: "Remote Phone ($remoteDeviceId)"

        val dbUrl = repository.getState("RAILWAY_DATABASE_URL")
        val savedBackend = repository.getState("BACKEND_TYPE") ?: if (!dbUrl.isNullOrBlank()) "RAILWAY" else "FIREBASE"

        val lastPullTsKey = "LAST_PULL_TS_FOR_$remoteDeviceId"
        val isFirstTime = repository.getState("INITIAL_PULL_COMPLETED_FOR_$remoteDeviceId") != "1"
        val lastPullTs = if (forceFullPull || isFirstTime) 0L else (repository.getState(lastPullTsKey)?.toLongOrNull() ?: 0L)

        if (savedBackend == "RAILWAY" && !dbUrl.isNullOrBlank()) {
            try {
                if (!isPeriodicPoll) {
                    _state.update {
                        it.copy(
                            isPulling = true,
                            pullProgressCount = 0,
                            pullProgressTotal = 0,
                            pullingDeviceName = devName,
                            busy = if (lastPullTs == 0L) "Connecting to Railway to pull all $devName history…" else "Checking Railway for new $devName transactions…"
                        )
                    }
                }

                val totalCount = RailwayDatabaseEngine.countRemoteDeviceItems(dbUrl, remoteDeviceId, afterTs = lastPullTs)
                if (totalCount == 0) {
                    if (!isPeriodicPoll) {
                        _state.update {
                            it.copy(
                                isPulling = false,
                                pullProgressCount = 0,
                                pullProgressTotal = 0,
                                busy = null,
                                lastMergeInfo = "$devName is up to date (no new items)."
                            )
                        }
                    }
                    return@launch
                }

                _state.update {
                    it.copy(
                        isPulling = true,
                        pullProgressCount = 0,
                        pullProgressTotal = totalCount,
                        pullingDeviceName = devName,
                        busy = if (lastPullTs == 0L) "Pulling all $totalCount transactions from $devName…" else "Pulling $totalCount new transaction(s) from $devName…"
                    )
                }

                var pulledSoFar = 0
                val batchSize = 500
                RailwayDatabaseEngine.withConnection(dbUrl) { conn ->
                    while (pulledSoFar < totalCount) {
                        val batchRecords = RailwayDatabaseEngine.fetchRemoteDeviceItemsWithConnection(conn, remoteDeviceId, batchSize, pulledSoFar, afterTs = lastPullTs)
                        if (batchRecords.isEmpty()) break

                        val changes = SyncRecords.open(batchRecords, key, deviceId) { id, e ->
                            Log.w(TAG, "Skipping undecryptable record $id: ${e.message}")
                        }
                        if (!changes.isEmpty) {
                            repository.applyRemoteChanges(changes)
                        }

                        pulledSoFar += batchRecords.size
                        _state.update {
                            it.copy(
                                pullProgressCount = pulledSoFar,
                                pullProgressTotal = maxOf(totalCount, pulledSoFar),
                                lastReceivedAt = System.currentTimeMillis()
                            )
                        }
                    }
                }

                val nowTs = System.currentTimeMillis()
                repository.putState(lastPullTsKey, nowTs.toString())
                repository.putState("INITIAL_PULL_COMPLETED_FOR_$remoteDeviceId", "1")

                _state.update {
                    it.copy(
                        isPulling = false,
                        pullProgressCount = 0,
                        pullProgressTotal = 0,
                        busy = null,
                        error = null,
                        lastMergeInfo = if (lastPullTs == 0L) "Successfully pulled all $pulledSoFar items from $devName (100%)!" else "Successfully pulled $pulledSoFar new item(s) from $devName!"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Railway pull failed for $remoteDeviceId", e)
                _state.update { it.copy(isPulling = false, busy = null, error = "Pull Error: ${e.localizedMessage}") }
            }
        } else {
            // Firebase Pull
            try {
                if (!isPeriodicPoll) {
                    _state.update {
                        it.copy(
                            isPulling = true,
                            pullProgressCount = 0,
                            pullProgressTotal = 0,
                            pullingDeviceName = devName,
                            busy = "Connecting to Firebase to pull $devName transactions…"
                        )
                    }
                }
                val snap = db.collection("devices/$remoteDeviceId/items").get().awaitTask()
                val totalDocs = snap.documents.size
                if (totalDocs == 0) {
                    if (!isPeriodicPoll) {
                        _state.update {
                            it.copy(
                                isPulling = false,
                                pullProgressCount = 0,
                                pullProgressTotal = 0,
                                busy = null,
                                lastMergeInfo = "No items to pull from $devName yet."
                            )
                        }
                    }
                    return@launch
                }

                _state.update {
                    it.copy(
                        isPulling = true,
                        pullProgressCount = 0,
                        pullProgressTotal = totalDocs,
                        pullingDeviceName = devName,
                        busy = "Pulling $totalDocs items from $devName…"
                    )
                }
                val records = ArrayList<SealedRecord>()
                var count = 0
                for (doc in snap.documents) {
                    val blob = doc.getBlob("p") ?: continue
                    records += SealedRecord(doc.id, doc.getString("d") ?: remoteDeviceId, blob.toBytes())
                    count++
                    if (count % 100 == 0 || count == totalDocs) {
                        _state.update { it.copy(pullProgressCount = count, pullProgressTotal = totalDocs) }
                    }
                }
                val changes = SyncRecords.open(records, key, deviceId) { id, e ->
                    Log.w(TAG, "Skipping undecryptable record $id: ${e.message}")
                }
                if (!changes.isEmpty) {
                    repository.applyRemoteChanges(changes)
                }
                repository.putState(lastPullTsKey, System.currentTimeMillis().toString())
                repository.putState("INITIAL_PULL_COMPLETED_FOR_$remoteDeviceId", "1")
                _state.update {
                    it.copy(
                        isPulling = false,
                        pullProgressCount = 0,
                        pullProgressTotal = 0,
                        busy = null,
                        error = null,
                        lastMergeInfo = "Successfully pulled $count items from $devName (100%)!"
                    )
                }
                refreshPullListeners()
            } catch (e: Exception) {
                Log.e(TAG, "Firebase pull failed for $remoteDeviceId", e)
                _state.update { it.copy(isPulling = false, busy = null, error = "Pull Error: ${e.localizedMessage}") }
            }
        }
    }

    private suspend fun refreshPullListeners() {
        val currentSynced = _state.value.activeSyncedDeviceIds.filter { it != deviceId }
        val dbUrl = repository.getState("RAILWAY_DATABASE_URL")
        val savedBackend = repository.getState("BACKEND_TYPE") ?: if (!dbUrl.isNullOrBlank()) "RAILWAY" else "FIREBASE"
        if (savedBackend == "RAILWAY") return

        // Remove listeners for devices no longer synced
        val toRemove = pullListeners.keys.filter { it !in currentSynced }
        for (rem in toRemove) {
            pullListeners.remove(rem)?.remove()
        }

        // Attach listeners for newly synced devices
        for (remId in currentSynced) {
            if (pullListeners.containsKey(remId)) continue
            if (repository.getState("INITIAL_PULL_COMPLETED_FOR_$remId") != "1") continue
            val query = db.collection("devices/$remId/items").orderBy("ts")
            val listener = query.addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) {
                    Log.w(TAG, "Pull listener error for $remId", err)
                    return@addSnapshotListener
                }
                if (snap != null) scope.launch(Dispatchers.IO) { applyRemoteDeviceSnapshot(remId, snap) }
            }
            pullListeners[remId] = listener
        }
    }

    private suspend fun applyRemoteDeviceSnapshot(remoteDeviceId: String, snap: QuerySnapshot) {
        val fromServer = !snap.metadata.isFromCache
        _state.update { it.copy(online = fromServer) }
        val key = groupKey ?: return

        val totalDocs = snap.documentChanges.size
        _state.update {
            it.copy(
                isPulling = true,
                pullProgressCount = 0,
                pullProgressTotal = totalDocs,
                pullingDeviceName = remoteDeviceId
            )
        }

        val records = ArrayList<SealedRecord>()
        var count = 0
        for (change in snap.documentChanges) {
            if (change.type == DocumentChange.Type.REMOVED) continue
            val doc = change.document
            if (doc.metadata.hasPendingWrites()) continue
            val blob = doc.getBlob("p") ?: continue
            records += SealedRecord(doc.id, doc.getString("d") ?: remoteDeviceId, blob.toBytes())
            count++
            if (count % 100 == 0 || count == totalDocs) {
                _state.update { it.copy(pullProgressCount = count, pullProgressTotal = totalDocs) }
            }
        }

        val changes = SyncRecords.open(records, key, deviceId) { id, e -> Log.w(TAG, "Skipping undecryptable record $id: ${e.message}") }
        if (!changes.isEmpty) {
            val summary = repository.applyRemoteChanges(changes)
            repository.putState("INITIAL_PULL_COMPLETED_FOR_$remoteDeviceId", "1")
            _state.update {
                it.copy(
                    isPulling = false,
                    lastReceivedAt = System.currentTimeMillis(),
                    lastMergeInfo = if (summary.transactionsAdded + summary.transactionsUpdated > 0)
                        "Pulled ${summary.transactionsAdded} new / ${summary.transactionsUpdated} updated transactions from $remoteDeviceId (100%)" else it.lastMergeInfo
                )
            }
        } else {
            repository.putState("INITIAL_PULL_COMPLETED_FOR_$remoteDeviceId", "1")
            _state.update { it.copy(isPulling = false) }
        }
    }

    fun triggerSyncAndUpload() = scope.launch(Dispatchers.IO) {
        try {
            _state.update { it.copy(busy = "Preparing data for cloud sync…", error = null) }
            // Enforce local database as the source of truth by re-queuing all local items for upload
            repository.setCloudSyncEnabled(false)
            repository.setCloudSyncEnabled(true)
            
            pushNow(rewriteRemote = true)
            val syncedRemotes = _state.value.activeSyncedDeviceIds.filter { it != deviceId }
            for (remId in syncedRemotes) {
                pullRemoteDeviceData(remId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "triggerSyncAndUpload failed", e)
            _state.update { it.copy(isUploading = false, isPulling = false, busy = null, error = "Sync failed: ${e.localizedMessage}") }
        }
    }

    /** Uploads local outbox in batches with Progress Bar and %. */
    private suspend fun pushNow(rewriteRemote: Boolean = false): Unit = pushMutex.withLock {
        if (deviceId.isBlank()) deviceId = repository.localDeviceId()
        val key = groupKey ?: return@withLock

        val dbUrl = repository.getState("RAILWAY_DATABASE_URL")
        val savedBackend = repository.getState("BACKEND_TYPE") ?: if (!dbUrl.isNullOrBlank()) "RAILWAY" else "FIREBASE"

        val totalPending = repository.getOutboxCount()
        if (totalPending == 0) {
            _state.update { it.copy(isUploading = false, uploadProgressCount = 0, uploadProgressTotal = 0, busy = null) }
            return@withLock
        }

        var uploadedSoFar = 0
        _state.update {
            it.copy(
                isUploading = true,
                uploadProgressCount = 0,
                uploadProgressTotal = totalPending,
                busy = "Uploading local database to $savedBackend…"
            )
        }

        if (savedBackend == "RAILWAY" && !dbUrl.isNullOrBlank()) {
            if (rewriteRemote) {
                try {
                    RailwayDatabaseEngine.clearDeviceItems(dbUrl, deviceId)
                } catch (e: Exception) {
                    Log.w(TAG, "Railway clear remote items failed", e)
                }
            }
            try {
                RailwayDatabaseEngine.withConnection(dbUrl) { conn ->
                    while (true) {
                        val batchItems = repository.peekOutbox(BATCH_SIZE)
                        if (batchItems.isEmpty()) break

                        val sealedRecords = SyncRecords.seal(batchItems, repository, key, deviceId)
                        RailwayDatabaseEngine.uploadBatchWithConnection(conn, deviceId, sealedRecords)
                        repository.ackOutbox(batchItems)
                        uploadedSoFar += batchItems.size
                        val currentRemaining = repository.getOutboxCount()
                        val calcTotal = maxOf(totalPending, uploadedSoFar + currentRemaining)
                        _state.update {
                            it.copy(
                                uploadProgressCount = uploadedSoFar,
                                uploadProgressTotal = calcTotal,
                                lastUploadedAt = System.currentTimeMillis()
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Railway upload failed", e)
                _state.update { it.copy(isUploading = false, busy = null, error = "Railway Upload: ${e.localizedMessage}") }
                scope.launch(Dispatchers.IO) { delay(RETRY_DELAY_MS); pushNow() }
                return@withLock
            }
        } else {
            // Firebase Upload
            val itemsCollection = db.collection("devices/$deviceId/items")
            if (rewriteRemote) {
                try {
                    val docs = itemsCollection.get().awaitTask()
                    if (!docs.isEmpty) {
                        val deleteBatch = db.batch()
                        for (d in docs.documents) {
                            deleteBatch.delete(d.reference)
                        }
                        deleteBatch.commit().awaitTask()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Firebase clear remote items failed", e)
                }
            }
            while (true) {
                val batchItems = repository.peekOutbox(BATCH_SIZE)
                if (batchItems.isEmpty()) break

                val batch = db.batch()
                for (record in SyncRecords.seal(batchItems, repository, key, deviceId)) {
                    batch.set(
                        itemsCollection.document(record.docId),
                        mapOf("d" to record.deviceId, "ts" to FieldValue.serverTimestamp(), "p" to Blob.fromBytes(record.payload))
                    )
                }
                try {
                    batch.commit().awaitTask()
                    repository.ackOutbox(batchItems)
                    uploadedSoFar += batchItems.size
                    val currentRemaining = repository.getOutboxCount()
                    val calcTotal = maxOf(totalPending, uploadedSoFar + currentRemaining)
                    _state.update {
                        it.copy(
                            uploadProgressCount = uploadedSoFar,
                            uploadProgressTotal = calcTotal,
                            lastUploadedAt = System.currentTimeMillis()
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Firebase Upload failed", e)
                    _state.update { it.copy(isUploading = false, busy = null, error = friendlyError(e)) }
                    scope.launch(Dispatchers.IO) { delay(RETRY_DELAY_MS); pushNow() }
                    break
                }
            }
        }

        _state.update {
            it.copy(
                isUploading = false,
                uploadProgressCount = uploadedSoFar,
                uploadProgressTotal = uploadedSoFar,
                busy = null,
                error = null,
                lastMergeInfo = if (uploadedSoFar > 0) "Successfully uploaded $uploadedSoFar items to database (100%)!" else it.lastMergeInfo
            )
        }
    }

    fun clearMessage() = _state.update { it.copy(error = null, lastMergeInfo = null) }

    fun unlink() = scope.launch {
        sessionMutex.withLock {
            discoveryListener?.remove()
            discoveryListener = null
            discoveryJob?.cancel()
            discoveryJob = null
            pullListeners.values.forEach { it.remove() }
            pullListeners.clear()
            pushJob?.cancel()
            pushJob = null
            repository.setCloudSyncEnabled(false)
            repository.putState("RAILWAY_DATABASE_URL", "")
            repository.putState("FIREBASE_PROJECT_ID", "")
            repository.putState("FIREBASE_API_KEY", "")
            _state.update { CloudSyncState(savedDatabaseUrl = null, savedProjectId = null, savedApiKey = null) }
        }
    }

    private suspend fun ensureSignedIn() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously().awaitTask()
        }
    }

    private fun friendlyError(e: Exception): String = when {
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Access denied by cloud rules. Check project configuration."
        e.message?.contains("UNAVAILABLE") == true || e.message?.contains("network") == true ->
            "Network connection unavailable. Retrying when online."
        else -> e.localizedMessage ?: "Cloud sync error ($e)"
    }

    /** Pushes pending outbox immediately to cloud in background. */
    fun pushPendingNow() {
        scope.launch(Dispatchers.IO) {
            val count = repository.getOutboxCount()
            if (count > 0 && !_state.value.isUploading) {
                pushNow(rewriteRemote = false)
            }
        }
    }

    companion object {
        private const val TAG = "CloudSyncManager"
        private const val BATCH_SIZE = 500
        private const val PUSH_DEBOUNCE_MS = 300L
        private const val RETRY_DELAY_MS = 10000L
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { res -> cont.resume(res) }
    addOnFailureListener { err -> cont.resumeWithException(err) }
}
