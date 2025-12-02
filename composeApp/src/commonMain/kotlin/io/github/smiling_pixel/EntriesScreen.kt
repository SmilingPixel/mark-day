package io.github.smiling_pixel

import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.client.WeatherClient
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.database.InMemoryDiaryDao
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalFoundationApi::class, ExperimentalTime::class)
@Composable
fun EntriesScreen(
    repo: DiaryRepository,
    weatherClient: WeatherClient,
    isSelectionMode: Boolean,
    selectedIds: Set<Int>,
    onSelectionModeChange: (Boolean) -> Unit,
    onSelectionChange: (Set<Int>) -> Unit
) {
    val entriesState by repo.entries.collectAsState()
    val scope = rememberCoroutineScope()

    // currently-selected entry; null means list view (unless creating)
    var selectedEntry by remember { mutableStateOf<DiaryEntry?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    if (isCreating || selectedEntry != null) {
        // Details view (New or Edit)
        EntryDetailsScreen(
            entry = selectedEntry,
            weatherClient = weatherClient,
            onSave = { entry ->
                scope.launch {
                    if (isCreating) {
                        val newId = repo.insert(entry)
                        selectedEntry = entry.copy(id = newId)
                    } else {
                        repo.update(entry)
                        selectedEntry = entry
                    }
                    isCreating = false
                }
            },
            onCancel = {
                isCreating = false
                selectedEntry = null
            }
        )
    } else {
        // List view
        Scaffold(
            floatingActionButton = {
                if (!isSelectionMode) {
                    FloatingActionButton(
                        onClick = { isCreating = true },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Diary Entry")
                    }
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entriesState, key = { it.id }) { entry ->
                    val isSelected = entry.id in selectedIds
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CardDefaults.shape)
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        val newSelection = if (isSelected) selectedIds - entry.id else selectedIds + entry.id
                                        onSelectionChange(newSelection)
                                        if (newSelection.isEmpty()) {
                                            onSelectionModeChange(false)
                                        }
                                    } else {
                                        selectedEntry = entry
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        onSelectionModeChange(true)
                                        onSelectionChange(setOf(entry.id))
                                    }
                                }
                            ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null // Handled by card click
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                val updatedLocal = entry.updatedAt.toLocalDateTime(TimeZone.currentSystemDefault())
                                Text(
                                    text = "Updated: ${updatedLocal.date} ${updatedLocal.time}",
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


