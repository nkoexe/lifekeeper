package com.lifekeeper.app.data.model

/**
 * Pre-computed statistics for a single calendar day.
 *
 * @param dayStartMs  Local-midnight epoch-ms for the day this record represents.
 * @param durationsMs Map of modeId → total milliseconds spent in that mode during the day.
 *                    Only modes with at least 1 ms of tracked time appear here.
 */
data class DailySummary(
    val dayStartMs: Long,
    val durationsMs: Map<Long, Long>,
) {
    /** Total tracked milliseconds across all modes for this day. */
    val totalMs: Long get() = durationsMs.values.sum()
}
