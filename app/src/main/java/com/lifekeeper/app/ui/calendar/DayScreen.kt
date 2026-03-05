package com.lifekeeper.app.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifekeeper.app.LifekeeperApp
import com.lifekeeper.app.data.model.Mode
import com.lifekeeper.app.data.model.TimeEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Layout constants ──────────────────────────────────────────────────────────

/** Width of the left gutter that holds the hour labels. */
private val TIME_COLUMN_WIDTH   = 52.dp

/** Default pixel-height per hour at unit zoom. */
private val DEFAULT_HOUR_HEIGHT = 64.dp

/** Minimum hour height — prevents the grid from becoming unreadably dense. */
private val MIN_HOUR_HEIGHT     = 36.dp

/** Maximum hour height — prevents scrolling off screen for a single hour. */
private val MAX_HOUR_HEIGHT     = 160.dp

/** Minimum visible height for a very-short event block. */
private val MIN_EVENT_HEIGHT    = 10.dp

/** Horizontal padding between adjacent events (left/right of events column). */
private val EVENT_H_PADDING     = 4.dp

/** Corner radius for event blocks — matches M3 "small" shape category. */
private val EVENT_CORNER        = 6.dp

/**
 * Entries shorter than this are absorbed into the preceding block to prevent
 * visual clutter from rapid mode switching.
 */
private const val MERGE_THRESHOLD_MS = 2L * 60_000L   // 2 minutes

/**
 * Two consecutive entries of the same mode separated by less than this gap are
 * treated as a single continuous block (e.g. a brief app interruption).
 */
private const val TOUCH_GAP_MS = 5_000L              // 5 seconds

/** Page index that represents "today" in the HorizontalPager. */
private const val PAGER_CENTER_PAGE = 3_650

/** Total pager page count: ±10 years around today. */
private const val TOTAL_PAGES = 7_301

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun DayScreen() {
    val app = LocalContext.current.applicationContext as LifekeeperApp
    val vm: DayViewModel = viewModel(factory = DayViewModel.factory(app))
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    DayContent(
        uiState         = uiState,
        onPreviousDay   = vm::previousDay,
        onNextDay       = vm::nextDay,
        onToday         = vm::goToday,
        onViewModeCycle = vm::cycleViewMode,
        onSetDayOffset  = vm::setDayOffset,
    )
}

// ── Screen scaffold ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayContent(
    uiState        : DayUiState,
    onPreviousDay  : () -> Unit,
    onNextDay      : () -> Unit,
    onToday        : () -> Unit,
    onViewModeCycle: () -> Unit,
    onSetDayOffset : (Int) -> Unit,
) {
    val scope   = rememberCoroutineScope()
    val todayMs = remember { todayMidnight() }

    // Zoom — shared across all view modes and pager pages.
    var hourHeightDp by rememberSaveable(
        stateSaver = Saver(save = { it.value }, restore = { Dp(it) }),
    ) { mutableStateOf(DEFAULT_HOUR_HEIGHT) }

    // Pager state — always in memory; rendered only in SINGLE mode.
    val pagerState = rememberPagerState(
        initialPage = PAGER_CENTER_PAGE,
        pageCount   = { TOTAL_PAGES },
    )

    // Pager → VM: user settled on a new page.
    LaunchedEffect(pagerState.settledPage) {
        if (uiState.viewMode == ViewMode.SINGLE) {
            onSetDayOffset(pagerState.settledPage - PAGER_CENTER_PAGE)
        }
    }

    // VM → Pager: dayStartMs changed externally (buttons, goToday, mode switch).
    val expectedPage = remember(uiState.dayStartMs) {
        (PAGER_CENTER_PAGE + (uiState.dayStartMs - todayMs) / DayViewModel.DAY_MS).toInt()
    }
    LaunchedEffect(expectedPage, uiState.viewMode) {
        if (uiState.viewMode == ViewMode.SINGLE && pagerState.currentPage != expectedPage) {
            pagerState.scrollToPage(expectedPage)
        }
    }

    // Route button presses through the pager in SINGLE mode for animated swipes.
    val onPrev: () -> Unit = if (uiState.viewMode == ViewMode.SINGLE) {
        { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }
    } else onPreviousDay
    val onNext: () -> Unit = if (uiState.viewMode == ViewMode.SINGLE) {
        { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }
    } else onNextDay
    val onTodayNav: () -> Unit = if (uiState.viewMode == ViewMode.SINGLE) {
        { scope.launch { pagerState.animateScrollToPage(PAGER_CENTER_PAGE) } }
    } else onToday

    // Event detail bottom sheet.
    var selectedBlock by remember { mutableStateOf<EventBlockData?>(null) }
    selectedBlock?.let { block ->
        ModalBottomSheet(onDismissRequest = { selectedBlock = null }) {
            EventDetailSheet(block = block)
        }
    }

    val dayLabel = remember(uiState.dayStartMs, uiState.viewMode) {
        val shortFmt = SimpleDateFormat("d MMM", Locale.getDefault())
        when (uiState.viewMode) {
            ViewMode.SINGLE     ->
                SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
                    .format(Date(uiState.dayStartMs))
            ViewMode.THREE_DAYS ->
                "${shortFmt.format(Date(uiState.dayStartMs))} – " +
                shortFmt.format(Date(uiState.dayStartMs + 2 * DayViewModel.DAY_MS))
            ViewMode.WEEK       ->
                "${shortFmt.format(Date(uiState.dayStartMs))} – " +
                shortFmt.format(Date(uiState.dayStartMs + 6 * DayViewModel.DAY_MS))
        }
    }
    val viewModeIcon = when (uiState.viewMode) {
        ViewMode.SINGLE     -> Icons.Outlined.CalendarToday
        ViewMode.THREE_DAYS -> Icons.Outlined.DateRange
        ViewMode.WEEK       -> Icons.Outlined.ViewWeek
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = dayLabel,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = onViewModeCycle) {
                        Icon(viewModeIcon, contentDescription = "Cycle view mode")
                    }
                    if (!uiState.isToday) {
                        IconButton(onClick = onTodayNav) {
                            Icon(Icons.Outlined.Today, contentDescription = "Go to today")
                        }
                    }
                    IconButton(onClick = onPrev) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous")
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "Next")
                    }
                },
            )
        },
    ) { innerPadding ->
        // M3 Expressive scale+fade cross-fade when switching between 1-day / 3-day / week view.
        val motionScheme = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = uiState.viewMode,
            transitionSpec = {
                (scaleIn(
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    initialScale  = 0.92f,
                ) + fadeIn(motionScheme.defaultEffectsSpec())) togetherWith
                (scaleOut(
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    targetScale   = 0.92f,
                ) + fadeOut(motionScheme.defaultEffectsSpec()))
            },
            label    = "calendar-view-mode",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { viewMode ->
            when (viewMode) {
                ViewMode.SINGLE -> {
                    HorizontalPager(
                        state                   = pagerState,
                        beyondViewportPageCount = 1,
                        modifier                = Modifier.fillMaxSize(),
                    ) { page ->
                        val pageDayStart = todayMs + (page - PAGER_CENTER_PAGE).toLong() * DayViewModel.DAY_MS
                        val pageEntries  = if (pageDayStart == uiState.dayStartMs) uiState.entries
                                           else emptyList()
                        DayGrid(
                            dayStartMs         = pageDayStart,
                            entries            = pageEntries,
                            modes              = uiState.modes,
                            nowMs              = uiState.nowMs,
                            isToday            = pageDayStart == todayMs,
                            hourHeightDp       = hourHeightDp,
                            onHourHeightChange = { hourHeightDp = it },
                            scrollState        = rememberScrollState(),
                            onEventClick       = { selectedBlock = it },
                            modifier           = Modifier.fillMaxSize(),
                        )
                    }
                }
                ViewMode.THREE_DAYS,
                ViewMode.WEEK       -> {
                    MultiDayGrid(
                        uiState            = uiState,
                        hourHeightDp       = hourHeightDp,
                        onHourHeightChange = { hourHeightDp = it },
                        onEventClick       = { selectedBlock = it },
                        modifier           = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

// ── Scrollable / zoomable grid ────────────────────────────────────────────────

/**
 * The core hourly-calendar canvas.
 *
 * **Interaction model**
 * - Single-finger vertical swipe → [verticalScroll]
 * - Two-finger pinch → [rememberTransformableState] with [lockRotationOnZoomPan]=true,
 *   which only consumes multi-pointer events so the scroll gesture is unaffected.
 * - Zoom is reflected immediately (no spring), because it tracks live pointer input.
 *   M3 Expressive springs ([motionScheme.defaultSpatialSpec]) are reserved for
 *   discrete state transitions; continuous gesture tracking stays instantaneous.
 *
 * **Rendering layers** (bottom → top)
 * 1. [Canvas]: half-hour and full-hour grid lines.
 * 2. Absolute-offset [Text] composables: hour labels (01:00 … 23:00).
 * 3. [EventBlock] composables: one rounded rectangle per [TimeEntry].
 * 4. [NowLine]: current-time indicator, only when displaying today.
 */
@Composable
private fun DayGrid(
    dayStartMs        : Long,
    entries           : List<TimeEntry>,
    modes             : Map<Long, Mode>,
    nowMs             : Long,
    isToday           : Boolean,
    hourHeightDp      : Dp,
    onHourHeightChange: (Dp) -> Unit,
    scrollState       : ScrollState,
    onEventClick      : (EventBlockData) -> Unit = {},
    modifier          : Modifier = Modifier,
) {
    val density = LocalDensity.current

    // Snapshot the latest values so the pointerInput(Unit) closure is never stale.
    val currentHourHeight = rememberUpdatedState(hourHeightDp)
    val currentOnChange   = rememberUpdatedState(onHourHeightChange)

    val totalHeight  = hourHeightDp * 24
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSvColor    = MaterialTheme.colorScheme.onSurfaceVariant
    val errorColor   = MaterialTheme.colorScheme.error

    // Memoised event data: parse colors & clamp timestamps once per entries change.
    val eventBlocks = remember(entries, modes, nowMs) {
        buildEventBlocks(entries, modes, nowMs)
    }

    LaunchedEffect(dayStartMs) {
        val anchorMs = if (isToday) {
            maxOf(nowMs - 2L * 3_600_000L, dayStartMs)
        } else {
            dayStartMs + 7L * 3_600_000L
        }
        val anchorFrac = (anchorMs - dayStartMs).toFloat() / DayViewModel.DAY_MS
        val anchorPx   = with(density) { (totalHeight * anchorFrac).toPx() }
        scrollState.scrollTo(anchorPx.toInt().coerceAtLeast(0))
    }

    // transformable is placed *outside* verticalScroll so the gesture system sees
    // it first.  We handle only multi-touch here; single-touch falls through to
    // verticalScroll below.  pointerInput(Unit) never restarts so we read hourHeight
    // via rememberUpdatedState to avoid stale closures.
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Observe (not consume) the first down so verticalScroll still
                    // handles single-touch scrolling normally.
                    awaitFirstDown(requireUnconsumed = false)
                    var prevDistance = 0f
                    do {
                        val event   = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            val p0 = pressed[0].position
                            val p1 = pressed[1].position
                            val centroidY = (p0.y + p1.y) / 2f
                            val distance  = (p0 - p1).getDistance()
                            if (prevDistance > 0f) {
                                val ratio    = distance / prevDistance
                                val oldH     = currentHourHeight.value
                                val newH     = (oldH * ratio).coerceIn(MIN_HOUR_HEIGHT, MAX_HOUR_HEIGHT)
                                val actRatio = newH.value / oldH.value
                                currentOnChange.value(newH)
                                // Keep the content under the pinch centroid stationary:
                                // dispatchRawDelta is synchronous — no coroutine scheduling
                                // delay, so scroll and zoom update in the same frame.
                                val delta = (scrollState.value + centroidY) * (actRatio - 1f)
                                scrollState.dispatchRawDelta(delta)
                            }
                            prevDistance = distance
                            pressed.forEach { it.consume() }
                        } else {
                            prevDistance = 0f
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .verticalScroll(scrollState),
    ) {
        // Fixed-height canvas — all children use offset(y=…) for absolute positioning.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeight),
        ) {

            // ── Layer 1: grid lines ───────────────────────────────────────
            Canvas(modifier = Modifier.fillMaxSize()) {
                val hourPx     = hourHeightDp.toPx()
                val halfHourPx = hourPx / 2f
                val timeLeft   = TIME_COLUMN_WIDTH.toPx()
                val showQtr    = hourHeightDp > 96.dp

                for (h in 0..23) {
                    val yHour = h * hourPx

                    if (h > 0) {
                        if (showQtr) {
                            drawLine(
                                color       = outlineColor.copy(alpha = 0.10f),
                                start       = Offset(timeLeft, yHour - hourPx * 0.75f),
                                end         = Offset(size.width, yHour - hourPx * 0.75f),
                                strokeWidth = 0.5.dp.toPx() * 0.4f,
                            )
                        }
                        drawLine(
                            color       = outlineColor.copy(alpha = 0.18f),
                            start       = Offset(timeLeft, yHour - halfHourPx),
                            end         = Offset(size.width, yHour - halfHourPx),
                            strokeWidth = 0.5.dp.toPx() * 0.6f,
                        )
                        if (showQtr) {
                            drawLine(
                                color       = outlineColor.copy(alpha = 0.10f),
                                start       = Offset(timeLeft, yHour - hourPx * 0.25f),
                                end         = Offset(size.width, yHour - hourPx * 0.25f),
                                strokeWidth = 0.5.dp.toPx() * 0.4f,
                            )
                        }
                    }
                    drawLine(
                        color       = outlineColor.copy(alpha = 0.35f),
                        start       = Offset(timeLeft, yHour),
                        end         = Offset(size.width, yHour),
                        strokeWidth = 0.5.dp.toPx(),
                    )
                }
                // Final 23:30 line
                drawLine(
                    color       = outlineColor.copy(alpha = 0.18f),
                    start       = Offset(timeLeft, 23 * hourPx + halfHourPx),
                    end         = Offset(size.width, 23 * hourPx + halfHourPx),
                    strokeWidth = 0.5.dp.toPx() * 0.6f,
                )
            }

            // ── Layer 2: hour labels ──────────────────────────────────────
            // Skipping 00:00 avoids visual clutter at the very top.
            for (h in 1..23) {
                Text(
                    text      = "%02d:00".format(h),
                    modifier  = Modifier
                        .width(TIME_COLUMN_WIDTH - 8.dp)
                        // Centre the label on the hour line: -8 dp ~ half of labelSmall.
                        .offset(y = hourHeightDp * h - 8.dp),
                    style     = MaterialTheme.typography.labelSmall,
                    color     = onSvColor,
                    textAlign = TextAlign.End,
                )
            }

            // ── Layer 3: event blocks ─────────────────────────────────────
            eventBlocks.forEach { block ->
                EventBlock(
                    block        = block,
                    dayStartMs   = dayStartMs,
                    hourHeightDp = hourHeightDp,
                    totalHeight  = totalHeight,
                    onClick      = { onEventClick(block) },
                )
            }

            if (isToday) {
                NowLine(
                    nowMs       = nowMs,
                    dayStartMs  = dayStartMs,
                    totalHeight = totalHeight,
                    color       = errorColor,
                )
            }
        }
    }
}

// ── Internal data class ───────────────────────────────────────────────────────

private data class EventBlockData(
    val modeId  : Long,
    val modeName: String,
    val color   : Color,
    val startMs : Long,
    val endMs   : Long,   // always closed — open entries receive nowMs
)

/**
 * Converts raw [TimeEntry] rows into [EventBlockData] ready for rendering.
 *
 * Merging rules (applied in order):
 * 1. If the entry's duration is below [MERGE_THRESHOLD_MS], absorb it into the
 *    previous block (extend its endMs). This hides noise from rapid taps.
 * 2. If the entry has the *same mode* as the previous block and starts within
 *    [TOUCH_GAP_MS] of that block's end, merge them into one continuous block.
 *    This collapses brief interruptions (screen-off, notification, etc.).
 */
private fun buildEventBlocks(
    entries: List<TimeEntry>,
    modes  : Map<Long, Mode>,
    nowMs  : Long,
): List<EventBlockData> {
    val result = mutableListOf<EventBlockData>()
    for (entry in entries.sortedBy { it.startEpochMs }) {
        val mode  = modes[entry.modeId] ?: continue
        val color = runCatching {
            Color(android.graphics.Color.parseColor(mode.colorHex))
        }.getOrNull() ?: continue
        val endMs      = entry.endEpochMs ?: nowMs
        val durationMs = endMs - entry.startEpochMs
        val last       = result.lastOrNull()
        when {
            durationMs < MERGE_THRESHOLD_MS -> {
                // Too short: absorb into the previous block if one exists.
                if (last != null) result[result.lastIndex] = last.copy(endMs = endMs)
            }
            last != null
                && last.modeId == entry.modeId
                && entry.startEpochMs - last.endMs < TOUCH_GAP_MS -> {
                // Same mode, touching: extend the previous block.
                result[result.lastIndex] = last.copy(endMs = endMs)
            }
            else -> {
                result += EventBlockData(
                    modeId   = entry.modeId,
                    modeName = mode.name,
                    color    = color,
                    startMs  = entry.startEpochMs,
                    endMs    = endMs,
                )
            }
        }
    }
    return result
}

// ── Event block composable ────────────────────────────────────────────────────

@Composable
private fun EventBlock(
    block       : EventBlockData,
    dayStartMs  : Long,
    hourHeightDp: Dp,
    totalHeight : Dp,
    onClick     : () -> Unit = {},
    startPadding: Dp = TIME_COLUMN_WIDTH + EVENT_H_PADDING,
) {
    val dayMs    = DayViewModel.DAY_MS.toFloat()
    val topFrac  = ((block.startMs - dayStartMs).toFloat() / dayMs).coerceIn(0f, 1f)
    val endFrac  = ((block.endMs   - dayStartMs).toFloat() / dayMs).coerceIn(0f, 1f)
    val topDp    = totalHeight * topFrac
    val heightDp = (totalHeight * (endFrac - topFrac)).coerceAtLeast(MIN_EVENT_HEIGHT)

    // M3 guidance: choose ink colour from background luminance so text stays legible
    // regardless of the mode's hue. Threshold 0.4 gives comfortable contrast on both
    // saturated darks and light pastels.
    val textColor = if (block.color.luminance() > 0.4f) Color(0xFF1C1B1F) else Color.White

    Box(
        modifier = Modifier
            .padding(start = startPadding, end = EVENT_H_PADDING)
            .fillMaxWidth()
            .offset(y = topDp)
            .height(heightDp)
            .clip(RoundedCornerShape(EVENT_CORNER))
            .background(block.color.copy(alpha = 0.88f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text     = block.modeName,
            color    = textColor,
            style    = MaterialTheme.typography.labelMedium,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
        )
    }
}

// ── Now indicator ─────────────────────────────────────────────────────────────

/**
 * A current-time indicator styled after Google Calendar: a small filled dot at
 * the left boundary of the events area, extending into a 2 dp coloured line.
 *
 * The [color] is [MaterialTheme.colorScheme.error] (conventional red).
 */
@Composable
private fun NowLine(
    nowMs           : Long,
    dayStartMs      : Long,
    totalHeight     : Dp,
    color           : Color,
    lineStartPadding: Dp      = TIME_COLUMN_WIDTH,
    showDot         : Boolean = true,
) {
    val fraction = ((nowMs - dayStartMs).toFloat() / DayViewModel.DAY_MS).coerceIn(0f, 1f)
    // Offset upward by half the dot size so the dot sits centred on the timeline.
    val offsetY  = totalHeight * fraction - 5.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = offsetY)
            .height(10.dp),
    ) {
        // Line — expands to fill the events column area.
        Box(
            modifier = Modifier
                .padding(start = lineStartPadding)
                .fillMaxWidth()
                .height(2.dp)
                .background(color)
                .align(Alignment.Center),
        )
        // Circle dot — only drawn in single-day mode where there is a time gutter.
        if (showDot) {
            Box(
                modifier = Modifier
                    .offset(x = lineStartPadding - 5.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

// ── Multi-day grid (3-day / 7-day view) ──────────────────────────────────────

/**
 * Displays [uiState.viewMode.visibleDays] day columns side by side, sharing a
 * single time-label gutter and a single vertical-scroll + pinch-zoom gesture.
 *
 * Per-column entry data is precomputed outside the composition loop so that
 * no `remember` calls cross iteration boundaries.
 */
@Composable
private fun MultiDayGrid(
    uiState           : DayUiState,
    hourHeightDp      : Dp,
    onHourHeightChange: (Dp) -> Unit,
    onEventClick      : (EventBlockData) -> Unit,
    modifier          : Modifier = Modifier,
) {
    val n            = uiState.viewMode.visibleDays
    val density      = LocalDensity.current
    val todayMs      = remember { todayMidnight() }
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSvColor    = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor   = MaterialTheme.colorScheme.error
    val totalHeight  = hourHeightDp * 24
    val scrollState  = rememberScrollState()
    val currentHourHeight = rememberUpdatedState(hourHeightDp)
    val currentOnChange   = rememberUpdatedState(onHourHeightChange)

    // Precompute per-column data outside the composition loop.
    val columnData = remember(uiState) {
        (0 until n).map { col ->
            val colDayStart = uiState.dayStartMs + col.toLong() * DayViewModel.DAY_MS
            val colDayEnd   = colDayStart + DayViewModel.DAY_MS
            val colEntries  = uiState.entries.filter { e ->
                e.startEpochMs < colDayEnd &&
                (e.endEpochMs ?: Long.MAX_VALUE) > colDayStart
            }
            Triple(colDayStart, buildEventBlocks(colEntries, uiState.modes, uiState.nowMs), colDayStart == todayMs)
        }
    }

    LaunchedEffect(uiState.dayStartMs) {
        val anchorMs = if (uiState.isToday) {
            maxOf(uiState.nowMs - 2L * 3_600_000L, uiState.dayStartMs)
        } else {
            uiState.dayStartMs + 7L * 3_600_000L
        }
        val anchorFrac = (anchorMs - uiState.dayStartMs).toFloat() / DayViewModel.DAY_MS
        val anchorPx   = with(density) { (totalHeight * anchorFrac).toPx() }
        scrollState.scrollTo(anchorPx.toInt().coerceAtLeast(0))
    }

    Column(modifier = modifier) {
        // ── Date header row ──────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(TIME_COLUMN_WIDTH))
            for (col in 0 until n) {
                val (colDayStart, _, isColToday) = columnData[col]
                Text(
                    text       = remember(colDayStart) {
                        SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(colDayStart))
                    },
                    modifier   = Modifier.weight(1f),
                    style      = MaterialTheme.typography.labelMedium,
                    textAlign  = TextAlign.Center,
                    fontWeight = if (isColToday) FontWeight.Bold else FontWeight.Normal,
                    color      = if (isColToday) primaryColor else onSvColor,
                )
            }
        }

        // ── Scrollable + zoomable area ────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var prevDistance = 0f
                        do {
                            val event   = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                val p0 = pressed[0].position
                                val p1 = pressed[1].position
                                val centroidY = (p0.y + p1.y) / 2f
                                val distance  = (p0 - p1).getDistance()
                                if (prevDistance > 0f) {
                                    val ratio    = distance / prevDistance
                                    val oldH     = currentHourHeight.value
                                    val newH     = (oldH * ratio).coerceIn(MIN_HOUR_HEIGHT, MAX_HOUR_HEIGHT)
                                    val actRatio = newH.value / oldH.value
                                    currentOnChange.value(newH)
                                    val delta = (scrollState.value + centroidY) * (actRatio - 1f)
                                    scrollState.dispatchRawDelta(delta)
                                }
                                prevDistance = distance
                                pressed.forEach { it.consume() }
                            } else {
                                prevDistance = 0f
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .verticalScroll(scrollState),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight),
            ) {
                // Shared time-label gutter.
                Box(
                    modifier = Modifier
                        .width(TIME_COLUMN_WIDTH)
                        .height(totalHeight),
                ) {
                    for (h in 1..23) {
                        Text(
                            text      = "%02d:00".format(h),
                            modifier  = Modifier
                                .width(TIME_COLUMN_WIDTH - 8.dp)
                                .offset(y = hourHeightDp * h - 8.dp),
                            style     = MaterialTheme.typography.labelSmall,
                            color     = onSvColor,
                            textAlign = TextAlign.End,
                        )
                    }
                }

                // Day columns.
                for (col in 0 until n) {
                    val (colDayStart, colBlocks, isColToday) = columnData[col]
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(totalHeight),
                    ) {
                        // Grid lines.
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val hourPx     = hourHeightDp.toPx()
                            val halfHourPx = hourPx / 2f
                            val showQtr    = hourHeightDp > 96.dp
                            for (h in 0..23) {
                                val yHour = h * hourPx
                                if (h > 0) {
                                    if (showQtr) {
                                        drawLine(
                                            color       = outlineColor.copy(alpha = 0.10f),
                                            start       = Offset(0f, yHour - hourPx * 0.75f),
                                            end         = Offset(size.width, yHour - hourPx * 0.75f),
                                            strokeWidth = 0.5.dp.toPx() * 0.4f,
                                        )
                                    }
                                    drawLine(
                                        color       = outlineColor.copy(alpha = 0.18f),
                                        start       = Offset(0f, yHour - halfHourPx),
                                        end         = Offset(size.width, yHour - halfHourPx),
                                        strokeWidth = 0.5.dp.toPx() * 0.6f,
                                    )
                                    if (showQtr) {
                                        drawLine(
                                            color       = outlineColor.copy(alpha = 0.10f),
                                            start       = Offset(0f, yHour - hourPx * 0.25f),
                                            end         = Offset(size.width, yHour - hourPx * 0.25f),
                                            strokeWidth = 0.5.dp.toPx() * 0.4f,
                                        )
                                    }
                                }
                                drawLine(
                                    color       = outlineColor.copy(alpha = 0.35f),
                                    start       = Offset(0f, yHour),
                                    end         = Offset(size.width, yHour),
                                    strokeWidth = 0.5.dp.toPx(),
                                )
                            }
                            drawLine(
                                color       = outlineColor.copy(alpha = 0.18f),
                                start       = Offset(0f, 23 * hourHeightDp.toPx() + halfHourPx),
                                end         = Offset(size.width, 23 * hourHeightDp.toPx() + halfHourPx),
                                strokeWidth = 0.5.dp.toPx() * 0.6f,
                            )
                        }
                        // Event blocks (no time-gutter offset in multi-day columns).
                        colBlocks.forEach { block ->
                            EventBlock(
                                block        = block,
                                dayStartMs   = colDayStart,
                                hourHeightDp = hourHeightDp,
                                totalHeight  = totalHeight,
                                onClick      = { onEventClick(block) },
                                startPadding = EVENT_H_PADDING,
                            )
                        }
                        // Now indicator (no dot; line only).
                        if (isColToday) {
                            NowLine(
                                nowMs            = uiState.nowMs,
                                dayStartMs       = colDayStart,
                                totalHeight      = totalHeight,
                                color            = errorColor,
                                lineStartPadding = 0.dp,
                                showDot          = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Event detail sheet ───────────────────────────────────────────────────────────

/** Content placed inside the [ModalBottomSheet] when the user taps an event. */
@Composable
private fun EventDetailSheet(block: EventBlockData) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val durationMs = block.endMs - block.startMs
    val hours   = durationMs / 3_600_000L
    val minutes = (durationMs % 3_600_000L) / 60_000L
    val durationLabel = when {
        hours > 0L  -> "${hours}h ${minutes.toString().padStart(2, '0')}m"
        minutes > 0 -> "$minutes min"
        else        -> "< 1 min"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .padding(bottom = 32.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(block.color),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text       = block.modeName,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(text = timeFmt.format(Date(block.startMs)), style = MaterialTheme.typography.bodyLarge)
            Text(
                text  = "→",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = timeFmt.format(Date(block.endMs)), style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            text     = durationLabel,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
