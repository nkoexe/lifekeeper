package com.lifekeeper.app.ui.edit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifekeeper.app.LifekeeperApp
import com.lifekeeper.app.R
import com.lifekeeper.app.data.model.Mode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// ── Color palette grid ────────────────────────────────────────────────────────
// 4 rows × 8 cols: lightness ramp (pastel → soft → punchy → deep) per column.
// Columns: RedRose · Purple · Indigo · Sky · Teal · Lime · Amber · Slate
private val COLOR_GRID = listOf(
    listOf("#FECDD3", "#E9D5FF", "#C7D2FE", "#BAE6FD", "#99F6E4", "#D9F99D", "#FDE68A", "#E2E8F0"),
    listOf("#FB7185", "#C084FC", "#818CF8", "#38BDF8", "#2DD4BF", "#A3E635", "#FBBF24", "#94A3B8"),
    listOf("#EF4444", "#A855F7", "#6366F1", "#0EA5E9", "#14B8A6", "#84CC16", "#F59E0B", "#64748B"),
    listOf("#9F1239", "#6B21A8", "#3730A3", "#075985", "#0F766E", "#3F6212", "#92400E", "#1E293B"),
)

private val DEFAULT_COLOR = COLOR_GRID[2][3] // Sky-500 #0EA5E9

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditModesScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as LifekeeperApp
    val viewModel: EditModesViewModel = viewModel(factory = EditModesViewModel.factory(app))

    val modes by viewModel.modes.collectAsState()

    val localModes = remember { mutableStateListOf<Mode>() }

    LaunchedEffect(modes) {
        val dbById = modes.associateBy { it.id }
        val localIds = localModes.map { it.id }.toSet()
        localModes.removeAll { it.id !in dbById }
        localModes.replaceAll { local -> dbById[local.id] ?: local }
        modes.filter { it.id !in localIds }.forEach { localModes.add(it) }
    }

    var pendingReorder by remember { mutableStateOf(false) }
    LaunchedEffect(pendingReorder) {
        if (!pendingReorder) return@LaunchedEffect
        delay(600)
        viewModel.reorderModes(localModes.map { it.id })
        pendingReorder = false
    }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        localModes.apply { add(to.index, removeAt(from.index)) }
        pendingReorder = true
    }

    var sheetTarget by remember { mutableStateOf<Mode?>(null) }
    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope       = rememberCoroutineScope()

    if (sheetTarget != null) {
        ModeEditorSheet(
            mode       = sheetTarget!!.let { if (it.id == 0L) null else it },
            sheetState = sheetState,
            onDismiss  = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { sheetTarget = null }
            },
            onSave = { name, colorHex ->
                val target = sheetTarget
                if (target != null) {
                    if (target.id == 0L) viewModel.addMode(name, colorHex)
                    else viewModel.updateMode(target.copy(name = name, colorHex = colorHex))
                }
                scope.launch { sheetState.hide() }.invokeOnCompletion { sheetTarget = null }
            },
            onDelete = { mode ->
                viewModel.deleteMode(mode)
                scope.launch { sheetState.hide() }.invokeOnCompletion { sheetTarget = null }
            },
        )
    }

    Scaffold(
        // Shell Scaffold already applies insets; avoid doubling them.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Edit Modes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { sheetTarget = Mode(id = 0L, name = "", colorHex = DEFAULT_COLOR) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.add_mode))
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(localModes.size, key = { localModes[it].id }) { index ->
                val mode = localModes[index]
                ReorderableItem(reorderState, key = mode.id) { isDragging ->
                    val elevation by animateDpAsState(
                        targetValue = if (isDragging) 8.dp else 0.dp,
                        label = "drag_elevation",
                    )
                    ElevatedCard(
                        onClick = { sheetTarget = mode },
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation, MaterialTheme.shapes.large),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                val color = remember(mode.colorHex) {
                                    runCatching {
                                        Color(android.graphics.Color.parseColor(mode.colorHex))
                                    }.getOrDefault(Color.Gray)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(color),
                                )
                                Text(mode.name, style = MaterialTheme.typography.bodyLarge)
                            }
                            Icon(
                                imageVector = Icons.Outlined.DragHandle,
                                contentDescription = stringResource(R.string.cd_reorder),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.draggableHandle(),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Editor bottom sheet ───────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeEditorSheet(
    mode       : Mode?,
    sheetState : androidx.compose.material3.SheetState,
    onDismiss  : () -> Unit,
    onSave     : (name: String, colorHex: String) -> Unit,
    onDelete   : (Mode) -> Unit,
) {
    val isEditing = mode != null
    var name     by remember(mode) { mutableStateOf(mode?.name ?: "") }
    var pickedColor by remember(mode) { mutableStateOf(mode?.colorHex ?: DEFAULT_COLOR) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Delete confirmation — separate AlertDialog layered on top of the sheet.
    if (showDeleteConfirm && mode != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title   = { Text("Delete mode?") },
            text    = { Text("\"${mode.name}\" and all its tracked time will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = { onDelete(mode) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        // Suppress built-in inset handling; we apply them manually so the sheet
        // sits flush with the nav bar and the keyboard pushes content up cleanly.
        contentWindowInsets = { WindowInsets(0) },
        dragHandle       = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 24.dp),
        ) {
            // ── Drag handle ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    .align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(20.dp))

            // ── Header: preview swatch + title ────────────────────────────
            val previewColor = remember(pickedColor) {
                runCatching { Color(android.graphics.Color.parseColor(pickedColor)) }
                    .getOrDefault(Color.Gray)
            }
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(previewColor),
                )
                Text(
                    text  = if (isEditing) "Edit Mode" else "New Mode",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Name field ────────────────────────────────────────────────
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("Name") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = MaterialTheme.shapes.large,
            )

            Spacer(Modifier.height(20.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(Modifier.height(16.dp))

            // ── Color section header ──────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Color", style = MaterialTheme.typography.titleSmall)
                Text(
                    text  = pickedColor.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Color grid (4 rows × 8 cols) ──────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                COLOR_GRID.forEach { row ->
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        row.forEach { hex ->
                            val swatchColor = remember(hex) {
                                runCatching { Color(android.graphics.Color.parseColor(hex)) }
                                    .getOrDefault(Color.Gray)
                            }
                            val isSelected = hex.equals(pickedColor, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .then(
                                        if (isSelected)
                                            Modifier.border(
                                                width = 2.5.dp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                shape = CircleShape,
                                            )
                                        else Modifier
                                    )
                                    .clickable { pickedColor = hex },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(Modifier.height(16.dp))

            // ── Action row ────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isEditing) Arrangement.SpaceBetween else Arrangement.End,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                if (isEditing) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector        = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.error,
                            modifier           = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick  = { onSave(name.trim(), pickedColor) },
                        enabled  = name.isNotBlank(),
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
