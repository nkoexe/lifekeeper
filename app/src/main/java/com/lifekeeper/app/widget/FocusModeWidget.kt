package com.lifekeeper.app.widget

import android.content.Intent
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
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
import com.lifekeeper.app.MainActivity

class FocusModeWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as LifekeeperApp
        provideContent {
            val modes   by app.modeRepository.modes.collectAsState(emptyList())
            val active  by app.timeRepository.getActiveEntryFlow().collectAsState(null)
            val entries by app.timeRepository.getTodayEntries().collectAsState(emptyList())
            val nowMs   = System.currentTimeMillis()
            val totals  = computeWidgetTotals(entries, nowMs)

            val activeMode = modes.find { it.id == active?.modeId } ?: modes.firstOrNull()
            FocusContent(
                modeName = activeMode?.name,
                colorHex = activeMode?.colorHex ?: "#808080",
                totalMs  = activeMode?.let { totals[it.id] ?: 0L } ?: 0L,
            )
        }
    }
}

// ── Content ───────────────────────────────────────────────────────────────────

@Composable
private fun FocusContent(
    modeName: String?,
    colorHex: String,
    totalMs:  Long,
) {
    val modeColor = parseWidgetColor(colorHex)
    val bg = if (modeName != null)
        adaptiveColor(
            blendOver(modeColor, LIGHT_SURFACE, 0.18f),
            blendOver(modeColor, DARK_SURFACE,  0.26f),
        )
    else cpWidgetBg

    val context   = LocalContext.current
    val launchApp = actionStartActivity(Intent(context, MainActivity::class.java))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(bg)
            .cornerRadius(24.dp)
            .clickable(launchApp),
        contentAlignment = Alignment.Center,
    ) {
        if (modeName == null) { WidgetEmptyState(); return@Box }

        Column(
            modifier            = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            // ── Secondary label ───────────────────────────────────────────
            Text(
                text  = "current mode:",
                style = TextStyle(color = cpTextFaded, fontSize = 11.sp),
            )

            Spacer(GlanceModifier.height(8.dp))

            // ── Pill + Name (left)  ·  Time (right) ───────────────────────
            Row(
                modifier           = GlanceModifier.fillMaxWidth(),
                verticalAlignment  = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = GlanceModifier
                        .width(4.dp)
                        .height(26.dp)
                        .cornerRadius(2.dp)
                        .background(ColorProvider(modeColor)),
                ) {}
                Spacer(GlanceModifier.width(10.dp))
                Text(
                    text     = modeName,
                    modifier = GlanceModifier.defaultWeight(),
                    style    = TextStyle(
                        color      = cpTextActive,
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Text(
                    text  = formatDuration(totalMs),
                    style = TextStyle(color = cpTextSecond, fontSize = 14.sp),
                )
            }
        }
    }
}
