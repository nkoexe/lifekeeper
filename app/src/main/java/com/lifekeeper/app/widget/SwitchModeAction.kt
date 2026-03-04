package com.lifekeeper.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.lifekeeper.app.LifekeeperApp

/**
 * Action dispatched when the user taps a mode row in either widget.
 * Switches the active mode; the widgets recompose automatically via their
 * collectAsState observers.
 */
class SwitchModeAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val modeId = parameters[ModeIdKey] ?: return
        val app = context.applicationContext as LifekeeperApp
        app.timeRepository.switchMode(modeId)
        // scheduleWidgetUpdate() restarts any widget session that has expired
        // after the ~45 s idle window; live sessions recompose via Flow.
        app.scheduleWidgetUpdate()
    }

    companion object {
        val ModeIdKey = ActionParameters.Key<Long>("modeId")
    }
}
