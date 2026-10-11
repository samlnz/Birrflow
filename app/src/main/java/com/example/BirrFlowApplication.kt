package com.example

import android.app.Application
import com.example.data.database.AppDatabase
import com.example.data.repository.FinancialRepository
import com.example.service.SmsSyncCoordinator
import com.example.sync.CloudSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BirrFlowApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { FinancialRepository(database) }
    val smsSync by lazy { SmsSyncCoordinator(this, repository, applicationScope) }
    val cloudSync by lazy { CloudSyncManager(this, repository, applicationScope) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Seed defaults, assign this install's device id, re-parse stored SMS after a parser upgrade
        smsSync.start()
        if (smsSync.hasSmsPermission()) smsSync.onSmsPermissionGranted()
        // Resume the real-time internet sync with the other phones (if this phone is linked)
        cloudSync.start()
        // Start persistent background service so incoming bank transactions are processed even when app is closed
        com.example.service.BirrFlowSyncService.startService(this)
    }

    companion object {
        lateinit var instance: BirrFlowApplication
            private set
    }
}
