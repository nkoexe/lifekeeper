package com.lifekeeper.app.ui.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import com.lifekeeper.app.data.model.DailySummary
import com.lifekeeper.app.data.model.Mode
import com.lifekeeper.app.data.model.ModeSummary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun StatsScreen() {
    val app = LocalContext.current.applicationContext as LifekeeperApp
    val vm: StatsViewModel = viewModel(factory = StatsViewModel.factory(app))
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    StatsContent(
        uiState           = uiState,
        onSetRangeMode    = vm::setRangeMode,
        onSetRollingDays  = vm::setRollingDays,
        onSetFixedAnchor  = vm::setFixedAnchor,
        anchorAsUtcMs     = vm::anchorAsUtcMs,
    )
}

// ── Main content ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsContent(
    uiState          : StatsUiState,
    onSetRangeMode   : (StatsRangeMode) -> Unit,
    onSetRollingDays : (Int) -> Unit,
    onSetFixedAnchor : (Long) -> Unit,
    anchorAsUtcMs    : () -> Long,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Stats") }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ── Range picker ──────────────────────────────────────────────
            item {
                RangePicker(
                    rangeMode       = uiState.rangeMode,
                    rollingDays     = uiState.rollingDays,
                    windowLabel     = uiState.windowLabel,
                    anchorAsUtcMs   = anchorAsUtcMs,
                    onSetMode       = onSetRangeMode,
                    onSetRolling    = onSetRollingDays,
                    onSetAnchor     = onSetFixedAnchor,
                    modifier        = Modifier.padding(top = 8.dp),
                )
            }

            // ── Donut chart ───────────────────────────────────────────────
            item {
                SectionCard(title = "Breakdown") {
                    DonutChart(
                        modeSummaries = uiState.modeSummaries,
                        totalMs       = uiState.totalMs,
                        modifier      = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    )
                    if (uiState.modeSummaries.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        uiState.modeSummaries.forEach { summary ->
                            LegendRow(
                                label      = summary.modeName,
                                colorHex   = summary.colorHex,
                                durationMs = summary.totalMs,
                                totalMs    = uiState.totalMs,
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color    = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(Modifier.width(20.dp))
                            Text(
                                text       = "Total",
                                modifier   = Modifier.weight(1f),
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text       = formatDuration(uiState.totalMs),
                                style      = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color      = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            // ── Stacked bar chart ─────────────────────────────────────────
            item {
                SectionCard(title = "Daily totals") {
                    StackedBarChart(
                        dailySummaries = uiState.dailySummaries,
                        modes          = uiState.modes,
                        modifier       = Modifier.fillMaxWidth(),
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ── Range picker ──────────────────────────────────────────────────────────────

/**
 * A two-part picker:
 * - Left: ExposedDropdownMenuBox to choose [StatsRangeMode].
 * - Right (ROLLING): FilterChip pills for 1d / 7d / 30d / 3mo / 6mo / 1yr.
 * - Right (Fixed modes): a TextButton showing [windowLabel] that opens a DatePickerDialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePicker(
    rangeMode     : StatsRangeMode,
    rollingDays   : Int,
    windowLabel   : String,
    anchorAsUtcMs : () -> Long,
    onSetMode     : (StatsRangeMode) -> Unit,
    onSetRolling  : (Int) -> Unit,
    onSetAnchor   : (Long) -> Unit,
    modifier      : Modifier = Modifier,
) {
    var menuExpanded    by remember { mutableStateOf(false) }
    var showDatePicker  by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Mode dropdown ─────────────────────────────────────────────
            ExposedDropdownMenuBox(
                expanded  = menuExpanded,
                onExpandedChange = { menuExpanded = it },
            ) {
                FilterChip(
                    selected     = false,
                    // Do NOT set onClick here — menuAnchor(PrimaryNotEditable) already
                    // wires the tap to onExpandedChange. Having both would double-toggle
                    // and leave the menu permanently closed.
                    onClick      = {},
                    label        = { Text(rangeMode.label) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuExpanded) },
                    modifier     = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded  = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    StatsRangeMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text    = { Text(mode.label) },
                            onClick = { onSetMode(mode); menuExpanded = false },
                        )
                    }
                }
            }

            // ── Pills (ROLLING) or date button (Fixed) ────────────────────
            if (rangeMode == StatsRangeMode.ROLLING) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(ROLLING_DAYS.size) { i ->
                        FilterChip(
                            selected = rollingDays == ROLLING_DAYS[i],
                            onClick  = { onSetRolling(ROLLING_DAYS[i]) },
                            label    = { Text(ROLLING_LABELS[i]) },
                        )
                    }
                }
            } else {
                TextButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text  = windowLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    // ── DatePickerDialog (fixed modes) ────────────────────────────────────────
    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = anchorAsUtcMs(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let(onSetAnchor)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = dpState)
        }
    }
}

// ── Animation easing ─────────────────────────────────────────────────────────

/** M3 "Emphasized Decelerate" easing — recommended for elements entering the screen. */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
/** M3 "Emphasized Accelerate" easing — recommended for elements leaving the screen. */
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

// ── Donut chart ───────────────────────────────────────────────────────────────

private const val DONUT_STROKE_DP = 28f
private const val DONUT_GAP_DEG   = 2f

/**
 * An animated ring/donut chart.
 * - Each arc occupies a fraction of 360° proportional to its [ModeSummary.totalMs].
 * - Small gaps separate adjacent segments when more than one mode is present.
 * - Tapping a segment highlights it (spring-animated wider stroke; others fade).
 * - Tapping the highlighted segment again deselects it.
 * - Center overlay cross-fades between the tapped segment label and empty.
 * - When [totalMs] == 0 a neutral ring with "No data" is shown.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DonutChart(
    modeSummaries : List<ModeSummary>,
    totalMs       : Long,
    modifier      : Modifier = Modifier,
) {
    val animProgress = remember { Animatable(0f) }
    // Key on mode IDs only — duration ticks must not restart the draw-in animation.
    val modeIds = modeSummaries.map { it.modeId }
    LaunchedEffect(modeIds) {
        animProgress.snapTo(0f)
        // EmphasizedDecelerate: fast start, decelerates smoothly into the end position.
        animProgress.animateTo(1f, tween(durationMillis = 500, easing = EmphasizedDecelerate))
    }

    // Key on modeIds (stable) — NOT modeSummaries, which changes every second as
    // live-entry totalMs ticks. Using modeSummaries would reset the highlight on
    // every recomposition caused by time ticking.
    var highlightedIdx by remember(modeIds) { mutableStateOf<Int?>(null) }

    val motionScheme = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val strokeDp = DONUT_STROKE_DP.dp
    val strokePx = with(density) { strokeDp.toPx() }
    val highlightStrokePx = strokePx * 1.3f

    val n = modeSummaries.size

    // Per-segment spring-animated alpha and stroke width.
    // Recreated when the segment set changes (modeIds key), stable between ticks.
    val segmentAlphas  = remember(modeIds) { List(n) { Animatable(1f) } }
    val segmentStrokes = remember(modeIds) { List(n) { Animatable(strokePx) } }

    // Drive all segment animations whenever the highlighted index changes.
    LaunchedEffect(highlightedIdx) {
        segmentAlphas.forEachIndexed { i, anim ->
            launch {
                anim.animateTo(
                    targetValue = when {
                        highlightedIdx == null -> 1f
                        i == highlightedIdx   -> 1f
                        else                  -> 0.35f
                    },
                    animationSpec = motionScheme.fastEffectsSpec(),
                )
            }
        }
        segmentStrokes.forEachIndexed { i, anim ->
            launch {
                anim.animateTo(
                    targetValue   = if (i == highlightedIdx) highlightStrokePx else strokePx,
                    animationSpec = motionScheme.fastSpatialSpec(),
                )
            }
        }
    }

    // Pre-compute segment angles (0f = top, clockwise) so tap hit-test matches draw.
    val totalSweepable = 360f - if (n > 1) DONUT_GAP_DEG * n else 0f
    val segmentSweeps: List<Float> = modeSummaries.map { s ->
        if (totalMs > 0L) (s.totalMs.toFloat() / totalMs) * totalSweepable else 0f
    }
    val segmentStarts: List<Float> = buildList {
        var cursor = -90f  // Start at 12 o'clock
        segmentSweeps.forEachIndexed { i, sweep ->
            add(cursor)
            cursor += sweep + if (n > 1) DONUT_GAP_DEG else 0f
        }
    }

    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceColor      = MaterialTheme.colorScheme.onSurface

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(modeIds) {
                    detectTapGestures { offset ->
                        val cx = size.width  / 2f
                        val cy = size.height / 2f
                        val outerR = (min(size.width, size.height) / 2f) - strokePx / 2f
                        val innerR = outerR - strokePx

                        val dx   = offset.x - cx
                        val dy   = offset.y - cy
                        val dist = sqrt(dx * dx + dy * dy)

                        // Must be within the donut ring band.
                        if (dist < innerR || dist > outerR + strokePx * 0.15f) return@detectTapGestures

                        // atan2 starts at 3 o'clock. Shift so 12 o'clock = 0°, clockwise positive.
                        val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        val normalized = (angleDeg + 90f + 360f) % 360f

                        // Walk segments to find which one was tapped.
                        val hit = segmentStarts.indices.firstOrNull { i ->
                            val start = (segmentStarts[i] + 90f + 360f) % 360f
                            val end   = (start + segmentSweeps[i] + 360f) % 360f
                            if (end >= start) normalized in start..end
                            else normalized >= start || normalized <= end   // wraps around 360°
                        }

                        highlightedIdx = if (hit != null && hit == highlightedIdx) null else hit
                    }
                },
        ) {
            val cx      = size.width  / 2f
            val cy      = size.height / 2f
            val outerR  = (min(size.width, size.height) / 2f) - highlightStrokePx / 2f
            val topLeft = Offset(cx - outerR, cy - outerR)
            val arcSize = Size(outerR * 2, outerR * 2)

            // Background ring.
            drawArc(
                color       = outlineVariantColor.copy(alpha = 0.25f),
                startAngle  = -90f,
                sweepAngle  = 360f,
                useCenter   = false,
                topLeft     = topLeft,
                size        = arcSize,
                style       = Stroke(width = strokePx, cap = StrokeCap.Butt),
            )

            val progress = animProgress.value

            if (totalMs == 0L || modeSummaries.isEmpty()) {
                // No data — draw centered text.
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        color       = onSurfaceColor.copy(alpha = 0.5f).toArgb()
                        textSize    = 36f
                        typeface    = android.graphics.Typeface.DEFAULT
                        textAlign   = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    canvas.nativeCanvas.drawText("No data", cx, cy + paint.textSize / 3f, paint)
                }
                return@Canvas
            }

            // Draw each segment using its individually spring-animated alpha and stroke.
            modeSummaries.forEachIndexed { i, summary ->
                val color = runCatching {
                    Color(android.graphics.Color.parseColor(summary.colorHex))
                }.getOrDefault(Color.Gray)

                drawArc(
                    color      = color.copy(alpha = segmentAlphas[i].value),
                    startAngle = segmentStarts[i],
                    sweepAngle = segmentSweeps[i] * progress,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = Stroke(width = segmentStrokes[i].value, cap = StrokeCap.Butt),
                )
            }
        }

        // ── Center label overlay ──────────────────────────────────────────────
        // Fade the label in/out when a segment is tapped.  Using animateFloatAsState
        // (simple alpha) avoids any layout-size fighting that AnimatedContent can
        // cause when transitioning between content of different heights.
        val labelAlpha by animateFloatAsState(
            targetValue    = if (highlightedIdx != null) 1f else 0f,
            animationSpec  = tween(200, easing = EmphasizedDecelerate),
            label          = "donut-label-alpha",
        )
        val shownIdx = highlightedIdx  // snapshot so content doesn't flicker on hide
        if (shownIdx != null && shownIdx < modeSummaries.size) {
            val s = modeSummaries[shownIdx]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
                    .alpha(labelAlpha),
            ) {
                Text(
                    text       = formatDuration(s.totalMs),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text     = s.modeName,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Stacked bar chart ─────────────────────────────────────────────────────────

private val BAR_CHART_HEIGHT = 180.dp
private val BAR_ITEM_WIDTH   = 32.dp
private val Y_AXIS_WIDTH     = 44.dp

// Nice max-tick boundaries for the Y-axis (in milliseconds).
private val Y_TICK_CANDIDATES = listOf(
    15L * 60_000,    // 15 min
    30L * 60_000,    // 30 min
    60L * 60_000,    // 1 h
    2  * 3_600_000L, // 2 h
    4  * 3_600_000L, // 4 h
    6  * 3_600_000L, // 6 h
    8  * 3_600_000L, // 8 h
    12 * 3_600_000L, // 12 h
    24 * 3_600_000L, // 24 h
)

private val dayLabelFmt = SimpleDateFormat("E", Locale.getDefault())

/**
 * A horizontally-scrollable stacked bar chart with a fixed Y-axis.
 * Bars animate in on first appearance / data change.
 * Tapping a bar shows a [SelectedDayDetail] card below the chart.
 */
@Composable
private fun StackedBarChart(
    dailySummaries : List<DailySummary>,
    modes          : List<Mode>,
    modifier       : Modifier = Modifier,
) {
    if (dailySummaries.isEmpty()) {
        Box(
            modifier = modifier.height(60.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("No data", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val animProgress = remember { Animatable(0f) }
    // Key on day timestamps only — duration ticks must not restart the animation.
    val dayStarts = dailySummaries.map { it.dayStartMs }
    LaunchedEffect(dayStarts) {
        animProgress.snapTo(0f)
        // EmphasizedDecelerate: bars grow quickly then settle, matching M3 enter guidance.
        animProgress.animateTo(1f, tween(durationMillis = 600, easing = EmphasizedDecelerate))
    }

    // Key on dayStarts (stable) — NOT dailySummaries, which changes every second
    // as durationsMs ticks. Using dailySummaries would reset selection every second.
    var selectedIdx by remember(dayStarts) { mutableIntStateOf(-1) }

    val modeMap  = remember(modes) { modes.associateBy { it.id } }
    val maxDayMs = dailySummaries.maxOfOrNull { it.totalMs }.let { if (it == null || it == 0L) 1L else it }

    // Pick a nice Y-axis tick interval.
    val tickMs = Y_TICK_CANDIDATES.firstOrNull { maxDayMs / it <= 5 } ?: (24 * 3_600_000L)
    val yMax   = ((maxDayMs + tickMs - 1) / tickMs) * tickMs  // round up to next tick

    val density = LocalDensity.current
    val barHeightPx  = with(density) { BAR_CHART_HEIGHT.toPx() }
    val yAxisWidthPx = with(density) { Y_AXIS_WIDTH.toPx() }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline   = MaterialTheme.colorScheme.outlineVariant
    val selectedBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    val listState  = rememberLazyListState()

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Y-axis (fixed, does not scroll)
            Canvas(
                modifier = Modifier
                    .width(Y_AXIS_WIDTH)
                    .height(BAR_CHART_HEIGHT),
            ) {
                val tickCount = (yMax / tickMs).toInt()
                for (t in 0..tickCount) {
                    val frac = t.toFloat() / tickCount
                    val y    = barHeightPx * (1f - frac)
                    val labelMs = t * tickMs
                    val label  = formatShortDuration(labelMs)

                    // Tick line
                    drawLine(
                        color       = outline,
                        start       = Offset(yAxisWidthPx - 6f, y),
                        end         = Offset(yAxisWidthPx, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            color       = onSurface.copy(alpha = 0.6f).toArgb()
                            textSize    = 9.dp.toPx()
                            isAntiAlias = true
                            textAlign   = android.graphics.Paint.Align.RIGHT
                        }
                        canvas.nativeCanvas.drawText(label, yAxisWidthPx - 8f, y + paint.textSize / 3f, paint)
                    }
                }
            }

            // Scrollable bars
            LazyRow(
                state  = listState,
                modifier = Modifier.weight(1f),
            ) {
                items(dailySummaries.size) { i ->
                    val day = dailySummaries[i]
                    val isSelected = selectedIdx == i

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(BAR_ITEM_WIDTH)
                            .background(if (isSelected) selectedBg else Color.Transparent)
                            .clickable { selectedIdx = if (selectedIdx == i) -1 else i },
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(BAR_CHART_HEIGHT),
                        ) {
                            val barW   = size.width * 0.65f
                            val barX   = (size.width - barW) / 2f
                            val progress = animProgress.value

                            // Background guide lines at each tick
                            val tickCount = (yMax / tickMs).toInt()
                            for (t in 0..tickCount) {
                                val frac = t.toFloat() / tickCount
                                val gy   = barHeightPx * (1f - frac)
                                drawLine(outline.copy(alpha = 0.3f),
                                    Offset(0f, gy), Offset(size.width, gy), 0.5.dp.toPx())
                            }

                            // Segments (bottom to top, sorted by modeId for visual stability)
                            var accum = 0L
                            day.durationsMs.entries
                                .sortedBy { it.key }
                                .forEach { (modeId, ms) ->
                                    val modeColor = modeMap[modeId]?.colorHex?.let {
                                        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
                                    } ?: Color.Gray

                                    val normBottom = (accum.toFloat() / yMax)     * progress
                                    val normTop    = ((accum + ms).toFloat() / yMax) * progress
                                    val segBottom  = barHeightPx * (1f - normBottom)
                                    val segTop     = barHeightPx * (1f - normTop)

                                    drawRect(
                                        color   = modeColor,
                                        topLeft = Offset(barX, segTop),
                                        size    = Size(barW, (segBottom - segTop).coerceAtLeast(0f)),
                                    )
                                    accum += ms
                                }

                            // Selection outline
                            if (isSelected) {
                                val totalFrac = (day.totalMs.toFloat() / yMax * progress).coerceIn(0f, 1f)
                                val totalTopY = barHeightPx * (1f - totalFrac)
                                drawRect(
                                    color       = onSurface.copy(alpha = 0.7f),
                                    topLeft     = Offset(barX, totalTopY),
                                    size        = Size(barW, (barHeightPx - totalTopY).coerceAtLeast(0f)),
                                    style       = Stroke(width = 1.5.dp.toPx()),
                                )
                            }
                        }

                        // X-axis label
                        Text(
                            text  = dayLabelFmt.format(Date(day.dayStartMs)).take(1),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }

        // Selected day detail card
        AnimatedVisibility(
            visible = selectedIdx >= 0,
            enter   = expandVertically(tween(300, easing = EmphasizedDecelerate)) +
                      fadeIn(tween(250, easing = EmphasizedDecelerate)),
            exit    = shrinkVertically(tween(200, easing = EmphasizedAccelerate)) +
                      fadeOut(tween(150, easing = EmphasizedAccelerate)),
        ) {
            val idx = selectedIdx
            if (idx >= 0 && idx < dailySummaries.size) {
                SelectedDayDetail(
                    day     = dailySummaries[idx],
                    modeMap = modeMap,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SelectedDayDetail(
    day     : DailySummary,
    modeMap : Map<Long, Mode>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape     = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val dateFmt = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
            Text(
                text  = dateFmt.format(Date(day.dayStartMs)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text  = "Total: ${formatDuration(day.totalMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (day.durationsMs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                day.durationsMs.entries
                    .sortedByDescending { it.value }
                    .forEach { (modeId, ms) ->
                        val m    = modeMap[modeId]
                        val clr  = m?.colorHex?.let {
                            runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
                        } ?: Color.Gray
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(clr))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text     = m?.name ?: "Unknown",
                                style    = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text  = formatDuration(ms),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
            }
        }
    }
}

// ── Section card ──────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title  : String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

// ── Legend row ────────────────────────────────────────────────────────────────

@Composable
private fun LegendRow(
    label      : String,
    colorHex   : String,
    durationMs : Long,
    totalMs    : Long,
) {
    val color = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
        .getOrDefault(MaterialTheme.colorScheme.primary)
    val pct   = if (totalMs > 0) (durationMs * 100f / totalMs) else 0f

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            text     = label,
            modifier = Modifier.weight(1f),
            style    = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text  = "${formatDuration(durationMs)}  (%.0f%%)".format(pct),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Duration formatting helpers ───────────────────────────────────────────────

/** Returns e.g. "2h 04m" or "45m" or "30s". */
private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0m"
    val h = ms / 3_600_000L
    val m = (ms % 3_600_000L) / 60_000L
    val s = (ms % 60_000L) / 1_000L
    return when {
        h > 0  -> "%dh %02dm".format(h, m)
        m > 0  -> "%dm".format(m)
        else   -> "%ds".format(s)
    }
}

/** Compact ticker label for Y-axis: "30m", "1h", "4h", etc. */
private fun formatShortDuration(ms: Long): String {
    if (ms == 0L) return "0"
    val h = ms / 3_600_000L
    val m = (ms % 3_600_000L) / 60_000L
    return when {
        h > 0 && m == 0L -> "${h}h"
        h > 0            -> "${h}h${m}m"
        else             -> "${m}m"
    }
}
