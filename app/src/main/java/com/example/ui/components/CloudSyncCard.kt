package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.sync.CloudSyncState
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BlueFintech
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.RoseExpense
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudSyncCard(
    state: CloudSyncState,
    phonesInGroup: Int,
    onCreateGroup: () -> Unit = {},
    onJoinGroup: (String) -> Unit = {},
    onSyncDevice: (String) -> Unit = {},
    onUnsyncDevice: (String) -> Unit = {},
    onSyncAllDevices: () -> Unit = {},
    onTriggerUpload: () -> Unit = {},
    onUnlink: () -> Unit = {},
    onClearMessage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showUnlinkConfirm by remember { mutableStateOf(false) }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Card(
        modifier = modifier.fillMaxWidth().testTag("cloud_sync_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "AUTOMATIC MULTI-PHONE CLOUD MESH",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.1.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (!state.savedDatabaseUrl.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EmeraldPrimary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Railway DB: ${state.savedDatabaseUrl.take(28)}…",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                } else if (!state.savedProjectId.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EmeraldPrimary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Firebase: ${state.savedProjectId}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Text(
                "All devices configured with the same Railway Database URL or Firebase Project ID automatically stream their transactions. Tap 'Sync & Upload' to push local data, or select a phone below to pull data.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.needsFirebaseConfig) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AmberAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Cloud Database Connection Required", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AmberAccent)
                        Text(
                            "To link multiple phones over the internet, set your Railway Public Database URL or Firebase Project ID using the '+' button above.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Live status row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, color, label) = when {
                        state.online && state.pendingUploads == 0 -> Triple(Icons.Default.CloudDone, EmeraldPrimary, "Live — connected & up to date")
                        state.online -> Triple(Icons.Default.CloudSync, EmeraldPrimary, "Live — uploading ${state.pendingUploads} change(s)")
                        else -> Triple(Icons.Default.CloudOff, AmberAccent, "Offline — changes will sync when online")
                    }
                    Surface(shape = CircleShape, color = color.copy(alpha = 0.15f), modifier = Modifier.size(36.dp)) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(8.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.testTag("cloud_status"))
                        Text(
                            "$phonesInGroup phone(s) in Room database" +
                                (state.lastReceivedAt?.let { " · received ${timeFmt.format(Date(it))}" } ?: "") +
                                (state.lastUploadedAt?.let { " · sent ${timeFmt.format(Date(it))}" } ?: ""),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Upload & Pull Progress Section
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Upload Local Data to Cloud Database", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Syncs your local SMS bank alerts & balances to Railway / Firebase so other phones can pull them.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = onTriggerUpload,
                                enabled = !state.isUploading && !state.isPulling,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp).testTag("trigger_upload_button")
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Sync & Upload", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Upload Progress Bar
                        if (state.isUploading || state.uploadProgressTotal > 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (state.isUploading) "Uploading local database…" else "Upload complete!",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (state.isUploading) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${state.uploadPercent}% (${state.uploadProgressCount} / ${state.uploadProgressTotal} items)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldPrimary,
                                        modifier = Modifier.testTag("upload_progress_percent")
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (state.uploadPercent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = EmeraldPrimary,
                                    trackColor = EmeraldPrimary.copy(alpha = 0.2f)
                                )
                            }
                        }

                        // Requirement 2: Pull Progress Bar & %
                        if (state.isPulling || state.pullProgressTotal > 0) {
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (state.isPulling) "Pulling transactions from ${state.pullingDeviceName ?: "remote device"}…" else "Pull complete!",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (state.isPulling) BlueFintech else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${state.pullPercent}% (${state.pullProgressCount} / ${state.pullProgressTotal} items)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BlueFintech,
                                        modifier = Modifier.testTag("pull_progress_percent")
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (state.pullPercent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = BlueFintech,
                                    trackColor = BlueFintech.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                }

                // Discovered connected devices list (remote peer phones only to avoid duplicating current device)
                val remoteDevices = state.discoveredDevices.filter { !it.isCurrentDevice }.distinctBy { it.deviceId }
                if (remoteDevices.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("DISCOVERED PEER PHONES (${remoteDevices.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            val hasUnsyncedRemotes = remoteDevices.any { !it.isSynced }
                            if (hasUnsyncedRemotes) {
                                TextButton(onClick = onSyncAllDevices) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(12.dp), tint = EmeraldPrimary)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Sync All Phones", fontSize = 11.sp, color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        remoteDevices.forEach { dev ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (dev.isCurrentDevice) EmeraldPrimary.copy(alpha = 0.3f)
                                    else if (dev.isSynced) BlueFintech.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("discovered_device_${dev.deviceId}")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = true)
                                    ) {
                                        Icon(
                                            Icons.Default.PhoneAndroid,
                                            contentDescription = null,
                                            tint = if (dev.isCurrentDevice) EmeraldPrimary else if (dev.isSynced) BlueFintech else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f, fill = true)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    dev.friendlyName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (dev.isCurrentDevice) {
                                                    Surface(shape = RoundedCornerShape(4.dp), color = EmeraldPrimary.copy(alpha = 0.2f)) {
                                                        Text("THIS DEVICE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                    }
                                                } else if (dev.isSynced) {
                                                    val isCurrentlyPullingThis = state.isPulling && (state.pullingDeviceName?.contains(dev.friendlyName) == true || state.pullingDeviceName?.contains(dev.deviceId) == true)
                                                    if (isCurrentlyPullingThis) {
                                                        Surface(shape = RoundedCornerShape(4.dp), color = BlueFintech.copy(alpha = 0.2f)) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                            ) {
                                                                CircularProgressIndicator(modifier = Modifier.size(8.dp), strokeWidth = 1.5.dp, color = BlueFintech)
                                                                Spacer(Modifier.width(3.dp))
                                                                Text("PULLING ${state.pullPercent}%", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = BlueFintech)
                                                            }
                                                        }
                                                    } else {
                                                        Surface(shape = RoundedCornerShape(4.dp), color = EmeraldPrimary.copy(alpha = 0.15f)) {
                                                            Text("LINKED", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                        }
                                                    }
                                                }
                                            }
                                            Text(
                                                "${dev.deviceId} • ${dev.modelName}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    if (!dev.isCurrentDevice) {
                                        Spacer(Modifier.width(8.dp))
                                        if (dev.isSynced) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Button(
                                                    onClick = { onSyncDevice(dev.deviceId) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = BlueFintech),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(32.dp).testTag("pull_now_btn_${dev.deviceId}")
                                                ) {
                                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(Modifier.width(3.dp))
                                                    Text("Pull Now", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                }
                                                OutlinedButton(
                                                    onClick = { onUnsyncDevice(dev.deviceId) },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseExpense),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(32.dp).testTag("unsync_btn_${dev.deviceId}")
                                                ) {
                                                    Text("Unlink", fontSize = 10.sp, maxLines = 1)
                                                }
                                            }
                                        } else {
                                            Button(
                                                onClick = { onSyncDevice(dev.deviceId) },
                                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(32.dp).testTag("sync_btn_${dev.deviceId}")
                                            ) {
                                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Sync This Phone", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            state.busy?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(it, fontSize = 12.sp)
                }
            }

            (state.error ?: state.lastMergeInfo)?.let { msg ->
                val color = if (state.error != null) RoseExpense else EmeraldPrimary
                Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, fontSize = 12.sp, color = color, modifier = Modifier.weight(1f).testTag("cloud_message"))
                        TextButton(onClick = onClearMessage) { Text("OK", fontSize = 12.sp) }
                    }
                }
            }
        }
    }

    if (showUnlinkConfirm) {
        AlertDialog(
            onDismissRequest = { showUnlinkConfirm = false },
            title = { Text("Disconnect cloud sync?") },
            text = { Text("This phone stops sending and receiving updates. Data already on this phone stays.") },
            confirmButton = {
                Button(
                    onClick = { onUnlink(); showUnlinkConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseExpense)
                ) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { showUnlinkConfirm = false }) { Text("Cancel") } }
        )
    }
}
