package com.lifekeeper.app.data.preferences

data class UserPreferences(
    val theme: ThemePreference = ThemePreference.SYSTEM,

    /**
     * Entries shorter than this are treated as accidental taps and automatically
     * removed when the next mode switch occurs (see [TimeRepository.switchMode]).
     *
     * Only configurable from the UI in debug builds (Settings → Tracking → Minimum session).
     * In release builds this always reads as the default (60 s).
     *
     * Available options: 15, 30, 60, 120, 300 seconds.
     */
    val minSessionDurationSeconds: Int = 60,

    // TODO: Day boundary (hour of day when a new "day" begins, e.g. 4 for 4 am).
    // Intended for users who work night shifts or keep late-night schedules and want
    // entries between midnight and their chosen hour to belong to the previous day.
    // This will need to be plumbed through:
    //   - TimeRepository.dayBoundaryMs() / todayMidnightMs()
    //   - Any Stats aggregation that buckets by day
    //   - The future continuous-timeline view (planned feature)
    // val dayBoundaryHour: Int = 0,
)
