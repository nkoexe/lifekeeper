@file:OptIn(ExperimentalMaterial3Api::class)

package com.lifekeeper.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifekeeper.app.BuildConfig
import com.lifekeeper.app.LifekeeperApp
import com.lifekeeper.app.R
import com.lifekeeper.app.data.preferences.ThemePreference

// ── Short labels for min-session segmented picker ─────────────────────────────

private val MIN_SESSION_OPTIONS = listOf(0, 15, 30, 60, 120, 300)

private fun Int.toDropdownLabel(): String = when (this) {
    0    -> "Instant"
    15   -> "15 seconds"
    30   -> "30 seconds"
    60   -> "1 minute"
    120  -> "2 minutes"
    300  -> "5 minutes"
    else -> "${this}s"
}

// ── Settings group card ───────────────────────────────────────────────────────

@Composable
private fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.elevatedCardColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text     = title,
            style    = MaterialTheme.typography.labelLarge,
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors   = colors,
        ) {
            Column(content = content)
        }
    }
}

// ── Confirm delete dialog ─────────────────────────────────────────────────────

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text(title) },
        text    = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(); onDismiss() },
                colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(onBack: () -> Unit, onAbout: () -> Unit) {
    val app = LocalContext.current.applicationContext as LifekeeperApp
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(app))

    val prefs     by vm.preferences.collectAsState()
    val isFilling by vm.isFilling.collectAsState()
    val fillDone  by vm.fillDone.collectAsState()
    val isWorking by vm.isWorking.collectAsState()
    val workMsg   by vm.workMessage.collectAsState()

    var showDeleteTrackedDialog    by remember { mutableStateOf(false) }
    var showDeleteEverythingDialog by remember { mutableStateOf(false) }
    var expandedMinSession         by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(fillDone) {
        if (fillDone) {
            snackbar.showSnackbar("Database filled with 30 days of random data")
            vm.clearFillDone()
        }
    }
    LaunchedEffect(workMsg) {
        workMsg?.let {
            snackbar.showSnackbar(it)
            vm.clearWorkMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // ── APPEARANCE ────────────────────────────────────────────────────
            SettingsGroup(title = "Appearance") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text  = "Theme",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemePreference.entries.forEachIndexed { index, option ->
                            val icon = when (option) {
                                ThemePreference.SYSTEM -> Icons.Outlined.SettingsBrightness
                                ThemePreference.LIGHT  -> Icons.Outlined.LightMode
                                ThemePreference.DARK   -> Icons.Outlined.DarkMode
                            }
                            SegmentedButton(
                                selected = prefs.theme == option,
                                onClick  = { vm.setTheme(option) },
                                shape    = SegmentedButtonDefaults.itemShape(index, ThemePreference.entries.size),
                                icon     = {
                                    SegmentedButtonDefaults.Icon(active = prefs.theme == option) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                        )
                                    }
                                },
                            ) {
                                Text(option.displayName())
                            }
                        }
                    }
                }
            }

            // ── DATA ──────────────────────────────────────────────────────────
            SettingsGroup(title = "Data") {
                ListItem(
                    headlineContent   = {
                        Text(
                            text       = "Delete tracking history",
                            color      = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Normal,
                        )
                    },
                    supportingContent = { Text("Remove all recorded time entries") },
                    leadingContent    = {
                        Icon(
                            Icons.Outlined.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier = Modifier.clickable(
                        enabled = !isFilling && !isWorking,
                        onClick = { showDeleteTrackedDialog = true },
                    ),
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                ListItem(
                    headlineContent   = {
                        Text(
                            text       = "Reset everything",
                            color      = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Normal,
                        )
                    },
                    supportingContent = { Text("Delete all data and reset settings to defaults") },
                    leadingContent    = {
                        Icon(
                            Icons.Outlined.SettingsBackupRestore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier = Modifier.clickable(
                        enabled = !isFilling && !isWorking,
                        onClick = { showDeleteEverythingDialog = true },
                    ),
                )
            }

            // ── DEVELOPER (debug builds only) ─────────────────────────────────
            if (BuildConfig.DEBUG) {
                SettingsGroup(title = "Developer") {

                    // Minimum session
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text  = "Minimum session",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text  = "Taps shorter than this are silently discarded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        ExposedDropdownMenuBox(
                            expanded = expandedMinSession,
                            onExpandedChange = { expandedMinSession = !expandedMinSession },
                        ) {
                            OutlinedTextField(
                                value = prefs.minSessionDurationSeconds.toDropdownLabel(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Duration") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMinSession) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(
                                expanded = expandedMinSession,
                                onDismissRequest = { expandedMinSession = false },
                            ) {
                                MIN_SESSION_OPTIONS.forEach { seconds ->
                                    DropdownMenuItem(
                                        text = { Text(seconds.toDropdownLabel()) },
                                        onClick = {
                                            vm.setMinSessionDuration(seconds)
                                            expandedMinSession = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Fill random data
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text  = "Fill last 30 days with random data",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text  = "Generates random entries for each mode over the past 30 days. Replaces entries in that window.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        FilledTonalButton(
                            onClick  = vm::fillWithRandomData,
                            enabled  = !isFilling && !isWorking,
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor   = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            if (isFilling) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color       = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.BugReport,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text("Fill with random data")
                            }
                        }
                    }
                }
            }

            // ── ABOUT ─────────────────────────────────────────────────────────
            ElevatedCard(
                onClick  = onAbout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                ListItem(
                    headlineContent = { Text("About") },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showDeleteTrackedDialog) {
        ConfirmDeleteDialog(
            title        = "Delete tracking history?",
            body         = "All recorded time entries will be permanently deleted. Your modes and settings will not be affected.",
            confirmLabel = "Delete",
            onConfirm    = vm::deleteTrackedData,
            onDismiss    = { showDeleteTrackedDialog = false },
        )
    }

    if (showDeleteEverythingDialog) {
        ConfirmDeleteDialog(
            title        = "Reset everything?",
            body         = "All tracking history, modes, and settings will be permanently deleted and reset to defaults. This cannot be undone.",
            confirmLabel = "Reset",
            onConfirm    = vm::deleteEverything,
            onDismiss    = { showDeleteEverythingDialog = false },
        )
    }
}
