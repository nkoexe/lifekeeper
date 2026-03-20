package com.lifekeeper.app.widget

/**
 * Shared palette, color helpers, duration formatting, data-aggregation
 * utilities, and Glance composables used by every home-screen widget.
 *
 * All declarations are `internal` — visible across the module but not
 * exported as public API.
 */

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lifekeeper.app.data.model.TimeEntry
import com.lifekeeper.app.data.model.elapsedMsAt
import com.lifekeeper.app.data.model.isActiveAt

// ── Baseline M3 surface tones (pre-blended, fully opaque) ────────────────────
//
// Keeping colors opaque avoids RemoteViews transparency rendering bugs on
// older launchers while still adapting cleanly to light / dark system themes.

internal val LIGHT_SURFACE        = Color(red = 0.996f, green = 0.969f, blue = 1.000f)
internal val DARK_SURFACE         = Color(red = 0.078f, green = 0.071f, blue = 0.094f)
private  val LIGHT_ON_SURFACE     = Color(red = 0.114f, green = 0.106f, blue = 0.125f)
private  val DARK_ON_SURFACE      = Color(red = 0.902f, green = 0.882f, blue = 0.898f)
private  val LIGHT_ON_SURFACE_VAR = Color(red = 0.286f, green = 0.271f, blue = 0.310f)
private  val DARK_ON_SURFACE_VAR  = Color(red = 0.792f, green = 0.769f, blue = 0.816f)
// 54 % onSurface over surface  — avoids any semi-transparent text painting
private  val LIGHT_TEXT_FADED     = Color(red = 0.514f, green = 0.502f, blue = 0.525f)
private  val DARK_TEXT_FADED      = Color(red = 0.522f, green = 0.508f, blue = 0.526f)

// ── Shared ColorProvider constants ────────────────────────────────────────────

internal val cpWidgetBg   = adaptiveColor(LIGHT_SURFACE,        DARK_SURFACE)
internal val cpTextActive = adaptiveColor(LIGHT_ON_SURFACE,     DARK_ON_SURFACE)
internal val cpTextSecond = adaptiveColor(LIGHT_ON_SURFACE_VAR, DARK_ON_SURFACE_VAR)
internal val cpTextFaded  = adaptiveColor(LIGHT_TEXT_FADED,     DARK_TEXT_FADED)

// ── Color helpers ─────────────────────────────────────────────────────────────

/** Day/night adaptive [ColorProvider] backed by [androidx.glance.color]. */
internal fun adaptiveColor(day: Color, night: Color): ColorProvider =
    androidx.glance.color.ColorProvider(day, night)

/**
 * Alpha-composite [fg] over [bg] at [alpha] opacity, returning a fully
 * opaque [Color].  Used to pre-compute tinted row backgrounds that are safe
 * for RemoteViews rendering.
 */
internal fun blendOver(fg: Color, bg: Color, alpha: Float): Color {
    val b = 1f - alpha
    return Color(
        red   = fg.red   * alpha + bg.red   * b,
        green = fg.green * alpha + bg.green * b,
        blue  = fg.blue  * alpha + bg.blue  * b,
        alpha = 1f,
    )
}

/** Parse a hex color string (e.g. "#6750A4"), falling back to [Color.Gray]. */
internal fun parseWidgetColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }
        .getOrDefault(Color.Gray)

// ── Duration formatting ───────────────────────────────────────────────────────

/**
 * Format a millisecond duration as a compact human-readable string:
 *  - "—"    when  ms ≤ 0
 *  - "<1m"  when  0 < totalMin < 1
 *  - "42m"  when  hours == 0
 *  - "2h 5m" when  hours > 0
 */
internal fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "—"
    val totalMin = ms / 60_000L
    if (totalMin == 0L) return "<1m"
    val hours = totalMin / 60L
    val mins  = totalMin % 60L
    return if (hours > 0L) "${hours}h ${mins}m" else "${mins}m"
}

// ── Time entry aggregation ────────────────────────────────────────────────────

/**
 * Aggregate [TimeEntry] durations by modeId.
 * An entry with a null [endEpochMs] is still active; its duration is
 * counted up to [nowMs].
 */
internal fun computeWidgetTotals(entries: List<TimeEntry>, nowMs: Long): Map<Long, Long> {
    val result = mutableMapOf<Long, Long>()
    for (entry in entries) {
        val duration = entry.elapsedMsAt(nowMs)
        result[entry.modeId] = (result[entry.modeId] ?: 0L) + duration
    }
    return result
}

internal fun findActiveModeId(entries: List<TimeEntry>, nowMs: Long): Long? =
    entries.lastOrNull { it.isActiveAt(nowMs) }?.modeId

// ── Widget update ─────────────────────────────────────────────────────────────

/**
 * Refresh both home-screen widgets by querying Android's authoritative
 * [AppWidgetManager] for each receiver's placed instance IDs, then driving
 * Glance's per-instance [update] directly.
 *
 * Unlike [GlanceAppWidget.updateAll], this path never relies on Glance's
 * process-scoped ID cache, which is unpopulated after a process restart or
 * when the widget host hasn't triggered [onUpdate] in the current session.
 */
internal suspend fun updateWidgets(context: Context) {
    val awm      = AppWidgetManager.getInstance(context)
    val glanceAwm = GlanceAppWidgetManager(context)
    listOf(
        ModeWidget()      to ModeWidgetReceiver::class.java,
        FocusModeWidget() to FocusModeWidgetReceiver::class.java,
    ).forEach { (widget, receiverClass) ->
        awm.getAppWidgetIds(ComponentName(context, receiverClass))
            .forEach { appWidgetId ->
                runCatching {
                    val glanceId = glanceAwm.getGlanceIdBy(appWidgetId)
                    widget.update(context, glanceId)
                }
            }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

/** Centered placeholder shown when the modes list is empty. */
@Composable
internal fun WidgetEmptyState() {
    Box(
        modifier         = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = "Open Lifekeeper to begin",
            style = TextStyle(color = cpTextSecond, fontSize = 13.sp),
        )
    }
}
