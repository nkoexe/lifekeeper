package com.lifekeeper.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Refreshes the home-screen widget every 15 minutes (WorkManager minimum).
 * The widget is also refreshed immediately whenever the user switches modes
 * from inside the app or directly via the widget tap action.
 */
class ModeWidgetWorker(
    context: Context,
    params:  WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        updateWidgets(applicationContext)
        return Result.success()
    }
    companion object {
        private const val WORK_NAME = "lifekeeper_widget_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ModeWidgetWorker>(
                repeatInterval     = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
