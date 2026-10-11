package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.ai.CategorySuggestion
import com.example.data.entity.CategoryEntity
import com.example.data.entity.TransactionEntity
import com.example.data.repository.FinancialRepository
import com.example.receiver.CategorizeActionReceiver
import com.example.ui.components.formatCurrency

object CategorizeNotificationHelper {

    private const val CHANNEL_ID = "birrflow_categorize_channel"
    private const val NOTIFICATION_ID_BASE = 50000
    private const val CHIPS_PER_ROW = 3
    // Expanded custom notifications are capped at 256dp: 5 rows of chips fit under the details
    private const val MAX_CATEGORY_CHIPS = 15

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Debit Expense Categorization",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Quickly categorize new debit expenses directly from notification actions"
                enableLights(true)
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    /**
     * Shows an ONGOING high-priority notification for a debit transaction. Android limits standard
     * notification actions to three buttons, so the expanded view is a custom layout with a chip for
     * EVERY manual expense category; tapping a chip files the debit without opening the app.
     * The notification stays persistent (ongoing) until categorized by the user.
     */
    fun showCategorizeNotification(
        context: Context,
        transaction: TransactionEntity,
        categories: List<CategoryEntity>,
        sourceName: String,
        phoneName: String,
        suggestions: List<CategorySuggestion> = emptyList()
    ) {
        createNotificationChannel(context)
        // Only the user's own expense types; the system "Recovered from balance" category is not a manual choice.
        // Learned suggestions come first so the likely category is one tap away.
        val suggestedOrder = suggestions.map { it.categoryId }
        val topSuggestion = suggestions.firstOrNull()?.takeIf { it.confidence >= 0.25 }
        val manualCategories = categories
            .filter { it.categoryId != FinancialRepository.RECONCILED_CATEGORY.categoryId }
            .sortedWith(
                compareBy<CategoryEntity> { suggestedOrder.indexOf(it.categoryId).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                    .thenBy { it.name.lowercase() }
            )

        val targetLabel = transaction.merchant ?: transaction.recipient ?: sourceName
        val title = "Categorize Debit: -${formatCurrency(transaction.amount)}"
        val contentText = "$sourceName on $phoneName · $targetLabel"

        // Content intent: open the app directly with this transaction selected
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(CategorizeActionReceiver.EXTRA_TX_ID, transaction.transactionId)
            putExtra(CategorizeActionReceiver.EXTRA_OPEN_CATEGORIZE_POPUP, true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            transaction.transactionId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = notificationIdFor(transaction.transactionId)

        fun categorizeIntent(catId: String, catName: String): PendingIntent {
            val actionIntent = Intent(context, CategorizeActionReceiver::class.java).apply {
                action = CategorizeActionReceiver.ACTION_QUICK_CATEGORIZE
                putExtra(CategorizeActionReceiver.EXTRA_TX_ID, transaction.transactionId)
                putExtra(CategorizeActionReceiver.EXTRA_CATEGORY_ID, catId)
                putExtra(CategorizeActionReceiver.EXTRA_CATEGORY_NAME, catName)
                putExtra(CategorizeActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                topSuggestion?.let { putExtra(CategorizeActionReceiver.EXTRA_SUGGESTED_CATEGORY_ID, it.categoryId) }
            }
            return PendingIntent.getBroadcast(
                context,
                (transaction.transactionId + catId).hashCode(),
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val bigText = buildString {
            append(contentText)
            if (manualCategories.isNotEmpty()) {
                topSuggestion?.let { append("\nSuggested: ${it.categoryName} (${it.reason})") }
                append("\n\nExpand to tap one of your ${manualCategories.size} categories.")
            } else {
                append("\n\n(No categories created yet — tap to open BirrFlow and create categories)")
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Notification stays until categorized, even if other notifications are cleaned!
            .setOngoing(true)
            .setAutoCancel(false)
            // Re-posting (new category, app restart) must not buzz again for the same debit
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)

        if (manualCategories.isEmpty()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
        } else {
            val visible = if (manualCategories.size > MAX_CATEGORY_CHIPS) manualCategories.take(MAX_CATEGORY_CHIPS - 1) else manualCategories
            val hiddenCount = manualCategories.size - visible.size
            val chips = visible.map {
                val label = if (it.categoryId == topSuggestion?.categoryId) "★ ${it.name}" else it.name
                ChipSpec(label, parseColor(it.colorHex), categorizeIntent(it.categoryId, it.name))
            } +
                (if (hiddenCount > 0) listOf(ChipSpec("+$hiddenCount more…", null, contentPendingIntent)) else emptyList())

            val expanded = RemoteViews(context.packageName, R.layout.notification_categorize_expanded).apply {
                setTextViewText(R.id.notif_details, contentText)
                setTextViewText(
                    R.id.notif_hint,
                    topSuggestion?.let { "★ = learned suggestion (${(it.confidence * 100).toInt()}% sure) · tap to file:" }
                        ?: "Tap a category to file this debit:"
                )
                removeAllViews(R.id.notif_category_rows)
                chips.chunked(CHIPS_PER_ROW).forEach { rowChips ->
                    addView(R.id.notif_category_rows, buildChipRow(context, rowChips))
                }
            }
            // The heads-up popup only has room for one row: the first categories, then "more" opens the app
            val headsUpChips = if (chips.size > CHIPS_PER_ROW) {
                chips.take(CHIPS_PER_ROW - 1) + ChipSpec("More ▾", null, contentPendingIntent)
            } else {
                chips
            }
            val headsUp = RemoteViews(context.packageName, R.layout.notification_categorize_expanded).apply {
                setTextViewText(R.id.notif_details, contentText)
                setViewVisibility(R.id.notif_hint, View.GONE)
                removeAllViews(R.id.notif_category_rows)
                addView(R.id.notif_category_rows, buildChipRow(context, headsUpChips))
            }

            builder
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomBigContentView(expanded)
                .setCustomHeadsUpContentView(headsUp)
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(notificationId, builder.build())
    }

    private data class ChipSpec(val label: String, val color: Int?, val intent: PendingIntent)

    /** A row of equally wide chips; missing slots are padded with invisible chips to keep widths equal. */
    private fun buildChipRow(context: Context, chips: List<ChipSpec>): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.notification_category_row)
        for (i in 0 until CHIPS_PER_ROW) {
            val chip = RemoteViews(context.packageName, R.layout.notification_category_chip)
            val spec = chips.getOrNull(i)
            if (spec == null) {
                chip.setViewVisibility(R.id.notif_chip, View.INVISIBLE)
            } else {
                chip.setTextViewText(R.id.notif_chip, spec.label)
                spec.color?.let { chip.setTextColor(R.id.notif_chip, it) }
                chip.setOnClickPendingIntent(R.id.notif_chip, spec.intent)
            }
            row.addView(R.id.notif_chip_row, chip)
        }
        return row
    }

    private fun parseColor(hex: String): Int? = try {
        AndroidColor.parseColor(hex)
    } catch (_: Exception) {
        null
    }

    private fun notificationIdFor(transactionId: String): Int =
        NOTIFICATION_ID_BASE + kotlin.math.abs(transactionId.hashCode() % 10000)

    fun dismissNotification(context: Context, notificationId: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(notificationId)
    }

    fun dismissNotificationForTransaction(context: Context, transactionId: String) {
        dismissNotification(context, notificationIdFor(transactionId))
    }

    /**
     * Re-posts ongoing notifications for any pending uncategorized debit transactions,
     * ensuring notifications stay in the notification shade until categorized.
     */
    suspend fun syncUncategorizedDebitNotifications(context: Context, repository: FinancialRepository) {
        try {
            val uncategorized = repository.getUncategorizedExpensesOnce(limit = 10)
            if (uncategorized.isEmpty()) return

            val categories = repository.getActiveCategoriesOnce()
            val sources = repository.getActiveSources().associateBy { it.sourceId }
            val phones = repository.getAllPhonesOnce().associateBy { it.phoneId }
            val learner = repository.categoryLearner()

            for (tx in uncategorized) {
                val srcName = sources[tx.sourceId]?.name ?: tx.sourceId
                val phoneName = phones[tx.phoneId]?.friendlyName ?: tx.phoneId
                showCategorizeNotification(context, tx, categories, srcName, phoneName, learner.suggest(tx))
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
