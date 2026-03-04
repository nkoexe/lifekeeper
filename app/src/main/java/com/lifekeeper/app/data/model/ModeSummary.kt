package com.lifekeeper.app.data.model

/** Computed — not a Room entity. Represents aggregated time per mode for a time window. */
data class ModeSummary(
    val modeId: Long,
    val modeName: String,
    val colorHex: String,
    val totalMs: Long          // total milliseconds in this mode
)
