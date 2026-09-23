package io.github.smiling_pixel.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.model.LoadState

/**
 * Displays diary entries and reports route intents to the app coordinator.
 *
 * @param repo Repository containing committed diary entries.
 * @param isSelectionMode Whether multi-entry selection is active.
 * @param selectedIds Stable IDs of selected entries.
 * @param onSelectionModeChange Updates multi-entry selection mode.
 * @param onSelectionChange Updates selected entry IDs.
 * @param isSyncing Whether a cloud synchronization operation is running.
 * @param onSyncRequest Requests cloud synchronization.
 * @param syncAvailability Current Google Drive capability and authorization state.
 * @param onOpenSettings Opens the Settings destination.
 * @param onOpenEntry Opens an entry detail route by stable synchronization identifier.
 * @param onCreateEntry Opens the typed new-entry route.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntriesScreen(
    repo: DiaryRepository,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onSelectionModeChange: (Boolean) -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
    isSyncing: Boolean = false,
    onSyncRequest: () -> Unit = {},
    syncAvailability: SyncAvailability = SyncAvailability.NotConnected,
    onOpenSettings: () -> Unit = {},
    onOpenEntry: (String) -> Unit = {},
    onCreateEntry: () -> Unit = {},
) {
    val entriesState by repo.entries.collectAsState()
    val entriesLoadState by repo.entriesState.collectAsState()

    val contentState = entriesLoadState
    run {
        Scaffold(
            floatingActionButton = {
                if (!isSelectionMode) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (entriesState.isNotEmpty() &&
                            (
                                syncAvailability == SyncAvailability.Available ||
                                    syncAvailability == SyncAvailability.Offline
                            )
                        ) {
                            FloatingActionButton(onClick = onSyncRequest, shape = RoundedCornerShape(16.dp)) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "Sync with Google Drive")
                                }
                            }
                        }
                        if (entriesState.isNotEmpty()) {
                            FloatingActionButton(onClick = onCreateEntry, shape = RoundedCornerShape(16.dp)) {
                                Icon(Icons.Default.Add, contentDescription = "New Diary Entry")
                            }
                        }
                    }
                }
            },
        ) { paddingValues ->
            when (contentState) {
                LoadState.Loading ->
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                is LoadState.Error ->
                    EmptyContentState(
                        icon = Icons.Default.Add,
                        title = contentState.message,
                        description = "Try again to load your entries.",
                        actionLabel = "Retry",
                        onAction = { onSyncRequest() },
                    )
                is LoadState.Content ->
                    if (contentState.value.isEmpty()) {
                        EmptyContentState(
                            icon = Icons.Default.Add,
                            title = "Write your first entry",
                            description = "Capture a thought, memory, or moment from today.",
                            actionLabel = "New entry",
                            onAction = onCreateEntry,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
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
                                                        onOpenEntry(entry.syncId)
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
                                            entry.moodEmoji?.let { mood ->
                                                Text(
                                                    text = "Mood: $mood",
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
            if (entriesState.isNotEmpty() &&
                syncAvailability != SyncAvailability.Available &&
                syncAvailability != SyncAvailability.Offline
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Connect Google Drive in Settings.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onOpenSettings) { Text("Open Settings") }
                }
            }
        }
    }
}

@Composable
private fun EmptyContentState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        Button(onClick = onAction, modifier = Modifier.padding(top = 20.dp)) { Text(actionLabel) }
    }
}
