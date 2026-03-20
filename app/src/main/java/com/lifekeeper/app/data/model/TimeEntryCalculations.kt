package com.lifekeeper.app.data.model

/** Returns whether this entry should be treated as active at [nowMs]. */
fun TimeEntry.isActiveAt(nowMs: Long): Boolean =
    startEpochMs <= nowMs && (endEpochMs == null || endEpochMs > nowMs)

/**
 * Returns only the tracked milliseconds that have actually elapsed by [nowMs].
 * Future planned time is excluded.
 */
fun TimeEntry.elapsedMsAt(nowMs: Long): Long {
    val effectiveEndMs = minOf(endEpochMs ?: nowMs, nowMs)
    return (effectiveEndMs - startEpochMs).coerceAtLeast(0L)
}

/**
 * Returns the elapsed tracked milliseconds that overlap [startMs, endMs).
 * Future planned time is excluded by clamping to [nowMs].
 */
fun TimeEntry.elapsedOverlapMs(startMs: Long, endMs: Long, nowMs: Long): Long {
    val clippedStartMs = maxOf(startEpochMs, startMs)
    val clippedEndMs = minOf(minOf(endEpochMs ?: nowMs, nowMs), endMs)
    return (clippedEndMs - clippedStartMs).coerceAtLeast(0L)
}