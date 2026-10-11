package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.BirrFlowApplication
import com.example.service.CategorizeNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CategorizeActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_QUICK_CATEGORIZE) return

        val txId = intent.getStringExtra(EXTRA_TX_ID) ?: return
        val categoryId = intent.getStringExtra(EXTRA_CATEGORY_ID) ?: return
        val categoryName = intent.getStringExtra(EXTRA_CATEGORY_NAME) ?: categoryId
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val suggestedId = intent.getStringExtra(EXTRA_SUGGESTED_CATEGORY_ID)

        val app = context.applicationContext as? BirrFlowApplication ?: BirrFlowApplication.instance
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.repository.updateTransactionCategory(txId, categoryId, null)
                // Feeds the learner's accuracy score shown in the app
                if (suggestedId != null) app.repository.recordSuggestionOutcome(hit = suggestedId == categoryId)
                if (notificationId != -1) {
                    CategorizeNotificationHelper.dismissNotification(context, notificationId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pending.finish()
            }
        }

        Toast.makeText(context, "Marked as $categoryName", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ACTION_QUICK_CATEGORIZE = "com.example.action.QUICK_CATEGORIZE"
        const val EXTRA_TX_ID = "extra_tx_id"
        const val EXTRA_CATEGORY_ID = "extra_category_id"
        const val EXTRA_CATEGORY_NAME = "extra_category_name"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_OPEN_CATEGORIZE_POPUP = "extra_open_categorize_popup"
        const val EXTRA_SUGGESTED_CATEGORY_ID = "extra_suggested_category_id"
    }
}
