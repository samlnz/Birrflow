package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.BirrFlowApplication
import com.example.MainActivity
import com.example.R
import com.example.ai.AdvisorIntelligence
import com.example.ai.AdvisorPersistenceService
import com.example.ai.BriefingStore
import com.example.ai.DecisionTracker
import com.example.ai.FinancialSnapshotService
import com.example.ai.InvestmentCatalog
import com.example.ai.MarketDataService
import com.example.ai.NetWorthCalculator
import com.example.ai.NetWorthPoint
import com.example.ai.UserProfileService
import com.example.ai.WeeklyBriefing
import com.example.ai.WeeklyBriefingBuilder
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Once a week (checked every few hours while the background service runs) BirrFlow measures the
 * capital-plan outcomes, records net worth and posts a short money briefing notification.
 * Everything is computed on the phone; no AI call is made in the background.
 */
object WeeklyBriefingScheduler {

    private const val CHANNEL_ID = "birrflow_weekly_briefing"
    private const val NOTIFICATION_ID = 60001
    private const val KEY_LAST_RUN = "WEEKLY_BRIEFING_LAST_RUN"
    private const val WEEK_MS = 7L * 24 * 3600 * 1000
    private const val CHECK_EVERY_MS = 6L * 3600 * 1000

    private val loopStarted = AtomicBoolean(false)

    fun startLoop(app: BirrFlowApplication) {
        if (!loopStarted.compareAndSet(false, true)) return
        app.applicationScope.launch {
            while (isActive) {
                try {
                    runIfDue(app, force = false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(CHECK_EVERY_MS)
            }
        }
    }

    /** Builds and posts the briefing when a week has passed (or right away with [force]). */
    suspend fun runIfDue(app: BirrFlowApplication, force: Boolean): WeeklyBriefing? {
        val repository = app.repository
        val now = System.currentTimeMillis()
        val lastRun = repository.getState(KEY_LAST_RUN)?.toLongOrNull() ?: 0L
        if (!force && now - lastRun < WEEK_MS) return null

        val txs = repository.getAllTransactionsOnce()
        // Nothing to say before the first transactions arrive
        if (txs.isEmpty() && !force) return null
        val categories = repository.getActiveCategoriesOnce()
        val incomeSources = repository.getAllIncomeSourcesOnce()
        val goals = repository.getSavingsGoalsOnce()
        val wealth = repository.getWealthItemsOnce()
        val market = MarketDataService.load(repository)
        val profile = UserProfileService.load(repository)

        val manual = repository.getActiveManualSourcesOnce()
        val smsBalance = MainViewModel.latestBalances(txs).values.sum()
        val manualBalance = manual.sumOf { it.balance }

        // Measure tracked plan milestones first so the briefing reports fresh outcomes
        val savedPlan = AdvisorPersistenceService.loadCapitalPlan(repository, smsBalance + manualBalance)
        val plan = DecisionTracker.evaluate(savedPlan, txs, categories, now)
        AdvisorPersistenceService.saveCapitalPlan(repository, plan)

        val base = FinancialSnapshotService.computeSnapshot(
            bankBalance = smsBalance,
            manualBalance = manualBalance,
            transactions = txs,
            categorySpends = emptyList(),
            nowMillis = now,
            emergencyMonths = profile.emergencyMonths
        )
        val deep = AdvisorIntelligence.analyze(txs, categories, incomeSources, goals, plan, base.totalRecordedBalance, now)
        val prefs = profile.rankingPreferences(
            hasDebt = wealth.any { it.kind.isLiability },
            debtRatePct = NetWorthCalculator.highestDebtRatePct(wealth)
        )
        val ranked = InvestmentCatalog.rank(market, base.availableLiquidCash, prefs)
        val netWorth = NetWorthCalculator.summarize(smsBalance + manualBalance, wealth, market).netWorth

        val weekStart = now - WEEK_MS
        val lastWeek = txs.filter { it.createdTimestamp in weekStart..now }
        val previous = BriefingStore.history(repository).lastOrNull()
        val briefing = WeeklyBriefingBuilder.build(
            nowMillis = now,
            netWorth = netWorth,
            previous = previous,
            weekInflow = lastWeek.sumOf { AdvisorIntelligence.flowOf(it).first },
            weekOutflow = lastWeek.sumOf { AdvisorIntelligence.flowOf(it).second },
            deep = deep,
            ranked = ranked,
            market = market
        )

        BriefingStore.saveBriefing(repository, briefing)
        BriefingStore.appendHistory(repository, NetWorthPoint(now, netWorth))
        repository.putState(KEY_LAST_RUN, now.toString())
        notify(app, briefing)
        return briefing
    }

    private fun notify(context: Context, briefing: WeeklyBriefing) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Weekly money briefing", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Once a week: net worth change, alerts and the best place for idle cash"
                }
            )
        }
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_ADVISOR, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(briefing.title)
            .setContentText(briefing.lines.firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(briefing.lines.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted; the briefing is still shown in the AI Advisor
        }
    }

    const val EXTRA_OPEN_ADVISOR = "extra_open_advisor"
}
