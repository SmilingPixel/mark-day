package io.github.smiling_pixel.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.smiling_pixel.client.WeatherClient
import io.github.smiling_pixel.client.getCloudDriveClient
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.draft.EditorExitGuard
import io.github.smiling_pixel.draft.EntryDraftKey
import io.github.smiling_pixel.draft.EntryDraftRepository
import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.sync.startAutoSync
import io.github.smiling_pixel.util.Logger
import io.github.smiling_pixel.util.e
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

/**
 * Displays diary entries and coordinates durable entry-editor drafts.
 *
 * @param repo Repository containing committed diary entries.
 * @param draftRepository Repository containing device-local editor drafts.
 * @param weatherClient Client used to populate entry weather fields.
 * @param isSelectionMode Whether multi-entry selection is active.
 * @param selectedIds Stable IDs of selected entries.
 * @param onSelectionModeChange Updates multi-entry selection mode.
 * @param onSelectionChange Updates selected entry IDs.
 * @param onExitGuardChange Reports the active editor's exit protection.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalTime::class)
@Composable
fun EntriesScreen(
    repo: DiaryRepository,
    draftRepository: EntryDraftRepository,
    weatherClient: WeatherClient,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onSelectionModeChange: (Boolean) -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
    onExitGuardChange: (EditorExitGuard?) -> Unit = {},
) {
    val entriesState by repo.entries.collectAsState()
    val scope = rememberCoroutineScope()

    DisposableEffect(repo) {
        val autoSyncJob = startAutoSync(repo)
        onDispose {
            autoSyncJob?.cancel()
        }
    }

    // The stable ID is saveable; the entry itself is always resolved from repository state.
    var selectedEntrySyncId by rememberSaveable { mutableStateOf<String?>(null) }
    var recentlyCommittedEntry by remember { mutableStateOf<DiaryEntry?>(null) }
    var isCreating by rememberSaveable { mutableStateOf(false) }
    var initialDraftChecked by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncSummary by remember { mutableStateOf<String?>(null) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var draftRecoveryError by remember { mutableStateOf<String?>(null) }
    val selectedEntry =
        entriesState.firstOrNull { it.syncId == selectedEntrySyncId }
            ?: recentlyCommittedEntry?.takeIf { it.syncId == selectedEntrySyncId }

    LaunchedEffect(entriesState, selectedEntrySyncId) {
        if (entriesState.any { it.syncId == selectedEntrySyncId }) {
            recentlyCommittedEntry = null
        }
    }

    LaunchedEffect(draftRepository) {
        try {
            val newDraft = draftRepository.load(EntryDraftKey.NewEntry)
            if (newDraft != null) {
                // Save and draft deletion use different stores and cannot be transactional. If the entry already exists,
                // its stable target sync ID proves the commit completed and this is only interrupted cleanup.
                val committedEntry = repo.getAll().firstOrNull { it.syncId == newDraft.targetSyncId }
                if (committedEntry == null) {
                    isCreating = true
                } else {
                    draftRepository.delete(EntryDraftKey.NewEntry)
                }
            }
        } catch (e: Exception) {
            Logger.e("EntriesScreen", "New-entry draft recovery failed: $e")
            draftRecoveryError = "Couldn’t restore the saved entry draft."
        } finally {
            initialDraftChecked = true
        }
    }

    if (syncSummary != null) {
        AlertDialog(
            onDismissRequest = { syncSummary = null },
            title = { Text("Sync Summary") },
            text = { Text(syncSummary!!) },
            confirmButton = { Button(onClick = { syncSummary = null }) { Text("OK") } },
        )
    }

    if (syncError != null) {
        AlertDialog(
            onDismissRequest = { syncError = null },
            title = { Text("Sync Error") },
            text = { Text(syncError!!) },
            confirmButton = { Button(onClick = { syncError = null }) { Text("OK") } },
        )
    }

    if (draftRecoveryError != null) {
        AlertDialog(
            onDismissRequest = { draftRecoveryError = null },
            title = { Text("Draft recovery unavailable") },
            text = { Text(draftRecoveryError!!) },
            confirmButton = { Button(onClick = { draftRecoveryError = null }) { Text("OK") } },
        )
    }

    val performSync = {
        if (!isSyncing) {
            isSyncing = true
            scope.launch {
                try {
                    val result =
                        io.github.smiling_pixel.sync.performCloudSync(
                            client = getCloudDriveClient(),
                            repo = repo,
                            localEntries = entriesState,
                        )
                    syncSummary =
                        "Sync completed!\nUploaded: ${result.uploaded}\nDownloaded: ${result.downloaded}\nUnchanged: ${result.unchanged}"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    syncError = e.message ?: "An unknown error occurred during sync"
                } finally {
                    isSyncing = false
                }
            }
        }
    }

    if (!initialDraftChecked) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }
    } else if (isCreating || selectedEntrySyncId != null) {
        // Details view (New or Edit)
        if (isCreating || selectedEntry != null) {
            EntryDetailsScreen(
                entry = selectedEntry,
                weatherClient = weatherClient,
                isSyncing = isSyncing,
                onSyncRequest = { performSync() },
                draftRepository = draftRepository,
                onExitGuardChange = onExitGuardChange,
                onSave = { entry ->
                    // Keep a local canonical value until Room's Flow emits the write. This avoids briefly treating a
                    // successfully inserted entry as another new-entry editor while the database notification catches up.
                    val savedEntry =
                        if (isCreating) {
                            val newId = repo.insert(entry)
                            entry.copy(id = newId)
                        } else {
                            repo.update(entry)
                            entry
                        }
                    recentlyCommittedEntry = savedEntry
                    selectedEntrySyncId = savedEntry.syncId
                    isCreating = false
                    savedEntry
                },
                onCancel = {
                    isCreating = false
                    selectedEntrySyncId = null
                    recentlyCommittedEntry = null
                    onExitGuardChange(null)
                },
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    } else {
        // List view
        Scaffold(
            floatingActionButton = {
                if (!isSelectionMode) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        FloatingActionButton(
                            onClick = { performSync() },
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync with Google Drive")
                            }
                        }
                        FloatingActionButton(
                            onClick = { isCreating = true },
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "New Diary Entry")
                        }
                    }
                }
            },
        ) { paddingValues ->
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entriesState, key = { it.id }) { entry ->
                    val isSelected = entry.syncId in selectedIds
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(CardDefaults.shape)
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            val newSelection =
                                                if (isSelected) {
                                                    selectedIds - entry.syncId
                                                } else {
                                                    selectedIds + entry.syncId
                                                }
                                            onSelectionChange(newSelection)
                                            if (newSelection.isEmpty()) {
                                                onSelectionModeChange(false)
                                            }
                                        } else {
                                            selectedEntrySyncId = entry.syncId
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            onSelectionModeChange(true)
                                            onSelectionChange(setOf(entry.syncId))
                                        }
                                    },
                                ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp),
                        ) {
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null, // Handled by card click
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                val (displayTitle, displayContent) =
                                    remember(entry.title, entry.content) {
                                        if (entry.title.isNotEmpty()) {
                                            val contentPreview =
                                                if (entry.content.isNotEmpty()) {
                                                    entry.content
                                                        .lineSequence()
                                                        .firstOrNull()
                                                        ?.take(60)
                                                } else {
                                                    null
                                                }
                                            entry.title to contentPreview
                                        } else {
                                            val firstTwoLines =
                                                entry.content
                                                    .lineSequence()
                                                    .take(2)
                                                    .toList()
                                            val titlePreview =
                                                if (firstTwoLines.isNotEmpty() &&
                                                    firstTwoLines[0].isNotEmpty()
                                                ) {
                                                    firstTwoLines[0].take(60)
                                                } else {
                                                    "Untitled"
                                                }
                                            val contentPreview =
                                                if (firstTwoLines.size > 1 &&
                                                    firstTwoLines[1].isNotEmpty()
                                                ) {
                                                    firstTwoLines[1].take(60)
                                                } else {
                                                    null
                                                }
                                            titlePreview to contentPreview
                                        }
                                    }

                                Text(
                                    text = displayTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (displayContent != null) {
                                    Text(
                                        text = displayContent,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = "Date: ${entry.entryDate}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
