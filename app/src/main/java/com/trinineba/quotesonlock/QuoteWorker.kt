package com.trinineba.quotesonlock

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class QuoteWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val prefs = QuotePrefs(applicationContext)
        if (!prefs.enabled) return Result.success()
        QuoteRepository(applicationContext, prefs).advance()?.let {
            QuoteNotifier.show(applicationContext, it)
        }
        return Result.success()
    }
}

object QuoteScheduler {
    private const val WORK_NAME = "quote-rotation"

    /** (Re)schedules rotation, replacing any existing schedule. */
    fun schedule(context: Context, minutes: Long) =
        enqueue(context, minutes, ExistingPeriodicWorkPolicy.UPDATE)

    /** Makes sure rotation is scheduled without disturbing an existing schedule. */
    fun ensureScheduled(context: Context, minutes: Long) =
        enqueue(context, minutes, ExistingPeriodicWorkPolicy.KEEP)

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }

    private fun enqueue(context: Context, minutes: Long, policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<QuoteWorker>(minutes, TimeUnit.MINUTES)
            .setInitialDelay(minutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }
}
