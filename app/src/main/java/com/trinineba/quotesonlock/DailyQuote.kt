package com.trinineba.quotesonlock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * "Today's quote": one gentle notification a day at a time you choose, separate from the lock-screen
 * card. It picks from the quote types you have switched on and doesn't change the card's rotation.
 */
object DailyQuote {
    private const val WORK_NAME = "daily-quote"
    private const val CHANNEL_ID = "daily"
    private const val NOTIFICATION_ID = 4311

    /** Schedules the next one (or cancels when switched off). Safe to call often. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val prefs = QuotePrefs(app)
        val work = WorkManager.getInstance(app)
        if (!prefs.dailyEnabled) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = OneTimeWorkRequestBuilder<DailyQuoteWorker>()
            .setInitialDelay(millisUntil(prefs.dailyHour, prefs.dailyMinute), TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Milliseconds from now until the next time the clock shows [hour]:[minute]. */
    fun millisUntil(hour: Int, minute: Int, now: Calendar = Calendar.getInstance()): Long {
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }

    fun show(context: Context) {
        val app = context.applicationContext
        val prefs = QuotePrefs(app)
        val repo = QuoteRepository(app, prefs)
        val currentText = prefs.currentText
        val quote = repo.pool().filter { it.text != currentText }.randomOrNull() ?: return
        ensureChannel(app)
        val open = PendingIntent.getActivity(
            app, 7, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = quote.fullText()
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quote)
            .setContentTitle(quote.title ?: "Today's quote")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification) }
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Today's quote", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "One quote a day, at the time you choose"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

class DailyQuoteWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        DailyQuote.show(applicationContext)
        // The next one, tomorrow at the same time.
        DailyQuote.schedule(applicationContext)
        return Result.success()
    }
}
