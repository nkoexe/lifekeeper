package com.lifekeeper.app.ui.mode

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifekeeper.app.LifekeeperApp
import com.lifekeeper.app.R
import com.lifekeeper.app.data.model.Mode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

private val ITEM_HEIGHT = 108.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModeScreen(
    onOpenEditModes: () -> Unit,
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
                    IconButton(onClick = onOpenEditModes) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.cd_edit_modes))
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
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
            ModeList(
                modes     = modes,
                viewModel = viewModel,
                modifier  = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
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

    // Debounced mode switch — fires 400 ms after scrolling settles.
    LaunchedEffect(centerModeIdx) {
        delay(400)
        viewModel.switchMode(modes[centerModeIdx].id)
    }

    BoxWithConstraints(modifier = modifier) {
        val viewportHeight = maxHeight
        val aimLineTop    = viewportHeight / 2 - ITEM_HEIGHT / 2
        val aimLineBottom = viewportHeight / 2 + ITEM_HEIGHT / 2
        val density = LocalDensity.current

        val activeModeColor by remember {
            derivedStateOf {
                val id = viewModel.activeModeId
                val mode = if (id != null) modes.find { it.id == id }
                           else modes.getOrNull(centerModeIdx)
                mode?.colorHex?.let {
                    runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
                } ?: Color.Transparent
            }
        }

        val aimBandColor by animateColorAsState(
            targetValue = activeModeColor.copy(alpha = 0.20f),
            label = "aim_band",
        )

        val aimLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)

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
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top    = viewportHeight / 2 - ITEM_HEIGHT / 2,
                bottom = viewportHeight / 2 - ITEM_HEIGHT / 2,
            ),
        ) {
            itemsIndexed(modes, key = { _, mode -> mode.id }) { index, mode ->
                ModeItem(
                    mode     = mode,
                    isCenter = index == centerModeIdx,
                    onClick  = {
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

        Text(
            text  = mode.name,
            style = if (isCenter)
                MaterialTheme.typography.headlineSmall
            else
                MaterialTheme.typography.titleLarge,
            fontWeight = if (isCenter) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
        )
    }
}