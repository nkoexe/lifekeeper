package com.lifekeeper.app.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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

/**
 * Two consecutive entries of the same mode separated by less than this gap are
 * treated as a single continuous block (e.g. a brief app interruption).
 */
// REMOVED: UI-level merging has been replaced by DB-level merging (mergeAdjacentSameMode).
// The constant is kept only as a named tombstone so git blame is clear.
// private const val TOUCH_GAP_MS = 5_000L

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
    onResizeEntry    : (Long, Long?, Long?, Long?, Long?, Long?, UndoSnapshot?) -> Unit = { _, _, _, _, _, _, _ -> },
    onMoveEntry      : (Long, Long, Long, Long?, Long?, Long?, Long?, Long?, UndoSnapshot?) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onAddPlanned     : (Long, Long, Long) -> Unit = { _, _, _ -> },
    onDeleteAdjacent : (Long, Long, Long?, Long, Boolean, Long, Long?, Long) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onUndo           : () -> Unit = {},
    onClearUndo      : () -> Unit = {},
) {
    val scope             = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState         = rememberLazyListState()
    val density           = LocalDensity.current

    var hourHeightDp by rememberSaveable(
        stateSaver = Saver(save = { it.value }, restore = { Dp(it) }),
    ) { mutableStateOf(DEFAULT_HOUR_HEIGHT) }
    val currentHourHeight = rememberUpdatedState(hourHeightDp)

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

    // Build the list of day-midnight epoch values spanning the loaded window.
    val days = remember(uiState.windowStartMs, uiState.windowEndMs) {
        buildList {
            var d = uiState.windowStartMs
            while (d < uiState.windowEndMs) { add(d); d += DayViewModel.DAY_MS }
        }
    }
    val todayIdx = remember(days, uiState.todayStartMs) {
        days.indexOfFirst { it == uiState.todayStartMs }.coerceAtLeast(0)
    }

    // Scroll to today once on first load.
    var initialScrollDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(days.isNotEmpty()) {
        if (!initialScrollDone && days.isNotEmpty()) {
            listState.scrollToItem(todayIdx)
            initialScrollDone = true
        }
    }

    // Scroll to a specific time when an "other time" card is tapped in the detail sheet.
    LaunchedEffect(scrollToMs) {
        val target = scrollToMs ?: return@LaunchedEffect
        val targetDay = dayMidnight(target)
        val dayIdx = days.indexOfFirst { it == targetDay }
        if (dayIdx >= 0) {
            val hourHeightPx = with(density) { hourHeightDp.toPx() }
            val fracInDay    = ((target - targetDay).toFloat() / DayViewModel.DAY_MS).coerceIn(0f, 1f)
            val offsetPx     = (hourHeightPx * 24 * fracInDay - with(density) { 96.dp.toPx() }).toInt().coerceAtLeast(0)
            listState.animateScrollToItem(dayIdx, offsetPx)
        }
        scrollToMs = null
    }

    // Expand the loaded window when the user scrolls near an edge (3-day margin handled by VM).
    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val lastVisible  by remember { derivedStateOf {
        listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: firstVisible
    }}
    LaunchedEffect(firstVisible, lastVisible) {
        val first = days.getOrNull(firstVisible) ?: return@LaunchedEffect
        val last  = days.getOrNull(lastVisible)  ?: first
        onEnsureWindow(first, last + DayViewModel.DAY_MS)
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

    // Date-picker → scroll: set by the DatePickerDialog, consumed here.
    var goToDateMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(goToDateMs, days) {
        val target = goToDateMs ?: return@LaunchedEffect
        val idx = days.indexOfFirst { it == target }
        if (idx >= 0) {
            listState.animateScrollToItem(idx)
            goToDateMs = null
        } else {
            // Window doesn't cover this date yet — expand and retry on next recompose.
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

    // Is today currently visible in the list?
    val isTodayVisible by remember { derivedStateOf {
        listState.layoutInfo.visibleItemsInfo.any { it.index == todayIdx }
    }}

    // Pre-bucket entries by day midnight for O(1) lookup during composition.
    val entriesByDay = remember(uiState.entries, uiState.windowStartMs, uiState.windowEndMs) {
        val map = HashMap<Long, MutableList<TimeEntry>>(days.size * 2)
        for (day in days) map[day] = mutableListOf()
        for (entry in uiState.entries) map[dayMidnight(entry.startEpochMs)]?.add(entry)
        map
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

    Scaffold(
        contentWindowInsets  = WindowInsets(0),
        snackbarHost         = {
            // Anchor the snackbar just above the navigation bar (system gesture area /
            // button bar). On edge-to-edge builds the Scaffold content area already
            // starts below the status bar but the bottom inset belongs to us, so we
            // add navigationBarsPadding() here to lift the snackbar off the true edge.
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
                    title   = { Text("Timeline") },
                    actions = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Outlined.DateRange, contentDescription = "Go to date")
                        }
                        if (!isTodayVisible) {
                            IconButton(
                                onClick = { scope.launch { listState.animateScrollToItem(todayIdx) } },
                            ) {
                                Icon(Icons.Outlined.Today, contentDescription = "Jump to today")
                            }
                        }
                    },
                )
                // Mode filter chips — multi-select, horizontally scrollable.
                // Empty selection = show all (no "All" chip needed).
                if (uiState.allModes.isNotEmpty()) {
                    LazyRow(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
        // Pinch-zoom wrapper — intercepts 2-finger events; single-finger falls through to LazyColumn.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                                    val ratio    = distance / prevDistance
                                    val oldH     = currentHourHeight.value
                                    val newH     = (oldH * ratio).coerceIn(MIN_HOUR_HEIGHT, MAX_HOUR_HEIGHT)
                                    val actRatio = newH.value / oldH.value
                                    hourHeightDp = newH
                                    scope.launch { listState.scrollBy(centroidY * (actRatio - 1f)) }
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
            LazyColumn(
                state    = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    count       = days.size,
                    key         = { days[it] },
                    itemContent = { idx ->
                        val dayStart   = days[idx]
                        val isToday    = dayStart == uiState.todayStartMs
                        val dayEntries = entriesByDay[dayStart] ?: emptyList()
                        DaySection(
                            dayStartMs       = dayStart,
                            isToday          = isToday,
                            entries          = dayEntries,
                            modes            = uiState.modes,
                            nowMs            = uiState.nowMs,
                            hourHeightDp     = hourHeightDp,
                            onEventClick     = { block, dayBlocks ->
                                selectedEvent = SelectedEvent(block, dayBlocks)
                            },
                            onResize         = onResizeEntry,
                            onMove           = onMoveEntry,
                            onDeleteAdjacent = onDeleteAdjacent,
                        )
                    },
                )
            }
        }
    }
}

// ── Day section (date header + DayGrid) ──────────────────────────────────────

/**
 * One calendar day: a M3-styled date header row followed by a fixed-height
 * [DayGrid] canvas. The parent [LazyColumn] provides all scrolling — [DayGrid]
 * has no internal scroll, so the full 24 h slab is always laid out at its
 * natural height ([hourHeightDp] × 24).
 *
 * M3 Expressive design details:
 * - Today's row carries a subtle [primaryContainer] tint + a "Today" badge.
 * - Non-today dates use [onSurfaceVariant] to reduce visual noise.
 * - [HorizontalDivider] at the bottom of the header creates a clear boundary.
 */
@Composable
private fun DaySection(
    dayStartMs       : Long,
    isToday          : Boolean,
    entries          : List<TimeEntry>,
    modes            : Map<Long, Mode>,
    nowMs            : Long,
    hourHeightDp     : Dp,
    onEventClick     : (EventBlockData, List<EventBlockData>) -> Unit,
    onResize         : (Long, Long?, Long?, Long?, Long?, Long?, UndoSnapshot?) -> Unit,
    onMove           : (Long, Long, Long, Long?, Long?, Long?, Long?, Long?, UndoSnapshot?) -> Unit,
    onDeleteAdjacent : (Long, Long, Long?, Long, Boolean, Long, Long?, Long) -> Unit = { _, _, _, _, _, _, _, _ -> },
    modifier         : Modifier = Modifier,
) {
    val primaryColor   = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val onSvColor      = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier) {
        // Date header row.
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier              = Modifier
                .fillMaxWidth()
                .then(
                    if (isToday) Modifier.background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                    ) else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text       = remember(dayStartMs) {
                    SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(dayStartMs))
                },
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                color      = if (isToday) primaryColor else onSvColor,
                modifier   = Modifier.weight(1f),
            )
            if (isToday) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(primaryColor)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        text  = "Today",
                        style = MaterialTheme.typography.labelSmall,
                        color = onPrimaryColor,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        DayGrid(
            dayStartMs       = dayStartMs,
            entries          = entries,
            modes            = modes,
            nowMs            = nowMs,
            isToday          = isToday,
            hourHeightDp     = hourHeightDp,
            onEventClick     = onEventClick,
            onResize         = onResize,
            onMove           = onMove,
            onDeleteAdjacent = onDeleteAdjacent,
            modifier         = Modifier.fillMaxWidth(),
        )
    }
}

// ── Scrollable / zoomable grid ────────────────────────────────────────────────

/**
 * The core hourly-calendar canvas for a single day.
 *
 * Rendered as a fixed [hourHeightDp]×24 tall [Box] with no internal scroll —
 * the parent [LazyColumn] in [InfiniteCalendarScreen] owns all scrolling.
 * Pinch-zoom is handled one level above in the outer [Box] pointerInput.
 *
 * **Rendering layers** (bottom → top)
 * 1. [Canvas]: half-hour and full-hour grid lines.
 * 2. Absolute-offset [Text] composables: hour labels (01:00 … 23:00).
 * 3. [EventBlock] composables: one rounded rectangle per [TimeEntry].
 * 4. [NowLine]: current-time indicator, only when displaying today.
 */
@Composable
private fun DayGrid(
    dayStartMs       : Long,
    entries          : List<TimeEntry>,
    modes            : Map<Long, Mode>,
    nowMs            : Long,
    isToday          : Boolean,
    hourHeightDp     : Dp,
    onEventClick     : (EventBlockData, List<EventBlockData>) -> Unit = { _, _ -> },
    onResize         : (Long, Long?, Long?, Long?, Long?, Long?, UndoSnapshot?) -> Unit = { _, _, _, _, _, _, _ -> },
    onMove           : (Long, Long, Long, Long?, Long?, Long?, Long?, Long?, UndoSnapshot?) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onDeleteAdjacent : (Long, Long, Long?, Long, Boolean, Long, Long?, Long) -> Unit = { _, _, _, _, _, _, _, _ -> },
    modifier         : Modifier = Modifier,
) {
    val density = LocalDensity.current

    val totalHeight  = hourHeightDp * 24
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSvColor    = MaterialTheme.colorScheme.onSurfaceVariant
    val errorColor   = MaterialTheme.colorScheme.error

    // Memoised event data: parse colors & clamp timestamps once per entries change.
    val eventBlocks = remember(entries, modes, nowMs) {
        buildEventBlocks(entries, modes, nowMs)
    }

    // ── Edit state ────────────────────────────────────────────────────────────
    // Selected block index (-1 = none). Long-press activates; tap on selected deselects.
    var selectedBlockIdx by remember { mutableIntStateOf(-1) }
    // Stable DB id used to re-find the selected block after Room emits a DB update.
    var selectedEntryId  by remember { mutableStateOf<Long?>(null) }
    // Current drag gesture in progress.
    var dragMode         by remember { mutableStateOf(DragMode.NONE) }
    // Live boundary overrides for the selected block during drag.
    var liveStartMs      by remember { mutableStateOf<Long?>(null) }
    var liveEndMs        by remember { mutableStateOf<Long?>(null) }
    // Primary adjacent live boundary (RESIZE_TOP → prev block's end; RESIZE_BOTTOM → next block's start;
    // MOVE → prev block's end).
    var liveAdjIdx       by remember { mutableIntStateOf(-1) }
    var liveAdjStartMs   by remember { mutableStateOf<Long?>(null) }
    var liveAdjEndMs     by remember { mutableStateOf<Long?>(null) }
    // Secondary adjacent live boundary (MOVE only → next block's start).
    var liveAdj2Idx      by remember { mutableIntStateOf(-1) }
    var liveAdj2StartMs  by remember { mutableStateOf<Long?>(null) }
    var liveAdj2EndMs    by remember { mutableStateOf<Long?>(null) }
    // Accumulated pointer Y for the active drag gesture; reset per gesture.
    var dragAccumPx      by remember { mutableFloatStateOf(0f) }

    // Delete-warning state: which adjacent block (if any) is about to be absorbed.
    // Set during drag when adjacent block shrinks below DELETE_THRESHOLD_MS.
    var willDeletePrev   by remember { mutableStateOf(false) }
    var willDeleteNext   by remember { mutableStateOf(false) }

    /** Clears in-progress drag state while keeping the block selected for follow-up edits. */
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

    /** Deselects the current block and clears all drag state. */
    fun clearEditState() {
        selectedBlockIdx = -1
        selectedEntryId  = null
        clearDragState()
    }

    // After a DB round-trip (Room emits new list): clear drag state but keep selection.
    // Re-find the selected block by its stable entryId so the index stays correct
    // even if blocks shift due to adjacent-boundary updates.
    LaunchedEffect(eventBlocks) {
        clearDragState()
        val prevId = selectedEntryId ?: return@LaunchedEffect
        val newIdx = eventBlocks.indexOfFirst { it.entryId == prevId }
        if (newIdx >= 0) {
            selectedBlockIdx = newIdx
        } else {
            // Block no longer present (external deletion); deselect.
            selectedBlockIdx = -1
            selectedEntryId  = null
        }
    }

    // ms-per-pixel derived from current zoom level.
    val msPerPx by remember(totalHeight) {
        derivedStateOf {
            val totalPx = with(density) { totalHeight.toPx() }
            if (totalPx > 0f) DayViewModel.DAY_MS.toFloat() / totalPx else 1f
        }
    }
    val nowMsRef = rememberUpdatedState(nowMs)

    val primaryContainerColor   = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = modifier
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
                // Vertical separator — right edge of the time gutter.
                drawLine(
                    color       = outlineColor.copy(alpha = 0.30f),
                    start       = Offset(timeLeft, 0f),
                    end         = Offset(timeLeft, size.height),
                    strokeWidth = 0.5.dp.toPx(),
                )
            }

            // ── Layer 2: hour labels ──────────────────────────────────────
            // Stride adapts to zoom level so labels never overlap.
            val labelStride = when {
                hourHeightDp < 12.dp -> 6
                hourHeightDp < 22.dp -> 4
                hourHeightDp < 36.dp -> 2
                else                 -> 1
            }
            for (h in 1..23) {
                if (h % labelStride != 0) continue
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
            eventBlocks.forEachIndexed { idx, block ->
                val isSel = idx == selectedBlockIdx
                // Apply live boundary overrides during any active drag:
                //   • selected block     → liveStartMs / liveEndMs
                //   • primary adjacent   → liveAdjStartMs / liveAdjEndMs
                //   • secondary adjacent → liveAdj2StartMs / liveAdj2EndMs (MOVE only)
                val renderBlock = when {
                    isSel               -> block.copy(
                        startMs = liveStartMs  ?: block.startMs,
                        endMs   = liveEndMs    ?: block.endMs,
                    )
                    idx == liveAdjIdx   -> block.copy(
                        startMs = liveAdjStartMs  ?: block.startMs,
                        endMs   = liveAdjEndMs    ?: block.endMs,
                    )
                    idx == liveAdj2Idx  -> block.copy(
                        startMs = liveAdj2StartMs ?: block.startMs,
                        endMs   = liveAdj2EndMs   ?: block.endMs,
                    )
                    else                -> block
                }
                val selIdx = selectedBlockIdx
                val isAboutToDelete = when {
                    willDeletePrev && selIdx >= 1 && idx == selIdx - 1 -> true
                    willDeleteNext && selIdx >= 0 && idx == selIdx + 1 -> true
                    else -> false
                }
                EventBlock(
                    block           = renderBlock,
                    dayStartMs      = dayStartMs,
                    hourHeightDp    = hourHeightDp,
                    totalHeight     = totalHeight,
                    isSelected      = isSel,
                    isAboutToDelete = isAboutToDelete,
                    onClick         = {
                        if (isSel) {
                            // Tap on already-selected block → deselect.
                            clearEditState()
                        } else {
                            onEventClick(block, eventBlocks)
                        }
                    },
                    onLongPress     = if (!isSel) {
                        {
                            selectedBlockIdx = idx
                            selectedEntryId  = block.entryId
                            clearDragState()
                        }
                    } else {
                        // Already selected — absorb re-fire.
                        {}
                    },
                )
            }

            // ── Trash-delete indicator ──────────────────────────────────────────
            // A small circle with a delete icon sits at the boundary between the
            // selected block and the adjacent block that is about to be absorbed.
            // Positioned at the left edge (straddling the time gutter) so it is
            // always visible regardless of block height.
            run {
                val boundaryMs: Long = when {
                    willDeletePrev -> liveStartMs ?: return@run
                    willDeleteNext -> liveEndMs   ?: return@run
                    else           -> return@run
                }
                val frac     = ((boundaryMs - dayStartMs).toFloat() / DayViewModel.DAY_MS).coerceIn(0f, 1f)
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

            // ── Layer 4: edit overlay (top handle · body drag · bottom handle) ──
            // Activated whenever a block is selected. Each of the three zones uses
            // a stable pointerInput key (selIdx, dayStartMs) so the gesture handler
            // is NOT restarted between frames. rememberUpdatedState keeps all captured
            // block/neighbour data fresh without restarting the gesture.
            if (selectedBlockIdx in eventBlocks.indices) {
                val selIdx    = selectedBlockIdx
                val rawBlock  = eventBlocks[selIdx]
                val prevBlock = if (selIdx > 0) eventBlocks[selIdx - 1] else null
                val nextBlock = if (selIdx < eventBlocks.lastIndex) eventBlocks[selIdx + 1] else null

                // Always-fresh snapshots readable from inside pointerInput lambdas.
                val rawBlockRef   = rememberUpdatedState(rawBlock)
                val prevBlockRef  = rememberUpdatedState(prevBlock)
                val nextBlockRef  = rememberUpdatedState(nextBlock)
                val msPerPxRef    = rememberUpdatedState(msPerPx)
                val dayStartMsRef = rememberUpdatedState(dayStartMs)

                val effStart   = liveStartMs ?: rawBlock.startMs
                val effEnd     = liveEndMs   ?: rawBlock.endMs
                val dayMs      = DayViewModel.DAY_MS.toFloat()
                val topFrac    = ((effStart - dayStartMs).toFloat() / dayMs).coerceIn(0f, 1f)
                val endFrac    = ((effEnd   - dayStartMs).toFloat() / dayMs).coerceIn(0f, 1f)
                val blockTopDp = totalHeight * topFrac + EVENT_GAP_HALF_DP
                val blockBotDp = totalHeight * endFrac - EVENT_GAP_HALF_DP

                // ── TOP HANDLE ── visible when no MOVE drag is active ─────
                if (dragMode != DragMode.MOVE) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .padding(start = TIME_COLUMN_WIDTH + EVENT_H_PADDING, end = EVENT_H_PADDING)
                            .fillMaxWidth()
                            .offset(y = blockTopDp - HANDLE_TOUCH_H / 2)
                            .height(HANDLE_TOUCH_H)
                            .pointerInput(selIdx, dayStartMs) {
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
                                        val dsMs      = dayStartMsRef.value
                                        val candidate = rb.startMs + (dragAccumPx * msPerPxRef.value).toLong()
                                        // Own block keeps ≥ MIN_BLOCK_MS.
                                        val ceiling   = rb.endMs - MIN_BLOCK_MS
                                        // Prev block can shrink to half of DELETE_THRESHOLD_MS so the trash
                                        // warning is reachable and always has a sliver to render on.
                                        val floor     = prevBlk?.let { it.startMs + DELETE_THRESHOLD_MS / 2 } ?: dsMs
                                        // maxOf guards against degenerate range when blocks are very close.
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
                                                prevBlk.startMs, prevBlk.endMs, prevBlk.modeId,
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
                                        // Keep block selected so the user can chain another edit.
                                        clearDragState()
                                    },
                                    onDragCancel = { clearDragState() },
                                )
                            },
                    ) {
                        ResizeHandlePill(color = primaryContainerColor, onColor = onPrimaryContainerColor)
                    }
                }

                // ── BODY DRAG ZONE ── move the whole block ────────────────
                // Only for single-entry blocks (merged multi-entry blocks cannot
                // be safely moved by shifting only two DB rows).
                // The zone sits between the two handle touch-target strips so
                // handle drags always take priority over body drags.
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
                                .pointerInput(selIdx, dayStartMs) {
                                    detectDragGestures(
                                        onDragStart = {
                                            dragMode    = DragMode.MOVE
                                            dragAccumPx = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragAccumPx += dragAmount.y
                                            val rb      = rawBlockRef.value
                                            val prevBlk = prevBlockRef.value
                                            val nextBlk = nextBlockRef.value
                                            val dsMs    = dayStartMsRef.value
                                            val deltaMs = (dragAccumPx * msPerPxRef.value).toLong()
                                            val minStart = (prevBlk?.let { it.startMs + DELETE_THRESHOLD_MS / 2 }
                                                ?: dsMs).coerceAtLeast(dsMs)
                                            // Full-move: preserve duration and shift both ends together.
                                            // For open (active) entries rb.endMs is the visual end
                                            // (nowMs or MIN_VISUAL_DURATION floor).  The visual end
                                            // becomes the literal new endEpochMs after the move;
                                            // prevEndMs is stored as null in UndoSnapshot so undo
                                            // can restore the entry to its open state.
                                            val duration = rb.endMs - rb.startMs
                                            val maxEnd   = nextBlk?.let { it.endMs - DELETE_THRESHOLD_MS / 2 }
                                                ?: (dsMs + 30L * DayViewModel.DAY_MS)
                                            val maxStart = maxEnd - duration
                                            val newStart = (rb.startMs + deltaMs).coerceIn(minStart, maxOf(minStart, maxStart))
                                            val newEnd   = newStart + duration
                                            liveStartMs  = newStart
                                            liveEndMs    = newEnd
                                            if (prevBlk != null) {
                                                liveAdjIdx = selIdx - 1; liveAdjEndMs = newStart; liveAdjStartMs = null
                                                willDeletePrev = (newStart - prevBlk.startMs) < DELETE_THRESHOLD_MS
                                            }
                                            if (nextBlk != null) {
                                                liveAdj2Idx = selIdx + 1; liveAdj2StartMs = newEnd; liveAdj2EndMs = null
                                                willDeleteNext = (nextBlk.endMs - newEnd) < DELETE_THRESHOLD_MS
                                            }
                                            // If both approach threshold, prefer whichever is tighter.
                                            if (willDeletePrev && willDeleteNext) {
                                                val prevRemaining = newStart - (prevBlk?.startMs ?: Long.MAX_VALUE)
                                                val nextRemaining = (nextBlk?.endMs ?: Long.MAX_VALUE) - newEnd
                                                if (prevRemaining <= nextRemaining) willDeleteNext = false
                                                else willDeletePrev = false
                                            }
                                        },
                                        onDragEnd = {
                                            val rb      = rawBlockRef.value
                                            val prevBlk = prevBlockRef.value
                                            val nextBlk = nextBlockRef.value
                                            val newStart = liveStartMs
                                            if (newStart != null && newStart != rb.startMs) {
                                                if (willDeletePrev && prevBlk != null) {
                                                    onDeleteAdjacent(
                                                        rb.entryId, rb.startMs, rb.endMs,
                                                        prevBlk.entryId, false,
                                                        prevBlk.startMs, prevBlk.endMs, prevBlk.modeId,
                                                    )
                                                } else if (willDeleteNext && nextBlk != null) {
                                                    onDeleteAdjacent(
                                                        rb.entryId, rb.startMs, rb.endMs,
                                                        nextBlk.entryId, true,
                                                        nextBlk.startMs, nextBlk.endMs, nextBlk.modeId,
                                                    )
                                                } else {
                                                    val newEnd = liveEndMs
                                                    if (newEnd != null) {
                                                        // prevEndMs = null when the entry was open; undo
                                                        // restores it to open via moveEntry(newEndMs = null).
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

                // ── BOTTOM HANDLE ── visible for all entries except during MOVE ──
                if (dragMode != DragMode.MOVE) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .padding(start = TIME_COLUMN_WIDTH + EVENT_H_PADDING, end = EVENT_H_PADDING)
                            .fillMaxWidth()
                            .offset(y = blockBotDp - HANDLE_TOUCH_H / 2)
                            .height(HANDLE_TOUCH_H)
                            .pointerInput(selIdx, dayStartMs) {
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
                                        val dsMs      = dayStartMsRef.value
                                        val candidate = rb.endMs + (dragAccumPx * msPerPxRef.value).toLong()
                                        // Own block keeps ≥ MIN_BLOCK_MS.
                                        val floor     = rb.startMs + MIN_BLOCK_MS
                                        // Next block can shrink to half of DELETE_THRESHOLD_MS so the trash
                                        // warning is reachable and always has a sliver to render on.
                                        // For open (active) entries with no next block, allow planning up to
                                        // 30 days into the future from the day start.
                                        val ceiling   = nextBlk?.let { it.endMs - DELETE_THRESHOLD_MS / 2 }
                                            ?: if (rb.isOpen) (dsMs + 30L * DayViewModel.DAY_MS)
                                               else (dsMs + DayViewModel.DAY_MS)
                                        // maxOf guards against degenerate range when blocks are very close.
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
                                                nextBlk.startMs, nextBlk.endMs, nextBlk.modeId,
                                            )
                                        } else if (newEnd != null && newEnd != rb.endMs) {
                                            // prevEndMs = null when the entry was open so undo can
                                            // restore it to its open state (resizeEntry + reopenEntry).
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
        // Raw visual end: nowMs for open entries, stored value for closed.
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
    dayStartMs      : Long,
    hourHeightDp    : Dp,
    totalHeight     : Dp,
    onClick         : () -> Unit = {},
    onLongPress     : () -> Unit = {},
    isSelected      : Boolean    = false,
    isAboutToDelete : Boolean    = false,
    startPadding    : Dp         = TIME_COLUMN_WIDTH + EVENT_H_PADDING,
) {
    val haptic   = LocalHapticFeedback.current
    val dayMs    = DayViewModel.DAY_MS.toFloat()
    val topFrac  = ((block.startMs - dayStartMs).toFloat() / dayMs).coerceIn(0f, 1f)
    val endFrac  = ((block.endMs   - dayStartMs).toFloat() / dayMs).coerceIn(0f, 1f)

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
                    onTap      = { onClick() },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
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
