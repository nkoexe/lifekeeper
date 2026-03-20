package com.lifekeeper.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lifekeeper.app.LifekeeperApp
import com.lifekeeper.app.data.model.Mode

// ── Layout constants ──────────────────────────────────────────────────────────

private val ROW_HEIGHT         = 64.dp
private val COMPACT_ROW_HEIGHT = 56.dp
private val COMPACT_THRESHOLD  = 86.dp  // below this height → single-mode compact view

// ── Widget class ──────────────────────────────────────────────────────────────

class ModeWidget : GlanceAppWidget() {

    // SizeMode.Exact gives us LocalSize in Dp on every update so we can adapt.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as LifekeeperApp
        provideContent {
            // Observe all data inside the composition so the widget reacts
            // automatically to any DB change while the session is alive.
            val modes   by app.modeRepository.modes.collectAsState(emptyList())
            val entries by app.timeRepository.getTodayEntries().collectAsState(emptyList())
            val nowMs   = System.currentTimeMillis()
            val totals  = computeWidgetTotals(entries, nowMs)
            val activeModeId = findActiveModeId(entries, nowMs)
            WidgetContent(
                modes        = modes,
                totals       = totals,
                activeModeId = activeModeId,
            )
        }
    }

}

// ── Top-level composables ─────────────────────────────────────────────────────

@Composable
private fun WidgetContent(
    modes: List<Mode>,
    totals: Map<Long, Long>,
    activeModeId: Long?,
) {
    val isCompact = LocalSize.current.height < COMPACT_THRESHOLD

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(cpWidgetBg)
            .cornerRadius(24.dp),
    ) {
        when {
            modes.isEmpty() -> WidgetEmptyState()

            isCompact -> {
                // Compact: render only the active mode (or first if none selected).
                val pin = modes.find { it.id == activeModeId } ?: modes.first()
                ModeRow(
                    mode     = pin,
                    totalMs  = totals[pin.id] ?: 0L,
                    isActive = true,
                    compact  = true,
                )
            }

            else -> {
                // Normal / tall: scrollable list of all modes.
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(modes, itemId = { it.id }) { mode ->
                        ModeRow(
                            mode     = mode,
                            totalMs  = totals[mode.id] ?: 0L,
                            isActive = mode.id == activeModeId,
                            compact  = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeRow(
    mode:     Mode,
    totalMs:  Long,
    isActive: Boolean,
    compact:  Boolean,
) {
    val modeColor = parseWidgetColor(mode.colorHex)

    // Pre-blend active row tint to a fully opaque color — avoids RemoteViews
    // transparency issues while still visually tinting with the mode color.
    val rowBg = if (isActive)
        adaptiveColor(
            blendOver(modeColor, LIGHT_SURFACE, 0.18f),
            blendOver(modeColor, DARK_SURFACE,  0.26f),
        )
    else
        cpWidgetBg

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(if (compact) COMPACT_ROW_HEIGHT else ROW_HEIGHT)
            .background(rowBg)
            .clickable(
                actionRunCallback<SwitchModeAction>(
                    actionParametersOf(SwitchModeAction.ModeIdKey to mode.id)
                )
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Color indicator bar ───────────────────────────────────────────
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(if (compact) 22.dp else 30.dp)
                .cornerRadius(2.dp)
                .background(ColorProvider(modeColor)),
        ) {}

        Spacer(GlanceModifier.width(14.dp))

        // ── Mode name (centered in remaining space) ───────────────────────
        Text(
            text     = mode.name,
            modifier = GlanceModifier.defaultWeight(),
            style    = TextStyle(
                color      = if (isActive) cpTextActive else cpTextFaded,
                fontSize   = when {
                    isActive && !compact -> 17.sp
                    isActive             -> 15.sp
                    compact              -> 13.sp
                    else                 -> 14.sp
                },
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            ),
            maxLines = 1,
        )

        // ── Today's usage time ────────────────────────────────────────────
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text  = formatDuration(totalMs),
            style = TextStyle(
                color    = cpTextSecond,
                fontSize = if (compact) 12.sp else 11.sp,
            ),
        )
    }
}

