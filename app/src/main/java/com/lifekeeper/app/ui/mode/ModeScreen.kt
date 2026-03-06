package com.lifekeeper.app.ui.mode

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifekeeper.app.LifekeeperApp
import com.lifekeeper.app.R
import com.lifekeeper.app.data.model.Mode
import com.lifekeeper.app.data.model.TimeEntry
import com.lifekeeper.app.ui.calendar.todayMidnight
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

private val ITEM_HEIGHT = 108.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModeScreen(
    onOpenEditModes: () -> Unit,
    onOpenSettings : () -> Unit = {},
    onCalendarClick: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as LifekeeperApp
    val viewModel: ModeViewModel = viewModel(factory = ModeViewModel.factory(app))
    val modes by viewModel.modes.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Lifekeeper",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                    IconButton(onClick = onOpenEditModes) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.cd_edit_modes))
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        val todayEntries by viewModel.todayEntries.collectAsState()
        val nowMs        by viewModel.nowMs.collectAsState()
        val modesMap     = remember(modes) { modes.associateBy { it.id } }

        // Mid-night rollover: refresh dayStartMs when the system date changes
        // (e.g. the app stays open past midnight).
        var dayStartMs by remember { mutableStateOf(todayMidnight()) }
        val context = LocalContext.current
        DisposableEffect(context) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    dayStartMs = todayMidnight()
                }
            }
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_DATE_CHANGED))
            onDispose { context.unregisterReceiver(receiver) }
        }

        if (!viewModel.activeModeSeen || modes.isEmpty()) {
            // Hold the spinner until both flows have emitted. This guarantees that
            // ModeList is created with the correct initialFirstVisibleItemIndex and
            // never renders at index 0 before scrolling to the real active item.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // Compact timeline pinned to the top, just below the top bar.
                MiniDayStrip(
                    entries    = todayEntries,
                    modesMap   = modesMap,
                    nowMs      = nowMs,
                    dayStartMs = dayStartMs,
                    onTap      = onCalendarClick,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .height(44.dp),
                )
                ModeList(
                    modes     = modes,
                    viewModel = viewModel,
                    modifier  = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModeList(
    modes: List<Mode>,
    viewModel: ModeViewModel,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val durationsMs by viewModel.todayDurationsMs.collectAsState()

    // At this point activeModeSeen is guaranteed true (ModeScreen gate), so
    // activeModeId holds the real DB value. Passing the computed index to
    // rememberLazyListState positions the list at the correct item from the
    // very first frame — no scroll or animation ever occurs on launch or tab switch.
    val initialIdx = remember {
        val id = viewModel.activeModeId
        id?.let { modes.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIdx)

    val snapFling = rememberSnapFlingBehavior(
        snapLayoutInfoProvider = remember(listState) {
            androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider(
                lazyListState = listState,
                snapPosition  = SnapPosition.Center,
            )
        }
    )

    val centerModeIdx by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val vpCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .minByOrNull { abs((it.offset + it.size / 2) - vpCenter) }
                ?.index ?: 0
        }
    }

    // Animate to the active item when activeModeId changes externally (e.g. widget
    // tap). We wait for layout info to be populated before comparing indexes so that
    // centerModeIdx is accurate. On the very first composition the list is already
    // at the right position via initialFirstVisibleItemIndex, so after layout settles
    // targetIdx == centerModeIdx and no scroll fires.
    LaunchedEffect(viewModel.activeModeId) {
        val activeId  = viewModel.activeModeId ?: return@LaunchedEffect
        val targetIdx = modes.indexOfFirst { it.id == activeId }.takeIf { it >= 0 }
            ?: return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .first { it.isNotEmpty() }
        if (targetIdx != centerModeIdx) listState.animateScrollToItem(targetIdx)
    }

    // True while at least one finger is touching the list.
    var isDragging by remember { mutableStateOf(false) }

    // Mode switch — triggered only once the finger is lifted, after a short
    // pause to let the snap fling settle on its final item.
    LaunchedEffect(isDragging, centerModeIdx) {
        if (isDragging) return@LaunchedEffect
        delay(300)
        viewModel.switchMode(modes[centerModeIdx].id)
    }

    BoxWithConstraints(modifier = modifier) {
        val viewportHeight = maxHeight
        val aimLineTop    = viewportHeight / 2 - ITEM_HEIGHT / 2
        val aimLineBottom = viewportHeight / 2 + ITEM_HEIGHT / 2
        val density = LocalDensity.current

        // Always track the item currently in the crosshairs, not the DB-committed
        // activeModeId. This means finger-up animates transparent → new color in a
        // single hop — no intermediate flash of the old committed color.
        val centerModeColor by remember {
            derivedStateOf {
                modes.getOrNull(centerModeIdx)?.colorHex?.let {
                    runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
                } ?: Color.Transparent
            }
        }

        // Band clears only while the finger is actively touching AND the list is
        // moving (not on a stationary tap, not during a free fling after release).
        // The moment the finger lifts the band refills immediately, even mid-fling.
        val aimBandColor by animateColorAsState(
            targetValue   = if (isDragging && listState.isScrollInProgress) Color.Transparent
                            else centerModeColor.copy(alpha = 0.18f),
            animationSpec = tween(durationMillis = 500),
            label         = "aim_band",
        )

        // M3 semantic token for lines/borders — adapts to light/dark automatically.
        val aimLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

        // Colored band behind the list — highlights the selection zone.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ITEM_HEIGHT)
                .offset(y = aimLineTop)
                .background(aimBandColor)
        )

        LazyColumn(
            state          = listState,
            flingBehavior  = snapFling,
            modifier       = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Track finger contact independently of gestures consumed by
                    // the scroll/fling system — requireUnconsumed = false lets us
                    // observe the raw down/up without stealing events.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        isDragging = true
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                        isDragging = false
                    }
                },
            contentPadding = PaddingValues(
                top    = viewportHeight / 2 - ITEM_HEIGHT / 2,
                bottom = viewportHeight / 2 - ITEM_HEIGHT / 2,
            ),
        ) {
            itemsIndexed(modes, key = { _, mode -> mode.id }) { index, mode ->
                ModeItem(
                    mode       = mode,
                    isCenter   = index == centerModeIdx,
                    durationMs = durationsMs[mode.id] ?: 0L,
                    onClick    = {
                        scope.launch {
                            val info     = listState.layoutInfo
                            val vpCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
                            val item     = info.visibleItemsInfo.find { it.index == index }
                            if (item != null) {
                                // Item visible — scroll by exact delta to put it at center.
                                listState.animateScrollBy((item.offset + item.size / 2 - vpCenter).toFloat())
                            } else {
                                // Item off-screen — approximate via item height, snap cleans it up.
                                val itemHeightPx = with(density) { ITEM_HEIGHT.roundToPx() }
                                val currentPx = listState.firstVisibleItemIndex * itemHeightPx +
                                    listState.firstVisibleItemScrollOffset
                                listState.animateScrollBy((index * itemHeightPx - currentPx).toFloat())
                            }
                        }
                    },
                )
            }
        }

        // Top aim line.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .offset(y = aimLineTop)
                .background(aimLineColor)
        )
        // Bottom aim line.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .offset(y = aimLineBottom)
                .background(aimLineColor)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModeItem(
    mode: Mode,
    isCenter: Boolean,
    durationMs: Long,
    onClick: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme

    val scale by animateFloatAsState(
        targetValue   = if (isCenter) 1f else 0.84f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label         = "item_scale",
    )

    val textColor by animateColorAsState(
        targetValue   = if (isCenter)
            MaterialTheme.colorScheme.onSurface
        else
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        animationSpec = motionScheme.defaultEffectsSpec(),
        label         = "item_text",
    )

    // Modes with at least 1 minute tracked get the standard secondary text color;
    // anything shorter (including unused) fades to disabled — sub-minute is hidden.
    val durationTextColor by animateColorAsState(
        targetValue = if (durationMs >= 60_000L)
            MaterialTheme.colorScheme.onSurfaceVariant
        else
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "item_duration_text",
    )

    val modeColor = remember(mode.colorHex) {
        runCatching { Color(android.graphics.Color.parseColor(mode.colorHex)) }
            .getOrDefault(Color.Gray)
    }

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .height(ITEM_HEIGHT)
            .clickable(onClick = onClick)
            .scale(scale)
            .padding(horizontal = 32.dp, vertical = 10.dp),
    ) {
        // Mode color bar — vertical rounded line
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(modeColor)
        )

        Spacer(Modifier.width(20.dp))

        // Mode name — takes all spare width, ellipsizes if needed.
        Text(
            text     = mode.name,
            modifier = Modifier.weight(1f),
            style    = if (isCenter)
                MaterialTheme.typography.headlineSmall
            else
                MaterialTheme.typography.titleLarge,
            fontWeight = if (isCenter) FontWeight.SemiBold else FontWeight.Normal,
            color      = textColor,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.width(12.dp))

        // Today's duration, right-aligned — mirrors the widget layout.
        Text(
            text  = formatDuration(durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = durationTextColor,
        )
    }
}

// ── Mini day strip ───────────────────────────────────────────────────────────

private const val MINI_STRIP_DAY_MS = 24L * 60L * 60L * 1_000L

/**
 * Entries shorter than this are absorbed into the preceding segment.
 */
private const val MINI_MERGE_THRESHOLD_MS = 5L * 60_000L   // 5 minutes

/**
 * Same-mode entries separated by less than this gap are treated as continuous.
 */
private const val MINI_TOUCH_GAP_MS = 5_000L               // 5 seconds

/**
 * A compact, text-free horizontal timeline of today's tracked activity.
 *
 * Features:
 * - Short-entry merging (< [MINI_MERGE_THRESHOLD_MS]).
 * - Same-mode adjacent-entry merging (gap < [MINI_TOUCH_GAP_MS]).
 * - Hour tick marks + labels at the bottom of the bar.
 * - Pinch-to-zoom (1× – 8×) and horizontal drag to pan the visible window.
 */
@Composable
private fun MiniDayStrip(
    entries   : List<TimeEntry>,
    modesMap  : Map<Long, Mode>,
    nowMs     : Long,
    dayStartMs: Long,
    onTap     : () -> Unit = {},
    modifier  : Modifier = Modifier,
) {
    var zoomLevel     by remember { mutableStateOf(1f) }
    var panOffsetFrac by remember { mutableStateOf(0f) }
    var canvasWidthPx by remember { mutableStateOf(1f) }

    // ── Label visibility logic ────────────────────────────────────────────────
    // Labels are hidden when fully zoomed out and idle; they fade in on any touch
    // or zoom and fade back out 2 seconds after returning to the fully-zoomed-out state.
    var touchActive   by remember { mutableStateOf(false) }
    var labelsVisible by remember { mutableStateOf(false) }
    val labelAlpha    = remember { Animatable(0f) }
    val isZoomedIn    = zoomLevel > 1.005f

    // Show on any interaction; schedule hide 2 s after returning to full zoom.
    LaunchedEffect(isZoomedIn || touchActive) {
        if (isZoomedIn || touchActive) {
            labelsVisible = true
        } else {
            delay(2_000)
            labelsVisible = false
        }
    }
    // Animate the alpha: fast in, slower out.
    LaunchedEffect(labelsVisible) {
        labelAlpha.animateTo(
            targetValue   = if (labelsVisible) 1f else 0f,
            animationSpec = tween(durationMillis = if (labelsVisible) 200 else 400),
        )
    }

    // Derived window parameters — clamp pan so the window never leaves [0, 1].
    val windowFrac = (1f / zoomLevel).coerceIn(0f, 1f)
    panOffsetFrac  = panOffsetFrac.coerceIn(0f, (1f - windowFrac).coerceAtLeast(0f))

    // Build colour segments with short-entry merging AND same-mode adjacent merging.
    val segments = remember(entries, modesMap, nowMs, dayStartMs) {
        data class RawSeg(val startMs: Long, val endMs: Long, val color: Color, val modeId: Long)
        val raw = mutableListOf<RawSeg>()
        for (entry in entries.sortedBy { it.startEpochMs }) {
            val mode  = modesMap[entry.modeId] ?: continue
            val color = runCatching {
                Color(android.graphics.Color.parseColor(mode.colorHex))
            }.getOrNull() ?: continue
            val endMs      = entry.endEpochMs ?: nowMs
            val durationMs = endMs - entry.startEpochMs
            val last       = raw.lastOrNull()
            when {
                durationMs < MINI_MERGE_THRESHOLD_MS -> {
                    // Too short — absorb into previous.
                    if (last != null) raw[raw.lastIndex] = last.copy(endMs = endMs)
                }
                last != null
                    && last.modeId == entry.modeId
                    && entry.startEpochMs - last.endMs < MINI_TOUCH_GAP_MS -> {
                    // Same mode, touching — extend.
                    raw[raw.lastIndex] = last.copy(endMs = endMs)
                }
                else -> raw += RawSeg(entry.startEpochMs, endMs, color, entry.modeId)
            }
        }
        raw
    }

    val tickLineColor = MaterialTheme.colorScheme.onSurface
    val labelColor    = MaterialTheme.colorScheme.onSurfaceVariant

    // ── Paints — allocated once here, configured per draw to avoid per-frame allocs ──
    val hintTypeface = remember {
        android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC
        )
    }
    val labelPaint = remember { android.graphics.Paint() }
    val hintPaint  = remember { android.graphics.Paint() }

    // Hint text is picked once when the composable enters composition (app launch /
    // navigation) and never re-rolled on subsequent ticks. nowMs provides the
    // correct hour for slot selection at that moment.
    val hint = remember { miniStripHint(nowMs) }

    Canvas(
        modifier = modifier
            // Track finger-down/up to keep labels visible while the user is touching.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    touchActive = true
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    touchActive = false
                }
            }
            .pointerInput(onTap) {
            // Unified gesture handler: tap, single-touch pan, and two-finger pinch-zoom.
            //
            // Why not parallel detectTapGestures + detectTransformGestures?
            // detectTransformGestures guards zoom activation behind a touch-slop check
            // proportional to centroidSize (half the distance between fingers). When
            // fingers are placed close together (common for vertical pinch on a narrow
            // horizontal strip) centroidSize is tiny and the slop can never be exceeded,
            // silently swallowing the gesture. The custom handler below activates
            // multi-touch zoom immediately on two pointer-downs, no slop required.
            awaitEachGesture {
                val firstDown = awaitFirstDown(requireUnconsumed = false)
                var positions  = mutableMapOf(firstDown.id to firstDown.position)
                var movedSignificantly = false
                var prevCentroid = firstDown.position
                var prevDistance = 0f

                do {
                    val event   = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }

                    // Keep the positions map in sync.
                    pressed.forEach { positions[it.id] = it.position }
                    val activeIds = pressed.map { it.id }.toHashSet()
                    positions = positions.filterKeys { it in activeIds }.toMutableMap()

                    if (pressed.size >= 2) {
                        // ── Two-finger pinch: zoom anchored to centroid ──────────────
                        val p0       = pressed[0].position
                        val p1       = pressed[1].position
                        val centroid = Offset((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
                        val distance = (p0 - p1).getDistance()

                        if (prevDistance > 0f && canvasWidthPx > 0f) {
                            val ratio       = distance / prevDistance
                            val oldZoom     = zoomLevel
                            val newZoom     = (oldZoom * ratio).coerceIn(1f, 8f)
                            val oldWinFrac  = 1f / oldZoom
                            val newWinFrac  = 1f / newZoom

                            // Day-fraction pinned under the centroid:
                            //   centroidDayFrac = panOffsetFrac + (centroid.x / width) * oldWindowFrac
                            // After zoom, set pan so that same day-fraction stays at centroid.x:
                            //   panOffsetFrac_new = centroidDayFrac - (centroid.x / width) * newWindowFrac
                            // Then apply the centroid's own translation (pan component):
                            //   - (centroid.x - prevCentroid.x) / width * newWindowFrac
                            val centroidDayFrac = panOffsetFrac + (centroid.x / canvasWidthPx) * oldWinFrac
                            val panDeltaFrac    = -(centroid.x - prevCentroid.x) / canvasWidthPx * newWinFrac
                            zoomLevel     = newZoom
                            panOffsetFrac = (centroidDayFrac
                                    - (centroid.x / canvasWidthPx) * newWinFrac
                                    + panDeltaFrac
                                ).coerceIn(0f, (1f - newWinFrac).coerceAtLeast(0f))
                        }

                        prevDistance = distance
                        prevCentroid = centroid
                        movedSignificantly = true
                        pressed.forEach { it.consume() }
                    } else if (pressed.size == 1 && prevDistance == 0f) {
                        // ── Single touch: pan the timeline horizontally ──────────────
                        val change = pressed[0]
                        val delta  = change.position - change.previousPosition
                        if (delta.getDistance() > viewConfiguration.touchSlop / 4f) {
                            movedSignificantly = true
                        }
                        if (movedSignificantly && canvasWidthPx > 0f) {
                            val winFrac = 1f / zoomLevel
                            val panFrac = -delta.x / canvasWidthPx * winFrac
                            panOffsetFrac = (panOffsetFrac + panFrac)
                                .coerceIn(0f, (1f - winFrac).coerceAtLeast(0f))
                            change.consume()
                        }
                        prevDistance = 0f
                    } else {
                        // Pointer count changed mid-gesture; reset distance reference.
                        prevDistance = 0f
                    }
                } while (event.changes.any { it.pressed })

                // Fire tap only if the finger barely moved and it was single-touch.
                if (!movedSignificantly) onTap()
            }
        },
    ) {
        canvasWidthPx = size.width

        val stripH     = size.height * 0.58f
        val tickH       = 3.dp.toPx()
        val labelTextSz = 9.dp.toPx()
        val tickTop     = stripH + 3.dp.toPx()
        val labelBaseY  = tickTop + tickH + 1.dp.toPx() + labelTextSz
        val pan         = panOffsetFrac   // local val avoids smart-cast issues

        // ── Coloured bar (top portion of canvas) ──────────────────────────
        // All segments are plain rectangles; only the last (rightmost) one has
        // its right-side corners rounded.
        val r      = 4.dp.toPx()
        val barTop = 2.dp.toPx()
        val barBot = stripH - 2.dp.toPx()
        clipRect(0f, 0f, size.width, stripH) {
            segments.forEachIndexed { idx, seg ->
                val sDayFrac = ((seg.startMs - dayStartMs).toFloat() / MINI_STRIP_DAY_MS)
                    .coerceIn(0f, 1f)
                val eDayFrac = ((seg.endMs   - dayStartMs).toFloat() / MINI_STRIP_DAY_MS)
                    .coerceIn(0f, 1f)
                if (eDayFrac <= sDayFrac) return@forEachIndexed

                val xStart = ((sDayFrac - pan) / windowFrac).coerceIn(0f, 1f) * size.width
                val xEnd   = ((eDayFrac - pan) / windowFrac).coerceIn(0f, 1f) * size.width
                if (xEnd <= xStart) return@forEachIndexed

                val color = seg.color.copy(alpha = 0.88f)
                if (idx == segments.lastIndex) {
                    // Last segment: right corners rounded, left corners square.
                    val path = Path().apply {
                        addRoundRect(RoundRect(
                            left   = xStart, top    = barTop,
                            right  = xEnd,   bottom = barBot,
                            topLeftCornerRadius     = CornerRadius.Zero,
                            topRightCornerRadius    = CornerRadius(r),
                            bottomRightCornerRadius = CornerRadius(r),
                            bottomLeftCornerRadius  = CornerRadius.Zero,
                        ))
                    }
                    drawPath(path, color)
                } else {
                    drawRect(
                        color   = color,
                        topLeft = Offset(xStart, barTop),
                        size    = Size(xEnd - xStart, barBot - barTop),
                    )
                }
            }
        }

        // ── Hour tick marks + labels (below the bar) ───────────────────────
        val tickStepH = when {
            windowFrac > 0.75f -> 6
            windowFrac > 0.35f -> 3
            windowFrac > 0.12f -> 2
            else               -> 1
        }
        val alpha     = labelAlpha.value
        val hintAlpha = 1f - alpha
        val edgePad   = 2.dp.toPx()   // keeps edge labels inset from canvas boundary

        // Configure the remembered paints for this frame — no allocation.
        labelPaint.apply {
            textSize    = labelTextSz
            textAlign   = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            color       = labelColor.copy(alpha = alpha).toArgb()
        }
        hintPaint.apply {
            textSize    = labelTextSz
            textAlign   = android.graphics.Paint.Align.LEFT
            isAntiAlias = true
            typeface    = hintTypeface
            color       = labelColor.copy(alpha = hintAlpha * 0.7f).toArgb()
        }

        for (h in 0..24 step tickStepH) {
            val hFrac = h / 24f
            if (hFrac < pan - 0.001f || hFrac > pan + windowFrac + 0.001f) continue
            val wx    = ((hFrac - pan) / windowFrac) * size.width
            val label = if (h == 24) "24" else "%02d".format(h)
            // Clamp the text x so edge labels stay fully in frame.
            val halfW  = labelPaint.measureText(label) / 2f
            val wxText = wx.coerceIn(halfW + edgePad, size.width - halfW - edgePad)
            drawLine(
                color       = tickLineColor.copy(alpha = 0.22f * alpha),
                start       = Offset(wx, tickTop),
                end         = Offset(wx, tickTop + tickH),
                strokeWidth = 1.dp.toPx(),
            )
            drawIntoCanvas {
                it.nativeCanvas.drawText(label, wxText, labelBaseY, labelPaint)
            }
        }

        // ── Time-of-day hint text (right of last segment, hides when labels are shown) ──
        if (hintAlpha > 0.01f && hint != null && segments.isNotEmpty()) {
            val lastSeg  = segments.last()
            val eDayFrac = ((lastSeg.endMs - dayStartMs).toFloat() / MINI_STRIP_DAY_MS)
                .coerceIn(0f, 1f)
            val lastXEnd = ((eDayFrac - pan) / windowFrac) * size.width
            val hintX    = lastXEnd + 6.dp.toPx()

            // Need the hint start to be on-canvas and enough room to show
            // at least a few characters (hintPaint.textSize * 3 ≈ minimum readable width).
            val available = size.width - hintX
            if (hintX >= 0f && available >= hintPaint.textSize * 3f) {
                // Baseline: vertically centred on the coloured bar (0..stripH).
                val hintBaseY = stripH / 2f + labelTextSz / 2f - 1.dp.toPx()
                // clipRect truncates any overflow at the right edge cleanly.
                clipRect(hintX, 0f, size.width, size.height) {
                    drawIntoCanvas {
                        it.nativeCanvas.drawText(hint, hintX, hintBaseY, hintPaint)
                    }
                }
            }
        }
    }
}

/**
 * Formats a millisecond duration as a compact string.
 * Returns "—" for zero or sub-minute durations so the counter is
 * visually hidden until at least 1 minute has been tracked.
 */
private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "—"
    val totalMin = ms / 60_000L
    if (totalMin == 0L) return "—"
    val hours = totalMin / 60L
    val mins  = totalMin % 60L
    return if (hours > 0L) "${hours}h ${mins}m" else "${mins}m"
}

/**
 * Returns a hint string for the MiniDayStrip based on the current hour, or null
 * to hide the hint entirely. One entry is picked at random from each slot so the
 * label varies across sessions.
 */
private fun miniStripHint(nowMs: Long): String? {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
    val h   = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val candidates: List<String> = when {
        h < 3  -> listOf(
            "The day is over. Sleep well.",
            "Remember the sleep mode!",
            "Goodnight!",
        )
        h < 6  -> listOf(
            "Most of the world is still asleep.",
            "Good morning, early bird!",
            "A head start on the day!",
        )
        h < 10 -> listOf(
            "Have a great day!",
            "Good morning! Make the day count.",
            "Fresh start, good morning!",
        )
        h < 13 -> listOf(
            "So much time still ahead of you!",
            "Plenty of time to start something!",
            "Still a lot of time, what to do?",
        )
        h < 17 -> listOf(
            "Afternoon in progress...",
            "Good afternoon! Keep it up.",
            "Keep it up!",
        )
        h < 21 -> listOf(
            "Have a good evening!",
            "Have a lovely evening!",
            "How did today go?",
            "Almost done for today!",
        )
        else   -> listOf(
            "Remember sleep mode!",
            "Almost time to rest.",
            "Goodnight!",
        )
    }
    return candidates.random()
}