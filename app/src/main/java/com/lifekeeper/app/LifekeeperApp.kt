package com.lifekeeper.app

import android.app.Application
import com.lifekeeper.app.data.db.LifekeeperDatabase
import com.lifekeeper.app.data.preferences.UserPreferencesRepository
import com.lifekeeper.app.data.repository.ModeRepository
import com.lifekeeper.app.data.repository.TimeRepository
import com.lifekeeper.app.data.timeline.TimelineScheduler
import com.lifekeeper.app.widget.ModeWidgetWorker
import com.lifekeeper.app.widget.updateWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LifekeeperApp : Application() {

    val database by lazy { LifekeeperDatabase.getInstance(this) }

    val modeRepository by lazy { ModeRepository(database.modeDao()) }

    val userPreferencesRepository by lazy { UserPreferencesRepository(this) }

    val timeRepository by lazy { TimeRepository(database, userPreferencesRepository) }

    val timelineScheduler by lazy { TimelineScheduler(this, timeRepository) }

    /**
     * Long-lived scope tied to the [Application] lifetime for work that must
     * outlive any individual component (activity, action callback, etc.).
     * Runs on [Dispatchers.Main] because Glance's [GlanceAppWidget.update] is
     * a lightweight suspend function that does its own internal dispatching.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Re-renders both home-screen widgets on [appScope] so the update is
     * never tied to — or cancelled by — the caller's own coroutine lifetime.
     */
    fun scheduleWidgetUpdate() {
        appScope.launch { updateWidgets(this@LifekeeperApp) }
    }

    override fun onCreate() {
        super.onCreate()
        ModeWidgetWorker.schedule(this)
        timelineScheduler.start(appScope)
    }
}
