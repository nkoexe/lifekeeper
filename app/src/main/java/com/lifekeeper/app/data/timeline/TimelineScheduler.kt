package com.lifekeeper.app.data.timeline

import com.lifekeeper.app.LifekeeperApp
import com.lifekeeper.app.data.repository.TimeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Application-scoped scheduler for planned transitions and scheduled-active expiry.
 *
 * This must not live in a screen ViewModel because planned mode changes are a
 * domain concern that should continue working even when the calendar UI is not open.
 */
class TimelineScheduler(
    private val app: LifekeeperApp,
    private val timeRepository: TimeRepository,
) {
    private var started = false

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        scope.launch {
            var lastNowMs = System.currentTimeMillis()
            while (true) {
                val now = System.currentTimeMillis()
                var changed = false

                val justDue = timeRepository.getPlannedEntriesSnapshot(lastNowMs)
                    .filter { it.startEpochMs <= now }
                for (entry in justDue) {
                    timeRepository.activatePlannedEntry(entry)
                    changed = true
                }

                if (timeRepository.getEffectiveActiveEntry(now) == null) {
                    timeRepository.extendExpiredEntry(
                        afterMs = lastNowMs,
                        atOrBeforeMs = now,
                    )
                    changed = true
                }

                if (changed) app.scheduleWidgetUpdate()

                lastNowMs = now

                val upcoming = timeRepository.getPlannedEntriesSnapshot(now).firstOrNull()
                val effectiveActive = timeRepository.getEffectiveActiveEntry(now)
                val futureOpen = timeRepository.getFutureOpenEntry(now)

                val msUntilPlanned = upcoming?.let { it.startEpochMs - now } ?: Long.MAX_VALUE
                val msUntilExpiry = effectiveActive?.endEpochMs?.let { it - now } ?: Long.MAX_VALUE
                val msUntilDeferred = futureOpen?.let { it.startEpochMs - now } ?: Long.MAX_VALUE

                val msUntilNext = minOf(msUntilPlanned, msUntilExpiry, msUntilDeferred)
                val tickMs = if (msUntilNext <= 2 * 60_000L) 1_000L else 30_000L
                delay(tickMs)
            }
        }
    }
}