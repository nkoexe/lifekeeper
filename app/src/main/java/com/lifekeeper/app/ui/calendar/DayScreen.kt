package com.lifekeeper.app.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifekeeper.app.LifekeeperApp
import com.lifekeeper.app.data.model.Mode
import com.lifekeeper.app.data.model.TimeEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Layout constants ──────────────────────────────────────────────────────────

/** Width of the left gutter that holds the hour labels. */
private val TIME_COLUMN_WIDTH   = 72.dp

/** Default pixel-height per hour at unit zoom. */
private val DEFAULT_HOUR_HEIGHT = 64.dp

/** Minimum hour height — allows the whole day to be visible in ~150 dp. */
private val MIN_HOUR_HEIGHT     = 6.dp

/** Maximum hour height — allows fine-grained scheduling at ~4-min resolution. */
private val MAX_HOUR_HEIGHT     = 240.dp

/** Minimum visible height for a very-short event block. */
private val MIN_EVENT_HEIGHT    = 0.dp   // No minimum — blocks must never bleed over neighbours

/** Gap between consecutive event blocks — M3 recommends 2dp spacing between tonal surfaces. */
private val EVENT_GAP_DP        = 2.dp

/** Half of EVENT_GAP_DP — applied as inset on each block's top and bottom. */
private val EVENT_GAP_HALF_DP   = 1.dp

/** Horizontal padding between adjacent events (left/right of events column). */
private val EVENT_H_PADDING     = 4.dp

/** Corner radius for event blocks — matches M3 "small" shape category. */
private val EVENT_CORNER        = 6.dp

/** Visual width of resize handle pills — M3 Expressive stadium shape. */
private val HANDLE_PILL_W   = 56.dp
/** Visual height of resize handle pills. */
private val HANDLE_PILL_H   = 20.dp
/** Touch-target height for the handle wrapper strip (centred on the block edge). */
private val HANDLE_TOUCH_H  = 44.dp

/** Minimum duration for any single block after a resize or move (5 minutes). */
private const val MIN_BLOCK_MS  = 5L * 60_000L

/**
 * When an adjacent block is squeezed below this duration during drag, it shows
 * a trash warning and will be deleted + absorbed on release.
 */
private const val DELETE_THRESHOLD_MS = 2L * 60_000L   // 2 minutes

/** 1 hour in milliseconds — used for continuous-strip pixel/time conversions. */
private const val HOUR_MS: Long             = 3_600_000L

/**
 * Two consecutive entries of the same mode separated by less than this gap are
 * treated as a single continuous block (e.g. a brief app interruption).
 */
// REMOVED: UI-level merging has been replaced by DB-level merging (mergeAdjacentSameMode).
// The constant is kept only as a named tombstone so git blame is clear.
// private const val TOUCH_GAP_MS = 5_000L

/** Width of the left-rail date badge — matches the time-gutter width. */
private val GUTTER_BADGE_WIDTH      = TIME_COLUMN_WIDTH

/** Height of the left-rail date badge slot — used for sticky push-out geometry. */
private val GUTTER_BADGE_HEIGHT     = 60.dp

/** Corner radius of the rounded-rectangle card inside the date badge. */
private val GUTTER_BADGE_CORNER     = 10.dp

/** Stroke height of the thicker midnight divider rule. */
private val MIDNIGHT_LINE_WIDTH     = 2.dp

/**
 * Minimum visual duration for the active (open) entry.  Ensures the block
 * representing the current mode is always tall enough to be tappable even
 * seconds after a switch, without distorting the real DB value.
 */
private const val MIN_VISUAL_DURATION_MS = 60_000L   // 1 minute

/** Active drag gesture on a long-pressed calendar block. */
private enum class DragMode { NONE, RESIZE_TOP, RESIZE_BOTTOM, MOVE }

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun DayScreen() {
    val app = LocalContext.current.applicationContext as LifekeeperApp
    val vm: DayViewModel = viewModel(factory = DayViewModel.factory(app))
    val uiState     by vm.uiState.collectAsStateWithLifecycle()
    val pendingUndo by vm.pendingUndo.collectAsStateWithLifecycle()

    InfiniteCalendarScreen(
        uiState              = uiState,
        pendingUndo          = pendingUndo,
        onEnsureWindow       = vm::ensureWindowCovers,
        onToggleFilter       = vm::toggleFilter,
        onClearFilters       = vm::clearAllFilters,
        onResizeEntry        = vm::resizeEntry,
        onMoveEntry          = vm::moveEntry,
        onAddPlanned         = vm::addPlannedEntry,
        onDeleteAdjacent     = vm::deleteAndAbsorbAdjacent,
        onUndo               = vm::undo,
        onClearUndo          = vm::clearUndo,
    )
}

// ── Screen scaffold ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InfiniteCalendarScreen(
    uiState          : DayUiState,
    pendingUndo      : UndoSnapshot?,
    onEnsureWindow   : (Long, Long) -> Unit = { _, _ -> },
    onToggleFilter   : (Long) -> Unit = {},
    onClearFilters   : () -> Unit = {},
    onResizeEntry    : (Long, Long?, Long?, Long?, Long?, Long?, UndoSnapshot?) -> Unit = { _, _, _, _, _, _, _ -> },
    onMoveEntry      : (Long, Long, Long, Long?, Long?, Long?, Long?, Long?, UndoSnapshot?) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onAddPlanned     : (Long, Long, Long) -> Unit = { _, _, _ -> },
    onDeleteAdjacent : (Long, Long, Long?, Long, Boolean, Long, Long?, Boolean, Long) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onUndo           : () -> Unit = {},
    onClearUndo      : () -> Unit = {},
) {
    val scope             = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState       = rememberScrollState()
    val density           = LocalDensity.current
    var viewportHeightPx  by remember { mutableStateOf(0f) }

    var hourHeightDp by rememberSaveable(
        stateSaver = Saver(save = { it.value }, restore = { Dp(it) }),
    ) { mutableStateOf(DEFAULT_HOUR_HEIGHT) }
    val currentHourHeight = rememberUpdatedState(hourHeightDp)

    // Compose Constraints max dimension is 262143 px (2^18 - 1). Cap the zoom so
    // totalHeight.toPx() = hourHeightDp * totalHours * density never exceeds that.
    // derivedStateOf (instead of remember(keys)) makes this a trackable State so it can
    // be read inside other derivedStateOf lambdas and by rememberUpdatedState below.
    val dynamicMaxHourHeightDp by remember(density) {
        derivedStateOf {
            val totalHours = (uiState.windowEndMs - uiState.windowStartMs).toFloat() / HOUR_MS
            if (totalHours > 0f) (262_000f / density.density / totalHours).dp.coerceAtMost(MAX_HOUR_HEIGHT)
            else MAX_HOUR_HEIGHT
        }
    }
    // rememberUpdatedState ensures the pointerInput zoom handler (keyed on Unit — never
    // restarted) always clamps against the CURRENT ceiling, not the value it captured
    // on first composition.
    val currentDynamicMaxHourHeight = rememberUpdatedState(dynamicMaxHourHeightDp)
    // Clamp current zoom if the window grew and the safe ceiling dropped.
    LaunchedEffect(dynamicMaxHourHeightDp) {
        if (hourHeightDp > dynamicMaxHourHeightDp) hourHeightDp = dynamicMaxHourHeightDp
    }

    // Event detail sheet.
    var scrollToMs    by remember { mutableStateOf<Long?>(null) }
    var selectedEvent by remember { mutableStateOf<SelectedEvent?>(null) }
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    selectedEvent?.let { ev ->
        ModalBottomSheet(
            onDismissRequest    = { selectedEvent = null },
            sheetState          = detailSheetState,
            contentWindowInsets = { WindowInsets(0) },
            dragHandle          = null,
        ) {
            EventDetailSheet(
                event             = ev,
                onOtherBlockClick = { other ->
                    selectedEvent = ev.copy(block = other)
                    scrollToMs = other.startMs
                },
            )
        }
    }

    // Pixel/time conversions derived from the EFFECTIVE zoom level — i.e. hourHeightDp
    // already clamped to the 262 k px limit.  All scroll↔time maths must use this scale
    // so they stay consistent with what ContinuousTimelineGrid actually renders.
    val effectiveHourHeightDp = hourHeightDp.coerceAtMost(dynamicMaxHourHeightDp)
    val hourHeightPx = with(density) { effectiveHourHeightDp.toPx() }
    val pxPerMs      = if (hourHeightPx > 0f) hourHeightPx / HOUR_MS.toFloat() else 0f
    val msPerPx      = if (pxPerMs     > 0f) 1f / pxPerMs else HOUR_MS.toFloat()

    // Visible time range derived from scroll position.
    // remember {} without keys keeps the derivedStateOf objects alive for the lifetime of the
    // composable. Compose snapshot tracking re-evaluates the lambdas whenever the State values
    // they read (scrollState.value, hourHeightDp, dynamicMaxHourHeightDp, uiState) change —
    // without recreating the derivedStateOf on every scroll frame.
    val visibleStartMs by remember {
        derivedStateOf {
            // Use the clamped effective height so the time calculation always matches the
            // pixel positions actually drawn by ContinuousTimelineGrid.
            val effH   = hourHeightDp.coerceAtMost(dynamicMaxHourHeightDp)
            val hourPx = with(density) { effH.toPx() }
            val msPx   = if (hourPx > 0f) HOUR_MS.toFloat() / hourPx else HOUR_MS.toFloat()
            uiState.windowStartMs + (scrollState.value * msPx).toLong()
        }
    }
    val visibleEndMs by remember {
        derivedStateOf {
            val effH   = hourHeightDp.coerceAtMost(dynamicMaxHourHeightDp)
            val hourPx = with(density) { effH.toPx() }
            val msPx   = if (hourPx > 0f) HOUR_MS.toFloat() / hourPx else HOUR_MS.toFloat()
            visibleStartMs + (viewportHeightPx * msPx).toLong()
        }
    }

    // Topmost visible calendar day — drives the sticky badge and TopAppBar subtitle.
    //
    // Derived in grid-coordinate space rather than via visibleStartMs, to avoid the
    // one-frame race where windowStartMs has already updated but the SideEffect scroll
    // compensation hasn't fired yet.  In that frame visibleStartMs would be off by the
    // prepended amount (up to 3 days), showing the wrong date.
    //
    // Grid coordinate: badge for day D sits at content-px = (D - windowStartMs)/HOUR_MS * hourPx.
    // Viewport top is at scrollState.value px.
    // The topmost fully-or-partially visible badge is the largest D where badge-px <= scroll.
    // Solving: D = windowStartMs + scrollState.value * HOUR_MS / hourPx, then snap to midnight.
    // This is algebraically identical to dayMidnight(visibleStartMs) but reads ONLY States
    // that are always consistent with each other (windowStartMs is always the grid origin),
    // so it stays correct during the SideEffect compensation frame.
    val visibleDayStartMs by remember {
        derivedStateOf {
            val effH   = hourHeightDp.coerceAtMost(dynamicMaxHourHeightDp)
            val hourPx = with(density) { effH.toPx() }
            val msPx   = if (hourPx > 0f) HOUR_MS.toFloat() / hourPx else HOUR_MS.toFloat()
            // Clamp to windowStartMs so the badge never shows a day before the window.
            val msFromOrigin = (scrollState.value * msPx).toLong().coerceAtLeast(0L)
            dayMidnight(uiState.windowStartMs + msFromOrigin)
        }
    }
    // The next calendar day — used only for the push-out geometry of the sticky badge.
    // No window-boundary guard: the push formula returns 0 naturally when the target badge
    // is off-screen.
    // Uses nextMidnight() instead of +DAY_MS to be DST-safe: adding 86400000ms would
    // land 1h before/after midnight on DST-transition days, placing the push geometry wrong.
    val nextVisibleDayStartMs by remember {
        derivedStateOf { nextMidnight(visibleDayStartMs) }
    }
    val visibleMonthYearLabel = remember(visibleDayStartMs) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(visibleDayStartMs))
    }

    // Pixel offset of today's midnight in the scrollable content.
    val todayOffsetPx = remember(uiState.windowStartMs, uiState.todayStartMs, hourHeightPx) {
        ((uiState.todayStartMs - uiState.windowStartMs).toFloat() * pxPerMs)
            .toInt().coerceAtLeast(0)
    }

    // Scroll to today's midnight once on first load.
    var initialScrollDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.windowStartMs, uiState.windowEndMs, todayOffsetPx) {
        if (!initialScrollDone && uiState.windowEndMs > uiState.windowStartMs) {
            scrollState.scrollTo(todayOffsetPx)
            initialScrollDone = true
        }
    }

    // Compensate scroll when the window expands backward (content prepended above).
    // SideEffect fires synchronously within the same Choreographer frame, before the layout
    // pass (measureAndLayout). When dispatchRawDelta updates scrollState, ScrollNode is marked
    // dirty and re-placed with the corrected offset — no visible one-frame jump.
    // A plain LongArray (not MutableState) avoids triggering a recomposition from the update.
    val prevWindowStartRef = remember { longArrayOf(uiState.windowStartMs) }
    SideEffect {
        val prependedMs = prevWindowStartRef[0] - uiState.windowStartMs
        if (prependedMs > 0L && pxPerMs > 0f && initialScrollDone) {
            scrollState.dispatchRawDelta((prependedMs * pxPerMs))
        }
        prevWindowStartRef[0] = uiState.windowStartMs
    }

    // Scroll to a specific time when an "other time" card is tapped in the detail sheet.
    LaunchedEffect(scrollToMs, uiState.windowStartMs, uiState.windowEndMs, hourHeightPx) {
        val target = scrollToMs ?: return@LaunchedEffect
        if (target in uiState.windowStartMs until uiState.windowEndMs && pxPerMs > 0f) {
            val contentY  = ((target - uiState.windowStartMs) * pxPerMs).toInt()
            val centeredY = (contentY - viewportHeightPx * 0.2f).toInt().coerceAtLeast(0)
            scrollState.animateScrollTo(centeredY)
            scrollToMs = null
        } else {
            onEnsureWindow(target, target + DayViewModel.DAY_MS)
        }
    }

    // Expand the loaded window as the user scrolls near the edges.
    LaunchedEffect(visibleStartMs, visibleEndMs) {
        onEnsureWindow(visibleStartMs, visibleEndMs)
    }

    // Undo snackbar.
    LaunchedEffect(pendingUndo) {
        val snap = pendingUndo ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message           = snap.label,
            actionLabel       = "Undo",
            withDismissAction = true,
            duration          = SnackbarDuration.Short,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> onUndo()
            SnackbarResult.Dismissed       -> onClearUndo()
        }
    }

    // Dismiss undo when filter changes.
    LaunchedEffect(uiState.filterModeIds) {
        snackbarHostState.currentSnackbarData?.dismiss()
        onClearUndo()
    }

    // Date-picker → scroll.
    var goToDateMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(goToDateMs, uiState.windowStartMs, uiState.windowEndMs, hourHeightPx) {
        val target = goToDateMs ?: return@LaunchedEffect
        if (target in uiState.windowStartMs until uiState.windowEndMs && pxPerMs > 0f) {
            val offsetPx = ((target - uiState.windowStartMs) * pxPerMs).toInt().coerceAtLeast(0)
            scrollState.animateScrollTo(offsetPx)
            goToDateMs = null
        } else {
            onEnsureWindow(target, target + DayViewModel.DAY_MS)
        }
    }

    // Date picker dialog.
    var showDatePicker by remember { mutableStateOf(false) }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.todayStartMs,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val picked = datePickerState.selectedDateMillis
                    if (picked != null) goToDateMs = dayMidnight(picked)
                    showDatePicker = false
                }) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Is today's 24-hour block at all visible in the current viewport?
    val isTodayVisible by remember(scrollState.value, viewportHeightPx, todayOffsetPx, hourHeightPx) {
        derivedStateOf {
            val vpTop  = scrollState.value.toFloat()
            val vpBot  = scrollState.value + viewportHeightPx
            val dayTop = todayOffsetPx.toFloat()
            val dayBot = dayTop + 24f * hourHeightPx
            dayBot >= vpTop && dayTop <= vpBot
        }
    }

    // "Add planned activity" sheet state.
    var showAddSheet by remember { mutableStateOf(false) }
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showAddSheet) {
        AddPlannedSheet(
            modes         = uiState.allModes,
            sheetState    = addSheetState,
            onDismiss     = { showAddSheet = false },
            onConfirm     = { modeId, startMs, endMs ->
                onAddPlanned(modeId, startMs, endMs)
                showAddSheet = false
            },
        )
    }

    // Sticky date badge push-out: translate the sticky badge upward as the next
    // day's in-content badge scrolls up into the sticky zone — same visual effect
    // as LazyColumn stickyHeader but computed manually for a continuous-scroll canvas.
    val stickyPushOffsetPx by remember {
        derivedStateOf {
            val nextDay   = nextVisibleDayStartMs
            // Use the same effective hour height as ContinuousTimelineGrid so the computed
            // badge pixel position aligns with where the badge is actually drawn.
            val effH      = hourHeightDp.coerceAtMost(dynamicMaxHourHeightDp)
            val hourPx    = with(density) { effH.toPx() }
            val badgeH    = with(density) { GUTTER_BADGE_HEIGHT.toPx() }
            val deltaHours         = (nextDay - uiState.windowStartMs).toFloat() / HOUR_MS
            val nextBadgeContentY  = deltaHours * hourPx
            val nextBadgeViewportY = nextBadgeContentY - scrollState.value
            // Clamp to [-badgeH, 0]: returns 0 when the next badge is far below (no push),
            // -badgeH when it has reached the viewport top (fully pushed off).
            (nextBadgeViewportY - badgeH).coerceIn(-badgeH, 0f)
        }
    }

    Scaffold(
        contentWindowInsets  = WindowInsets(0),
        snackbarHost         = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier  = Modifier.navigationBarsPadding(),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add planned activity")
            }
        },
        topBar = {
            Column {
                TopAppBar(
                    title   = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Timeline")
                            Text(
                                text  = visibleMonthYearLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Outlined.DateRange, contentDescription = "Go to date")
                        }
                        if (!isTodayVisible) {
                            IconButton(
                                onClick = { scope.launch { scrollState.animateScrollTo(todayOffsetPx) } },
                            ) {
                                Icon(Icons.Outlined.Today, contentDescription = "Jump to today")
                            }
                        }
                    },
                )
                // Mode filter chips — multi-select, horizontally scrollable.
                if (uiState.allModes.isNotEmpty()) {
                    LazyRow(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "filter_all") {
                            FilterChip(
                                selected = uiState.filterModeIds.isEmpty(),
                                onClick  = { onClearFilters() },
                                label    = { Text("All") },
                            )
                        }
                        items(uiState.allModes, key = { it.id }) { mode ->
                            val modeColor = runCatching {
                                Color(android.graphics.Color.parseColor(mode.colorHex))
                            }.getOrNull()
                            FilterChip(
                                selected    = mode.id in uiState.filterModeIds,
                                onClick     = { onToggleFilter(mode.id) },
                                label       = {
                                    Text(mode.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                leadingIcon = if (modeColor != null) {
                                    {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(modeColor),
                                        )
                                    }
                                } else null,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SideEffect { viewportHeightPx = with(density) { maxHeight.toPx() } }

            // Pinch-zoom wrapper — intercepts 2-finger events; single-finger falls through to scroll.
            // clipToBounds prevents the sticky badge from bleeding over the TopAppBar when pushed upward.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var prevDistance = 0f
                            do {
                                val event   = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size >= 2) {
                                    val p0        = pressed[0].position
                                    val p1        = pressed[1].position
                                    val centroidY = (p0.y + p1.y) / 2f
                                    val distance  = (p0 - p1).getDistance()
                                    if (prevDistance > 0f) {
                                        val ratio     = distance / prevDistance
                                        val oldH      = currentHourHeight.value
                                        // Use currentDynamicMaxHourHeight (rememberUpdatedState) not
                                        // the captured-at-first-composition dynamicMaxHourHeightDp, so
                                        // the ceiling is always current even though pointerInput(Unit)
                                        // never restarts when the window grows.
                                        val newH      = (oldH * ratio).coerceIn(MIN_HOUR_HEIGHT, currentDynamicMaxHourHeight.value)
                                        val actRatio  = newH.value / oldH.value
                                        // Anchor zoom so the content point under the pinch centroid stays fixed.
                                        val anchoredY = scrollState.value + centroidY
                                        hourHeightDp  = newH
                                        scrollState.dispatchRawDelta(anchoredY * (actRatio - 1f))
                                    }
                                    prevDistance = distance
                                    pressed.forEach { it.consume() }
                                } else {
                                    prevDistance = 0f
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            ) {
                // Continuous scrollable strip — one Box spanning the full loaded window.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                ) {
                    ContinuousTimelineGrid(
                        windowStartMs    = uiState.windowStartMs,
                        windowEndMs      = uiState.windowEndMs,
                        todayStartMs     = uiState.todayStartMs,
                        entries          = uiState.entries,
                        modes            = uiState.modes,
                        nowMs            = uiState.nowMs,
                        hourHeightDp     = hourHeightDp,
                        scrollOffsetPx   = scrollState.value,
                        viewportHeightPx = viewportHeightPx,
                        onEventClick     = { block, dayBlocks ->
                            selectedEvent = SelectedEvent(block, dayBlocks)
                        },
                        onResize         = onResizeEntry,
                        onMove           = onMoveEntry,
                        onDeleteAdjacent = onDeleteAdjacent,
                        modifier         = Modifier.fillMaxWidth(),
                    )
                }

                // Sticky date badge pinned at the top of the left rail.
                // Pushed upward (stickyPushOffsetPx < 0) when the next day's in-content
                // badge scrolls up to meet it — same push-out effect as stickyHeader.
                StickyDateRailBadge(
                    dayStartMs   = visibleDayStartMs,
                    isToday      = visibleDayStartMs == uiState.todayStartMs,
                    pushOffsetPx = stickyPushOffsetPx,
                    modifier     = Modifier.align(Alignment.TopStart),
                )
            }
        }
    }
}

// ── Sticky date rail badge ────────────────────────────────────────────────────

/**
 * M3 Expressive date badge pinned at the top of the left rail in [InfiniteCalendarScreen].
 *
 * Visually identical to [DayRailBadge]. The [pushOffsetPx] translates it upward
 * when the next day's in-content badge scrolls into the overlap zone — replicating
 * the LazyColumn stickyHeader push-out behaviour for a continuous-scroll canvas.
 */
@Composable
private fun StickyDateRailBadge(
    dayStartMs   : Long,
    isToday      : Boolean,
    pushOffsetPx : Float,
    modifier     : Modifier = Modifier,
) {
    DayRailBadge(
        dayStartMs = dayStartMs,
        isToday    = isToday,
        modifier   = modifier.offset(y = with(LocalDensity.current) { pushOffsetPx.toDp() }),
    )
}

// ── Day rail badge ────────────────────────────────────────────────────────────

/**
 * Compact M3 Expressive date badge for the left-rail gutter.
 *
 * Renders a rounded-rectangle card centred inside the [GUTTER_BADGE_WIDTH] slot:
 * - Weekday abbreviation: [labelSmall], subdued — scannable at a glance.
 * - Day number: [titleLarge] bold — dominant, large enough to read while scrolling.
 * - Today: [primary] card background with [onPrimary] text.
 * - Other days: [surfaceContainerHigh] card background with [onSurface] text.
 *
 * The outer [Box] fills the full slot with an opaque background so the badge
 * occludes grid lines when sticky-pinned at the top of the viewport.
 */
@Composable
private fun DayRailBadge(
    dayStartMs : Long,
    isToday    : Boolean,
    modifier   : Modifier = Modifier,
) {
    val weekday = remember(dayStartMs) {
        SimpleDateFormat("EEE", Locale.getDefault()).format(Date(dayStartMs))
            .uppercase(Locale.getDefault())
    }
    val dayNum = remember(dayStartMs) {
        SimpleDateFormat("d", Locale.getDefault()).format(Date(dayStartMs))
    }
    val colorScheme = MaterialTheme.colorScheme
    val cardBg    = if (isToday) colorScheme.primary         else colorScheme.surfaceContainerHigh
    val textColor = if (isToday) colorScheme.onPrimary       else colorScheme.onSurface
    val subColor  = if (isToday) colorScheme.onPrimary.copy(alpha = 0.75f)
                    else         colorScheme.onSurfaceVariant

    Box(
        modifier         = modifier
            .width(GUTTER_BADGE_WIDTH)
            .height(GUTTER_BADGE_HEIGHT)
            .background(colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier            = Modifier
                .width(GUTTER_BADGE_WIDTH - 12.dp)
                .height(GUTTER_BADGE_HEIGHT - 8.dp)
                .clip(RoundedCornerShape(GUTTER_BADGE_CORNER))
                .background(cardBg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text  = weekday,
                style = MaterialTheme.typography.labelSmall,
                color = subColor,
            )
            Text(
                text       = dayNum,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = textColor,
            )
        }
    }
}

// ── Continuous timeline grid ──────────────────────────────────────────────────

/**
 * Continuous hourly-calendar canvas spanning [windowStartMs]..[windowEndMs].
 *
 * Unlike the old per-day DayGrid, this composable paints the entire loaded window
 * as one fixed-height [Box].  Entries whose duration crosses midnight are drawn
 * as uninterrupted blocks — there are no day-boundary clamps.
 *
 * Day boundaries are indicated by:
 *  • A [MIDNIGHT_LINE_WIDTH] thick horizontal rule across the events column.
 *  • An M3 Expressive [DayRailBadge] in the left gutter.
 *
 * **Rendering layers** (bottom → top):
 *  1. [Canvas]: half-hour and full-hour grid lines.
 *  2. Absolute-offset hour labels (01:00 … repeating per day).
 *  3. Midnight rules and in-content [DayRailBadge] composables.
 *  4. [EventBlock] composables — epoch-relative positioning.
 *  5. [NowLine]: current-time indicator.
 *  6. Edit overlay: resize handles and body-drag zone for the selected block.
 */
@Composable
private fun ContinuousTimelineGrid(
    windowStartMs    : Long,
    windowEndMs      : Long,
    todayStartMs     : Long,
    entries          : List<TimeEntry>,
    modes            : Map<Long, Mode>,
    nowMs            : Long,
    hourHeightDp     : Dp,
    scrollOffsetPx   : Int   = 0,
    viewportHeightPx : Float = 0f,
    onEventClick     : (EventBlockData, List<EventBlockData>) -> Unit = { _, _ -> },
    onResize         : (Long, Long?, Long?, Long?, Long?, Long?, UndoSnapshot?) -> Unit = { _, _, _, _, _, _, _ -> },
    onMove           : (Long, Long, Long, Long?, Long?, Long?, Long?, Long?, UndoSnapshot?) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onDeleteAdjacent : (Long, Long, Long?, Long, Boolean, Long, Long?, Boolean, Long) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    modifier         : Modifier = Modifier,
) {
    val density = LocalDensity.current

    val totalDurationMs = (windowEndMs - windowStartMs).coerceAtLeast(HOUR_MS)
    val totalHoursFloat = totalDurationMs.toFloat() / HOUR_MS
    val totalHoursInt   = totalHoursFloat.toInt().coerceAtLeast(1)
    // Compose layout axes cannot exceed 2^18-1 = 262143 px. Clamp hourHeightDp so
    // totalHeight never exceeds this limit. The parent clamps via LaunchedEffect (async);
    // this local clamp is a synchronous safety-net for the one-frame race window.
    val effectiveHourHeightDp = hourHeightDp.coerceAtMost(
        (262_000f / density.density / totalHoursFloat).dp
    )
    val totalHeight = effectiveHourHeightDp * totalHoursFloat

    val outlineColor = MaterialTheme.colorScheme.outline
    val onSvColor    = MaterialTheme.colorScheme.onSurfaceVariant
    val errorColor   = MaterialTheme.colorScheme.error

    val dayStarts = remember(windowStartMs, windowEndMs) {
        buildList {
            var d = dayMidnight(windowStartMs)
            // Advance via nextMidnight() (25h + snap) — not +DAY_MS — so DST transitions
            // always produce the correct next local midnight without skipping or duplicating days.
            while (d < windowEndMs) { add(d); d = nextMidnight(d) }
        }
    }

    val eventBlocks = remember(entries, modes, nowMs) {
        buildEventBlocks(entries, modes, nowMs)
    }

    // ── Edit state ─────────────────────────────────────────────────────────
    var selectedBlockIdx by remember { mutableIntStateOf(-1) }
    var selectedEntryId  by remember { mutableStateOf<Long?>(null) }
    var dragMode         by remember { mutableStateOf(DragMode.NONE) }
    var liveStartMs      by remember { mutableStateOf<Long?>(null) }
    var liveEndMs        by remember { mutableStateOf<Long?>(null) }
    var liveAdjIdx       by remember { mutableIntStateOf(-1) }
    var liveAdjStartMs   by remember { mutableStateOf<Long?>(null) }
    var liveAdjEndMs     by remember { mutableStateOf<Long?>(null) }
    var liveAdj2Idx      by remember { mutableIntStateOf(-1) }
    var liveAdj2StartMs  by remember { mutableStateOf<Long?>(null) }
    var liveAdj2EndMs    by remember { mutableStateOf<Long?>(null) }
    var dragAccumPx      by remember { mutableFloatStateOf(0f) }
    var willDeletePrev   by remember { mutableStateOf(false) }
    var willDeleteNext   by remember { mutableStateOf(false) }

    fun clearDragState() {
        dragMode        = DragMode.NONE
        liveStartMs     = null
        liveEndMs       = null
        liveAdjIdx      = -1
        liveAdjStartMs  = null
        liveAdjEndMs    = null
        liveAdj2Idx     = -1
        liveAdj2StartMs = null
        liveAdj2EndMs   = null
        dragAccumPx     = 0f
        willDeletePrev  = false
        willDeleteNext  = false
    }

    fun clearEditState() {
        selectedBlockIdx = -1
        selectedEntryId  = null
        clearDragState()
    }

    LaunchedEffect(eventBlocks) {
        clearDragState()
        val prevId = selectedEntryId ?: return@LaunchedEffect
        val newIdx = eventBlocks.indexOfFirst { it.entryId == prevId }
        if (newIdx >= 0) {
            selectedBlockIdx = newIdx
        } else {
            selectedBlockIdx = -1
            selectedEntryId  = null
        }
    }

    // ms-per-pixel at current zoom level — uses effectiveHourHeightDp so drag-gesture
    // ms-to-px conversions stay consistent with the rendered grid positions.
    val msPerPx by remember(effectiveHourHeightDp) {
        derivedStateOf {
            val pxPerHour = with(density) { effectiveHourHeightDp.toPx() }
            if (pxPerHour > 0f) HOUR_MS.toFloat() / pxPerHour else 1f
        }
    }
    val haptic                  = LocalHapticFeedback.current
    val primaryContainerColor   = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    // Captured outside Canvas — Canvas block is not @Composable so MaterialTheme can't be read inside.
    val labelTextStyle = MaterialTheme.typography.labelSmall.copy(
        color     = onSvColor,
        textAlign = TextAlign.End,
    )
    val textMeasurer = rememberTextMeasurer()

    // Hour-of-day (0–23) at the start of this window in local time.
    // Used to convert hAbs (hours from window start) to actual clock hours for
    // grid labels and midnight-skip checks — correct even when windowStartMs is
    // not midnight-aligned (which happens after ensureWindowCovers expands the
    // window to an arbitrary visibleStartMs offset).
    val windowStartHourOfDay = remember(windowStartMs) {
        Calendar.getInstance().also { it.timeInMillis = windowStartMs }
            .get(Calendar.HOUR_OF_DAY)
    }

    // Back-press while a block is selected → deselect.
    BackHandler(enabled = selectedBlockIdx >= 0) { clearEditState() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            // Tap on any empty area → deselect the active block.
            // detectTapGestures only fires on a clean tap (not drag / long-press), so it
            // does not interfere with scrolling, zooming, or the resize handles.
            // Event-block taps are NOT suppressed: child pointerInput handlers also run,
            // so tapping a non-selected block both clears any selection AND fires onClick.
            .pointerInput(Unit) {
                detectTapGestures { clearEditState() }
            },
    ) {
        // ── Layer 1: grid lines ─────────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hourPx     = effectiveHourHeightDp.toPx()
            val halfHourPx = hourPx / 2f
            val timeLeft   = TIME_COLUMN_WIDTH.toPx()
            val showQtr    = effectiveHourHeightDp > 96.dp

            // Hour lines and sub-divisions. Skip midnight (h==0) — thick rule drawn on Composable layer.
            for (hAbs in 1 until totalHoursInt) {
                val yHour = hAbs * hourPx
                // Viewport cull: skip lines well outside the visible range.
                if (viewportHeightPx > 0f &&
                    (yHour < scrollOffsetPx.toFloat() - hourPx ||
                     yHour > scrollOffsetPx.toFloat() + viewportHeightPx + hourPx)) continue
                val h     = (windowStartHourOfDay + hAbs) % 24
                if (h == 0) continue

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
                drawLine(
                    color       = outlineColor.copy(alpha = 0.35f),
                    start       = Offset(timeLeft, yHour),
                    end         = Offset(size.width, yHour),
                    strokeWidth = 0.5.dp.toPx(),
                )
            }
            // 23:30 sub-line for each day (final half-hour before midnight).
            for (dayStart in dayStarts) {
                if (dayStart < windowStartMs) continue
                val dHours    = (dayStart - windowStartMs).toFloat() / HOUR_MS
                val yMidnight = dHours * hourPx
                val y2330     = yMidnight - halfHourPx
                if (y2330 > 0f) {
                    drawLine(
                        color       = outlineColor.copy(alpha = 0.18f),
                        start       = Offset(timeLeft, y2330),
                        end         = Offset(size.width, y2330),
                        strokeWidth = 0.5.dp.toPx() * 0.6f,
                    )
                }
            }
            // Vertical separator — right edge of the time gutter.
            drawLine(
                color       = outlineColor.copy(alpha = 0.30f),
                start       = Offset(timeLeft, 0f),
                end         = Offset(timeLeft, size.height),
                strokeWidth = 0.5.dp.toPx(),
            )

            // ── Layer 2: hour labels (Canvas drawText — no layout nodes, runs on render thread) ──
            // Moving labels here eliminates up to 700+ Text composables from the layout tree,
            // avoiding full re-measurement of all of them on every zoom gesture frame.
            val labelStride = when {
                effectiveHourHeightDp < 12.dp -> 6
                effectiveHourHeightDp < 22.dp -> 4
                effectiveHourHeightDp < 36.dp -> 2
                else                          -> 1
            }
            val labelWidth = timeLeft - 8.dp.toPx()
            for (hAbs in 1 until totalHoursInt) {
                val h = (windowStartHourOfDay + hAbs) % 24
                if (h == 0 || h % labelStride != 0) continue
                val yPx = hAbs * hourPx - 8.dp.toPx()
                if (viewportHeightPx > 0f &&
                    (yPx + hourPx < scrollOffsetPx.toFloat() - hourPx ||
                     yPx > scrollOffsetPx.toFloat() + viewportHeightPx + hourPx)) continue
                drawText(
                    textMeasurer = textMeasurer,
                    text         = "%02d:00".format(h),
                    style        = labelTextStyle,
                    topLeft      = Offset(0f, yPx),
                    size         = Size(labelWidth, hourPx),
                )
            }
        }

        // ── Layer 3: midnight rules and in-content day badges ───────────────
        val midnightColor      = MaterialTheme.colorScheme.outlineVariant
        val todayMidnightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        val badgeHeightPx      = with(density) { GUTTER_BADGE_HEIGHT.toPx() }
        for (dayStart in dayStarts) {
            if (dayStart < windowStartMs) continue
            // Use the same coordinate formula as Canvas grid lines: dHours * hourHeightDp.
            // This is identical to totalHeight * frac only when totalDurationMs is an exact
            // HOUR_MS multiple — not guaranteed after ensureWindowCovers runs.
            val dHours = (dayStart - windowStartMs).toFloat() / HOUR_MS
            val yDp    = effectiveHourHeightDp * dHours
            // Viewport cull: skip midnight rules and badges outside the visible area.
            if (viewportHeightPx > 0f) {
                val yPx = with(density) { yDp.toPx() }
                if (yPx > scrollOffsetPx.toFloat() + viewportHeightPx + badgeHeightPx) continue
                if (yPx + badgeHeightPx < scrollOffsetPx.toFloat() - badgeHeightPx) continue
            }
            // Thick midnight rule spanning the events column.
            Box(
                modifier = Modifier
                    .padding(start = TIME_COLUMN_WIDTH)
                    .fillMaxWidth()
                    .offset(y = yDp - MIDNIGHT_LINE_WIDTH / 2)
                    .height(MIDNIGHT_LINE_WIDTH)
                    .background(
                        if (dayStart == todayStartMs) todayMidnightColor else midnightColor,
                    ),
            )
            // In-content date badge in the left gutter at this midnight position.
            DayRailBadge(
                dayStartMs = dayStart,
                isToday    = dayStart == todayStartMs,
                modifier   = Modifier.offset(y = yDp),
            )
        }

        // ── Layer 4: event blocks ───────────────────────────────────────────
        eventBlocks.forEachIndexed { idx, block ->
            val isSel = idx == selectedBlockIdx
            val renderBlock = when {
                isSel              -> block.copy(
                    startMs = liveStartMs  ?: block.startMs,
                    endMs   = liveEndMs    ?: block.endMs,
                )
                idx == liveAdjIdx  -> block.copy(
                    startMs = liveAdjStartMs ?: block.startMs,
                    endMs   = liveAdjEndMs   ?: block.endMs,
                )
                idx == liveAdj2Idx -> block.copy(
                    startMs = liveAdj2StartMs ?: block.startMs,
                    endMs   = liveAdj2EndMs   ?: block.endMs,
                )
                else               -> block
            }
            val selIdx = selectedBlockIdx
            val isAboutToDelete = when {
                willDeletePrev && selIdx >= 1 && idx == selIdx - 1 -> true
                willDeleteNext && selIdx >= 0 && idx == selIdx + 1 -> true
                else -> false
            }
            // Viewport cull: skip composing off-screen event blocks to reduce layout cost.
            // Always keep live-drag participants (selected + adjacent) in the tree.
            val isLiveParticipant = isSel || idx == liveAdjIdx || idx == liveAdj2Idx
            if (!isLiveParticipant && viewportHeightPx > 0f) {
                val bTopPx = with(density) {
                    (effectiveHourHeightDp * ((renderBlock.startMs - windowStartMs).toFloat() / HOUR_MS)).toPx()
                }
                val bBotPx = with(density) {
                    (effectiveHourHeightDp * ((renderBlock.endMs - windowStartMs).toFloat() / HOUR_MS)).toPx()
                }
                if (bBotPx < scrollOffsetPx.toFloat() - viewportHeightPx ||
                    bTopPx > scrollOffsetPx.toFloat() + viewportHeightPx * 2f) return@forEachIndexed
            }
            EventBlock(
                block           = renderBlock,
                timelineStartMs = windowStartMs,
                totalDurationMs = totalDurationMs,
                totalHeight     = totalHeight,
                isSelected      = isSel,
                isAboutToDelete = isAboutToDelete,
                onClick         = {
                    if (isSel) {
                        clearEditState()
                    } else {
                        val sameDay = dayMidnight(block.startMs)
                        onEventClick(block, eventBlocks.filter { dayMidnight(it.startMs) == sameDay })
                    }
                },
                onLongPress     = if (!isSel) {
                    {
                        selectedBlockIdx = idx
                        selectedEntryId  = block.entryId
                        clearDragState()
                    }
                } else {
                    {}
                },
            )
        }

        // ── Trash-delete indicator ──────────────────────────────────────────
        run {
            val boundaryMs: Long = when {
                willDeletePrev -> liveStartMs ?: return@run
                willDeleteNext -> liveEndMs   ?: return@run
                else           -> return@run
            }
            val frac       = ((boundaryMs - windowStartMs).toFloat() / totalDurationMs).coerceIn(0f, 1f)
            val circleSize = 28.dp
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(
                        x = TIME_COLUMN_WIDTH - circleSize / 2,
                        y = totalHeight * frac - circleSize / 2,
                    )
                    .size(circleSize)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
            ) {
                Icon(
                    imageVector        = Icons.Outlined.Delete,
                    contentDescription = "Will be deleted",
                    tint               = Color.White,
                    modifier           = Modifier.size(16.dp),
                )
            }
        }

        // ── Edit overlay ────────────────────────────────────────────────────
        if (selectedBlockIdx in eventBlocks.indices) {
            val selIdx    = selectedBlockIdx
            val rawBlock  = eventBlocks[selIdx]
            val prevBlock = if (selIdx > 0) eventBlocks[selIdx - 1] else null
            val nextBlock = if (selIdx < eventBlocks.lastIndex) eventBlocks[selIdx + 1] else null

            val rawBlockRef      = rememberUpdatedState(rawBlock)
            val prevBlockRef     = rememberUpdatedState(prevBlock)
            val nextBlockRef     = rememberUpdatedState(nextBlock)
            val msPerPxRef       = rememberUpdatedState(msPerPx)
            val windowStartMsRef = rememberUpdatedState(windowStartMs)
            val windowEndMsRef   = rememberUpdatedState(windowEndMs)

            val effStart   = liveStartMs ?: rawBlock.startMs
            val effEnd     = liveEndMs   ?: rawBlock.endMs
            val tlMs       = totalDurationMs.toFloat().coerceAtLeast(1f)
            val topFrac    = ((effStart - windowStartMs).toFloat() / tlMs).coerceIn(0f, 1f)
            val endFrac    = ((effEnd   - windowStartMs).toFloat() / tlMs).coerceIn(0f, 1f)
            val blockTopDp = totalHeight * topFrac + EVENT_GAP_HALF_DP
            val blockBotDp = totalHeight * endFrac - EVENT_GAP_HALF_DP

            // ── TOP HANDLE ────────────────────────────────────────────────
            if (dragMode != DragMode.MOVE) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .padding(start = TIME_COLUMN_WIDTH + EVENT_H_PADDING, end = EVENT_H_PADDING)
                        .fillMaxWidth()
                        .offset(y = blockTopDp - HANDLE_TOUCH_H / 2)
                        .height(HANDLE_TOUCH_H)
                        .pointerInput(selIdx, windowStartMs) {
                            detectDragGestures(
                                onDragStart = {
                                    dragMode    = DragMode.RESIZE_TOP
                                    dragAccumPx = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragAccumPx += dragAmount.y
                                    val rb        = rawBlockRef.value
                                    val prevBlk   = prevBlockRef.value
                                    val candidate = rb.startMs + (dragAccumPx * msPerPxRef.value).toLong()
                                    val ceiling   = rb.endMs - MIN_BLOCK_MS
                                    val floor     = prevBlk?.let { it.startMs + DELETE_THRESHOLD_MS / 2 }
                                        ?: windowStartMsRef.value
                                    val clamped   = candidate.coerceIn(floor, maxOf(floor, ceiling))
                                    liveStartMs = clamped
                                    if (prevBlk != null) {
                                        liveAdjIdx     = selIdx - 1
                                        liveAdjEndMs   = clamped
                                        liveAdjStartMs = null
                                        willDeletePrev = (clamped - prevBlk.startMs) < DELETE_THRESHOLD_MS
                                    }
                                },
                                onDragEnd = {
                                    val rb       = rawBlockRef.value
                                    val prevBlk  = prevBlockRef.value
                                    val newStart = liveStartMs
                                    if (willDeletePrev && prevBlk != null) {
                                        onDeleteAdjacent(
                                            rb.entryId, rb.startMs, rb.endMs,
                                            prevBlk.entryId, false,
                                            prevBlk.startMs, prevBlk.endMs, prevBlk.isOpen, prevBlk.modeId,
                                        )
                                    } else if (newStart != null && newStart != rb.startMs) {
                                        val snap = UndoSnapshot(
                                            editType     = EditType.RESIZE_TOP,
                                            label        = "Resized",
                                            entryId      = rb.entryId,
                                            startEntryId = rb.startEntryId,
                                            prevStartMs  = rb.startMs,
                                            prevEndMs    = rb.endMs,
                                            adjacentId   = prevBlk?.entryId,
                                            adjPrevEndMs = prevBlk?.endMs,
                                        )
                                        onResize(rb.entryId, newStart, null,
                                            prevBlk?.entryId, null, newStart, snap)
                                    }
                                    clearDragState()
                                },
                                onDragCancel = { clearDragState() },
                            )
                        },
                ) {
                    ResizeHandlePill(color = primaryContainerColor, onColor = onPrimaryContainerColor)
                }
            }

            // ── BODY DRAG ZONE ────────────────────────────────────────────
            val isMovable = rawBlock.startEntryId == rawBlock.entryId
            if (isMovable && dragMode != DragMode.RESIZE_TOP && dragMode != DragMode.RESIZE_BOTTOM) {
                val bodyTopDp    = blockTopDp + HANDLE_TOUCH_H / 2
                val bodyBotDp    = blockBotDp - HANDLE_TOUCH_H / 2
                val bodyHeightDp = (bodyBotDp - bodyTopDp).coerceAtLeast(0.dp)
                if (bodyHeightDp > 0.dp) {
                    Box(
                        modifier = Modifier
                            .padding(start = TIME_COLUMN_WIDTH + EVENT_H_PADDING, end = EVENT_H_PADDING)
                            .fillMaxWidth()
                            .offset(y = bodyTopDp)
                            .height(bodyHeightDp)
                            .pointerInput(selIdx, windowStartMs) {
                                // Move requires a long-press on the already-selected block.
                                // A plain drag (without prior long-press) falls through to the
                                // verticalScroll gesture, scrolling the view as expected.
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragMode    = DragMode.MOVE
                                        dragAccumPx = 0f
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragAccumPx += dragAmount.y
                                        val rb       = rawBlockRef.value
                                        val prevBlk  = prevBlockRef.value
                                        val nextBlk  = nextBlockRef.value
                                        val wStart   = windowStartMsRef.value
                                        val wEnd     = windowEndMsRef.value
                                        val deltaMs  = (dragAccumPx * msPerPxRef.value).toLong()
                                        val minStart = prevBlk?.let { it.startMs + DELETE_THRESHOLD_MS / 2 }
                                            ?: wStart
                                        val duration = rb.endMs - rb.startMs
                                        val maxEnd   = nextBlk?.let { it.endMs - DELETE_THRESHOLD_MS / 2 }
                                            ?: (wEnd + 30L * DayViewModel.DAY_MS)
                                        val maxStart = maxEnd - duration
                                        val newStart = (rb.startMs + deltaMs).coerceIn(minStart, maxOf(minStart, maxStart))
                                        val newEnd   = newStart + duration
                                        liveStartMs  = newStart
                                        liveEndMs    = newEnd
                                        if (prevBlk != null) {
                                            liveAdjIdx     = selIdx - 1
                                            liveAdjEndMs   = newStart
                                            liveAdjStartMs = null
                                            willDeletePrev = (newStart - prevBlk.startMs) < DELETE_THRESHOLD_MS
                                        }
                                        if (nextBlk != null) {
                                            liveAdj2Idx     = selIdx + 1
                                            liveAdj2StartMs = newEnd
                                            liveAdj2EndMs   = null
                                            willDeleteNext  = (nextBlk.endMs - newEnd) < DELETE_THRESHOLD_MS
                                        }
                                        if (willDeletePrev && willDeleteNext) {
                                            val prevR = newStart - (prevBlk?.startMs ?: Long.MAX_VALUE)
                                            val nextR = (nextBlk?.endMs ?: Long.MAX_VALUE) - newEnd
                                            if (prevR <= nextR) willDeleteNext = false
                                            else willDeletePrev = false
                                        }
                                    },
                                    onDragEnd = {
                                        val rb       = rawBlockRef.value
                                        val prevBlk  = prevBlockRef.value
                                        val nextBlk  = nextBlockRef.value
                                        val newStart = liveStartMs
                                        if (newStart != null && newStart != rb.startMs) {
                                            if (willDeletePrev && prevBlk != null) {
                                                onDeleteAdjacent(
                                                    rb.entryId, rb.startMs, rb.endMs,
                                                    prevBlk.entryId, false,
                                                    prevBlk.startMs, prevBlk.endMs, prevBlk.isOpen, prevBlk.modeId,
                                                )
                                            } else if (willDeleteNext && nextBlk != null) {
                                                onDeleteAdjacent(
                                                    rb.entryId, rb.startMs, rb.endMs,
                                                    nextBlk.entryId, true,
                                                    nextBlk.startMs, nextBlk.endMs, nextBlk.isOpen, nextBlk.modeId,
                                                )
                                            } else {
                                                val newEnd = liveEndMs
                                                if (newEnd != null) {
                                                    val snap = UndoSnapshot(
                                                        editType        = EditType.MOVE,
                                                        label           = "Moved",
                                                        entryId         = rb.entryId,
                                                        startEntryId    = rb.startEntryId,
                                                        prevStartMs     = rb.startMs,
                                                        prevEndMs       = if (rb.isOpen) null else rb.endMs,
                                                        adjacentId      = prevBlk?.entryId,
                                                        adjPrevEndMs    = prevBlk?.endMs,
                                                        adjacent2Id     = nextBlk?.startEntryId,
                                                        adj2PrevStartMs = nextBlk?.startMs,
                                                    )
                                                    onMove(
                                                        rb.entryId, rb.startEntryId,
                                                        newStart, newEnd,
                                                        prevBlk?.entryId, newStart,
                                                        nextBlk?.startEntryId, newEnd,
                                                        snap,
                                                    )
                                                }
                                            }
                                        }
                                        clearDragState()
                                    },
                                    onDragCancel = { clearDragState() },
                                )
                            },
                    )
                }
            }

            // ── BOTTOM HANDLE ──────────────────────────────────────────────
            if (dragMode != DragMode.MOVE) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .padding(start = TIME_COLUMN_WIDTH + EVENT_H_PADDING, end = EVENT_H_PADDING)
                        .fillMaxWidth()
                        .offset(y = blockBotDp - HANDLE_TOUCH_H / 2)
                        .height(HANDLE_TOUCH_H)
                        .pointerInput(selIdx, windowStartMs) {
                            detectDragGestures(
                                onDragStart = {
                                    dragMode    = DragMode.RESIZE_BOTTOM
                                    dragAccumPx = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragAccumPx += dragAmount.y
                                    val rb        = rawBlockRef.value
                                    val nextBlk   = nextBlockRef.value
                                    val wEnd      = windowEndMsRef.value
                                    val candidate = rb.endMs + (dragAccumPx * msPerPxRef.value).toLong()
                                    val floor     = rb.startMs + MIN_BLOCK_MS
                                    val ceiling   = nextBlk?.let { it.endMs - DELETE_THRESHOLD_MS / 2 }
                                        ?: (wEnd + 30L * DayViewModel.DAY_MS)
                                    val clamped   = candidate.coerceIn(floor, maxOf(floor, ceiling))
                                    liveEndMs = clamped
                                    if (nextBlk != null) {
                                        liveAdjIdx     = selIdx + 1
                                        liveAdjStartMs = clamped
                                        liveAdjEndMs   = null
                                        willDeleteNext = (nextBlk.endMs - clamped) < DELETE_THRESHOLD_MS
                                    }
                                },
                                onDragEnd = {
                                    val rb      = rawBlockRef.value
                                    val nextBlk = nextBlockRef.value
                                    val newEnd  = liveEndMs
                                    if (willDeleteNext && nextBlk != null) {
                                        onDeleteAdjacent(
                                            rb.entryId, rb.startMs, rb.endMs,
                                            nextBlk.entryId, true,
                                            nextBlk.startMs, nextBlk.endMs, nextBlk.isOpen, nextBlk.modeId,
                                        )
                                    } else if (newEnd != null && newEnd != rb.endMs) {
                                        val snap = UndoSnapshot(
                                            editType       = EditType.RESIZE_BOTTOM,
                                            label          = "Resized",
                                            entryId        = rb.entryId,
                                            startEntryId   = rb.startEntryId,
                                            prevStartMs    = rb.startMs,
                                            prevEndMs      = if (rb.isOpen) null else rb.endMs,
                                            adjacentId     = nextBlk?.entryId,
                                            adjPrevStartMs = nextBlk?.startMs,
                                        )
                                        onResize(rb.entryId, null, newEnd,
                                            nextBlk?.entryId, newEnd, null, snap)
                                    }
                                    clearDragState()
                                },
                                onDragCancel = { clearDragState() },
                            )
                        },
                ) {
                    ResizeHandlePill(color = primaryContainerColor, onColor = onPrimaryContainerColor)
                }
            }
        }

        // ── Now indicator ───────────────────────────────────────────────────
        if (nowMs in windowStartMs until windowEndMs) {
            NowLine(
                nowMs           = nowMs,
                timelineStartMs = windowStartMs,
                totalDurationMs = totalDurationMs,
                totalHeight     = totalHeight,
                color           = errorColor,
            )
        }
    }
}

// ── Internal data class ───────────────────────────────────────────────────────

private data class EventBlockData(
    val entryId      : Long,   // DB id of the TimeEntry that owns endEpochMs for this block
    val startEntryId : Long,   // DB id of the TimeEntry that owns startEpochMs (== entryId for single entries)
    val modeId       : Long,
    val modeName     : String,
    val color        : Color,
    val startMs      : Long,
    val endMs        : Long,   // always closed — open entries receive nowMs
    val isOpen       : Boolean, // true ↔ the underlying DB entry has endEpochMs == null
    val isPlanned    : Boolean, // true ↔ startMs > nowMs (future scheduled entry)
)

/**
 * Converts raw [TimeEntry] rows into [EventBlockData] ready for rendering.
 *
 * **One DB row → one [EventBlockData]** — no UI-level merging.  The database
 * is the single source of truth; adjacent same-mode entries are merged there
 * by [mergeAdjacentSameMode] before they ever reach the UI.
 *
 * Special handling:
 * - Open entries (currently active) receive `nowMs` as their visual end, with
 *   a minimum of [MIN_VISUAL_DURATION_MS] so the block is always reachable.
 *   The visual end is clamped to the start of the next block to avoid overlap.
 * - [isPlanned] = true when `startEpochMs > nowMs` and the entry is closed
 *   (a future-scheduled block).  Rendered with a distinct style.
 */
private fun buildEventBlocks(
    entries: List<TimeEntry>,
    modes  : Map<Long, Mode>,
    nowMs  : Long,
): List<EventBlockData> {
    val sorted = entries.sortedBy { it.startEpochMs }
    val result = mutableListOf<EventBlockData>()
    for (entry in sorted) {
        val mode  = modes[entry.modeId] ?: continue
        val color = runCatching {
            Color(android.graphics.Color.parseColor(mode.colorHex))
        }.getOrNull() ?: continue
        val isOpen    = entry.endEpochMs == null
        val isPlanned = !isOpen && entry.startEpochMs > nowMs
        // Raw visual end: nowMs for open entries, stored value for closed/planned ones.
        val rawEnd = entry.endEpochMs ?: nowMs
        result += EventBlockData(
            entryId      = entry.id,
            startEntryId = entry.id,
            modeId       = entry.modeId,
            modeName     = mode.name,
            color        = color,
            startMs      = entry.startEpochMs,
            endMs        = rawEnd,
            isOpen       = isOpen,
            isPlanned    = isPlanned,
        )
    }

    // Post-process: enforce a minimum visual duration for the open (active) entry so
    // it is always reachable, clamped to the next block's start to prevent overlap.
    for (i in result.indices) {
        val block = result[i]
        if (!block.isOpen) continue
        val minEnd = block.startMs + MIN_VISUAL_DURATION_MS
        if (block.endMs >= minEnd) continue          // already large enough
        val ceiling = if (i + 1 < result.size) result[i + 1].startMs else Long.MAX_VALUE
        result[i] = block.copy(endMs = minOf(minEnd, ceiling).coerceAtLeast(block.endMs))
    }

    return result
}

// ── Resize handle pill ────────────────────────────────────────────────────────

/**
 * M3 Expressive stadium-shaped drag handle pill — centered in the event column.
 *
 * Visual design follows M3 guidelines:
 * • Stadium (fully-rounded rectangle) shape — clearly distinct from event blocks.
 * • [HANDLE_PILL_W] × [HANDLE_PILL_H] visual size; the touch target is the outer
 *   full-width Box wrapper provided by the caller.
 * • Two parallel horizontal bars convey vertical-drag affordance.
 * • Subtle shadow conveys elevation above the underlying block.
 */
@Composable
private fun ResizeHandlePill(
    color  : Color,
    onColor: Color,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(HANDLE_PILL_W, HANDLE_PILL_H)
            .shadow(2.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(color),
    ) {
        Column(
            verticalArrangement   = Arrangement.spacedBy(3.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
        ) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(onColor.copy(alpha = 0.6f)),
                )
            }
        }
    }
}

// ── Event block composable ────────────────────────────────────────────────────

@Composable
private fun EventBlock(
    block           : EventBlockData,
    timelineStartMs : Long,
    totalDurationMs : Long,
    totalHeight     : Dp,
    onClick         : () -> Unit = {},
    onLongPress     : () -> Unit = {},
    isSelected      : Boolean    = false,
    isAboutToDelete : Boolean    = false,
    startPadding    : Dp         = TIME_COLUMN_WIDTH + EVENT_H_PADDING,
) {
    val haptic            = LocalHapticFeedback.current
    // rememberUpdatedState ensures the pointerInput coroutine (keyed on isSelected) always
    // calls the *current* callback even when the block list changes and a new lambda is passed
    // without the key changing — the classic stale-closure-in-pointerInput bug.
    val currentOnClick    = rememberUpdatedState(onClick)
    val currentOnLongPress = rememberUpdatedState(onLongPress)
    val timelineMs = totalDurationMs.toFloat().coerceAtLeast(1f)
    val topFrac    = ((block.startMs - timelineStartMs).toFloat() / timelineMs).coerceIn(0f, 1f)
    val endFrac    = ((block.endMs   - timelineStartMs).toFloat() / timelineMs).coerceIn(0f, 1f)

    // Gap math: each block is inset by half the gap on top and bottom so that
    // adjacent blocks never touch visually. Zero-height blocks become invisible.
    val rawTopDp    = totalHeight * topFrac
    val topDp       = rawTopDp + EVENT_GAP_HALF_DP
    val rawH        = totalHeight * (endFrac - topFrac)
    val rawHeightDp = (rawH - EVENT_GAP_DP).coerceAtLeast(0.dp)
    // When about to be absorbed always show at least 20 dp so the trash icon is visible
    // even at low zoom where 1-minute slivers collapse to sub-pixel height.
    val heightDp    = if (isAboutToDelete) rawHeightDp.coerceAtLeast(20.dp) else rawHeightDp

    if (heightDp <= 0.dp) return  // too short to render — never bleeds on neighbour

    // Planned entries use lower fill opacity + a colored border to distinguish
    // them visually from recorded history. Active (open) entry stays fully opaque.
    val alpha    = when {
        isSelected      -> 1.0f
        block.isPlanned -> 0.35f
        else            -> 0.88f
    }
    // Text colour: dark ink on light fills, white on dark fills.
    val textColor = if (block.color.luminance() > 0.4f) Color(0xFF1C1B1F) else Color.White

    Box(
        modifier = Modifier
            .padding(start = startPadding, end = EVENT_H_PADDING)
            .fillMaxWidth()
            .offset(y = topDp)
            .height(heightDp)
            .then(
                if (isSelected) Modifier.shadow(4.dp, RoundedCornerShape(EVENT_CORNER))
                else Modifier
            )
            .clip(RoundedCornerShape(EVENT_CORNER))
            .background(block.color.copy(alpha = alpha))
            .then(
                // Planned entries get a dashed-style border (implemented as a solid
                // 1.5 dp stroke — Compose Canvas doesn't do native dashes in Modifier).
                if (block.isPlanned) Modifier.border(
                    width = 1.5.dp,
                    color = block.color,
                    shape = RoundedCornerShape(EVENT_CORNER),
                ) else Modifier
            )
            .pointerInput(isSelected) {
                detectTapGestures(
                    onTap      = { currentOnClick.value() },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnLongPress.value()
                    },
                )
            },
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text(
                text     = block.modeName,
                color    = textColor,
                style    = MaterialTheme.typography.labelMedium,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
            if (block.isPlanned) {
                Text(
                    text  = "Planned",
                    color = textColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
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
    timelineStartMs : Long,
    totalDurationMs : Long,
    totalHeight     : Dp,
    color           : Color,
    lineStartPadding: Dp      = TIME_COLUMN_WIDTH,
    showDot         : Boolean = true,
) {
    val fraction = ((nowMs - timelineStartMs).toFloat() / totalDurationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
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

// ── Event detail sheet ────────────────────────────────────────────────────────

private data class SelectedEvent(
    val block    : EventBlockData,
    /** All merged blocks on the same calendar day — used for "other times". */
    val dayBlocks: List<EventBlockData>,
)

private fun formatBlockDuration(durationMs: Long): String {
    val hours   = durationMs / 3_600_000L
    val minutes = (durationMs % 3_600_000L) / 60_000L
    return when {
        hours > 0L  -> "${hours}h ${minutes.toString().padStart(2, '0')}m"
        minutes > 0 -> "$minutes min"
        else        -> "< 1 min"
    }
}

/** Content placed inside the [ModalBottomSheet] when the user taps an event block. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EventDetailSheet(
    event             : SelectedEvent,
    onOtherBlockClick : (EventBlockData) -> Unit,
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val motionScheme = MaterialTheme.motionScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp, bottom = 40.dp),
    ) {

        // ── Drag handle ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                .align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(20.dp))

        // ── Animated content — cross-fades when the selected block changes ──
        AnimatedContent(
            targetState = event.block,
            transitionSpec = {
                (slideInVertically(
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    initialOffsetY = { it / 8 },
                ) + fadeIn(motionScheme.defaultEffectsSpec())) togetherWith
                (slideOutVertically(
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    targetOffsetY = { -it / 8 },
                ) + fadeOut(motionScheme.defaultEffectsSpec()))
            },
            label = "block-transition",
        ) { block ->

            val otherBlocks = remember(event, block) {
                event.dayBlocks.filter { it.modeId == block.modeId && it != block }
            }

            Column(modifier = Modifier.fillMaxWidth()) {

                // ── Mode header ──────────────────────────────────────────
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(block.color),
                    )
                    Text(
                        text       = block.modeName,
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(28.dp))

                // ── Timeline connector ──────────────────────────────────
                //
                // Layout:
                //   "Start"  [spacer/rule/duration pill]  "End"
                //   time                                   time
                //
                // The connector row sits between the two label rows so the
                // horizontal rule is vertically centred between them, not
                // against just the clock numerals.
                //
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Row 1: labels
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text  = "Start",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text  = "End",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Row 2: time values + connector rule
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text       = timeFmt.format(Date(block.startMs)),
                            style      = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        // Connector: rule + duration pill, centred between the two numbers
                        Box(
                            modifier         = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.outlineVariant),
                            )
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text  = formatBlockDuration(block.endMs - block.startMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }

                        Text(
                            text       = timeFmt.format(Date(block.endMs)),
                            style      = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign  = TextAlign.End,
                        )
                    }
                }

                // ── Other occurrences ────────────────────────────────────
                if (otherBlocks.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(16.dp))

                    val count = otherBlocks.size
                    Text(
                        text  = "$count other ${if (count == 1) "time" else "times"} on this day",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(12.dp))

                    // Adaptive grid: 1 column on phone (< 600dp), 2 on tablet.
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val columns = if (maxWidth >= 600.dp) 2 else 1
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            otherBlocks.chunked(columns).forEach { rowItems ->
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    rowItems.forEach { other ->
                                        OtherTimeCard(
                                            other    = other,
                                            timeFmt  = timeFmt,
                                            onClick  = { onOtherBlockClick(other) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    // Fill empty cell in last row when columns = 2 and odd item count
                                    if (rowItems.size < columns) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtherTimeCard(
    other   : EventBlockData,
    timeFmt : java.text.SimpleDateFormat,
    onClick : () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeFmtRemembered = remember { timeFmt }
    Card(
        onClick = onClick,
        modifier = modifier,
        shape    = MaterialTheme.shapes.large,
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(other.color),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = "${timeFmtRemembered.format(Date(other.startMs))} – ${timeFmtRemembered.format(Date(other.endMs))}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = formatBlockDuration(other.endMs - other.startMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// ── Add planned activity sheet ────────────────────────────────────────────────

/**
 * Bottom sheet for scheduling a future activity.
 *
 * The user picks a mode from the list. The time selection is a placeholder
 * (defaults to +1 h → +2 h from now) and will be replaced by a proper
 * time-range picker in a future iteration.
 *
 * Pressing "Add" calls [onConfirm] with (modeId, startMs, endMs); the caller
 * dismisses the sheet via [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPlannedSheet(
    modes      : List<Mode>,
    sheetState : androidx.compose.material3.SheetState,
    onDismiss  : () -> Unit,
    onConfirm  : (modeId: Long, startMs: Long, endMs: Long) -> Unit,
) {
    var selectedModeId by remember { mutableStateOf<Long?>(null) }

    ModalBottomSheet(
        onDismissRequest    = onDismiss,
        sheetState          = sheetState,
        contentWindowInsets = { WindowInsets(0) },
        dragHandle          = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Sheet handle
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    .align(Alignment.CenterHorizontally),
            )

            Text(
                text  = "Plan an activity",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text  = "Choose a mode to schedule",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Mode list
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                modes.forEach { mode ->
                    val modeColor = runCatching {
                        Color(android.graphics.Color.parseColor(mode.colorHex))
                    }.getOrNull() ?: MaterialTheme.colorScheme.primary
                    val isSelected = mode.id == selectedModeId
                    ListItem(
                        headlineContent = { Text(mode.name) },
                        leadingContent  = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(modeColor),
                            )
                        },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { selectedModeId = mode.id }
                            .then(
                                if (isSelected) Modifier.background(
                                    MaterialTheme.colorScheme.secondaryContainer
                                ) else Modifier
                            ),
                    )
                }
            }

            // Time placeholder note
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text  = "Will be scheduled +1 h from now (time picker coming soon)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Confirm button
            Button(
                onClick = {
                    val id = selectedModeId ?: return@Button
                    val now      = System.currentTimeMillis()
                    val startMs  = now + 60 * 60_000L        // +1 h
                    val endMs    = now + 2 * 60 * 60_000L    // +2 h
                    onConfirm(id, startMs, endMs)
                },
                enabled  = selectedModeId != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add planned activity")
            }
        }
    }
}
