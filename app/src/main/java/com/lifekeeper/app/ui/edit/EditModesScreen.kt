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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifekeeper.app.LifekeeperApp
import com.lifekeeper.app.R
import com.lifekeeper.app.data.model.Mode
import kotlinx.coroutines.delay
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

    var dialogTarget by remember { mutableStateOf<Mode?>(null) }

    dialogTarget?.let { target ->
        ModeEditorDialog(
            mode = if (target.id == 0L) null else target,
            onDismiss = { dialogTarget = null },
            onSave = { name, colorHex ->
                if (target.id == 0L) viewModel.addMode(name, colorHex)
                else viewModel.updateMode(target.copy(name = name, colorHex = colorHex))
                dialogTarget = null
            },
            onDelete = { mode ->
                viewModel.deleteMode(mode)
                dialogTarget = null
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
                onClick = { dialogTarget = Mode(id = 0L, name = "", colorHex = DEFAULT_COLOR) },
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
                        onClick = { dialogTarget = mode },
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

// ── Editor dialog ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeEditorDialog(
    mode: Mode?,
    onDismiss: () -> Unit,
    onSave: (name: String, colorHex: String) -> Unit,
    onDelete: (Mode) -> Unit,
) {
    val isEditing = mode != null
    var name by remember(mode) { mutableStateOf(mode?.name ?: "") }
    var pickedColor by remember(mode) { mutableStateOf(mode?.colorHex ?: DEFAULT_COLOR) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm && mode != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete mode?") },
            text = { Text("\"${mode.name}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = { onDelete(mode) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
        return
    }

    val previewColor = remember(pickedColor) {
        runCatching { Color(android.graphics.Color.parseColor(pickedColor)) }.getOrDefault(Color.Gray)
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 28.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Header: preview circle + accented title ──────────────────
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(previewColor),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (isEditing) "Edit Mode" else "New Mode",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                // ── Name field ───────────────────────────────────────────────
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                )

                Spacer(Modifier.height(20.dp))

                // ── Color grid header ────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Color", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = pickedColor.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ── Color grid (4 rows × 8 columns) ─────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    COLOR_GRID.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            row.forEach { hex ->
                                val color = remember(hex) {
                                    runCatching { Color(android.graphics.Color.parseColor(hex)) }
                                        .getOrDefault(Color.Gray)
                                }
                                val isSelected = hex.equals(pickedColor, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .then(
                                            if (isSelected)
                                                Modifier.border(
                                                    2.5.dp,
                                                    MaterialTheme.colorScheme.onSurface,
                                                    CircleShape,
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

                // ── Action row ───────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isEditing) Arrangement.SpaceBetween else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isEditing) {
                        TextButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(
                            onClick = { onSave(name.trim(), pickedColor) },
                            enabled = name.isNotBlank(),
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

