package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.BirrFlowApplication
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.launch

class BirrFlowSyncService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                }
            } catch (e: Exception) {
                // Fallback for devices with strict manifest foreground service type rules
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Ensure application background services & receivers are active
        val app = applicationContext as? BirrFlowApplication ?: BirrFlowApplication.instance
        app.smsSync.start()
        app.cloudSync.start()

        // Restore ongoing notifications for any pending uncategorized debit transactions
        app.applicationScope.launch {
            CategorizeNotificationHelper.syncUncategorizedDebitNotifications(this@BirrFlowSyncService, app.repository)
        }

        // Weekly money briefing + automatic plan outcome checks (computed on the phone)
        WeeklyBriefingScheduler.startLoop(app)

        // START_STICKY ensures the OS automatically restarts this service if killed by low memory
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Android 15+ allows a dataSync foreground service about 6 hours a day, then calls this and
     * crashes the app if the service is still running a few seconds later. SMS still arrive through
     * the manifest receiver, so stopping here only ends the persistent notification until next launch.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BirrFlow Background Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps BirrFlow background SMS detection and multi-phone sync active when closed."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BirrFlow Active Background Sync")
            .setContentText("Monitoring bank SMS & syncing across devices")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "birrflow_background_sync_channel"
        const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            try {
                val intent = Intent(context, BirrFlowSyncService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
