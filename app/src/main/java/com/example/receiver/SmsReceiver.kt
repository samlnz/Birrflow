package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import com.example.BirrFlowApplication
import com.example.service.BirrFlowSyncService

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Ensure persistent service is alive
        BirrFlowSyncService.startService(context)

        val app = context.applicationContext as? BirrFlowApplication ?: BirrFlowApplication.instance

        // Acquire partial WakeLock to keep CPU active if phone screen is locked/asleep
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BirrFlow:SmsIncomingWakeLock"
        )?.apply {
            acquire(15_000L) // 15s max timeout safety
        }

        // Multipart SMS arrive as several PDUs of the same sender; join them into one body
        val sender = messages[0].displayOriginatingAddress ?: ""
        val fullBody = messages.joinToString(separator = "") { it.displayMessageBody ?: "" }
        val timestamp = messages[0].timestampMillis

        val pending = goAsync()
        app.smsSync.onLiveSms(sender, fullBody, timestamp) {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pending.finish()
            }
        }
    }
}
