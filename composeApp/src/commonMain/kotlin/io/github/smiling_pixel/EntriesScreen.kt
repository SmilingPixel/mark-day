package io.github.smiling_pixel

import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.model.sampleEntries
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.database.InMemoryDiaryDao
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun EntriesScreen() {
    // Repository backed by an in-memory DAO for non-Android targets / testing.
    val repo = remember { DiaryRepository(InMemoryDiaryDao(sampleEntries)) }
    val entriesState by repo.entries.collectAsState()
    val scope = rememberCoroutineScope()

    // currently-selected entry; null means list view
    val selected = remember { mutableStateOf<DiaryEntry?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (selected.value == null) {
            // List view
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entriesState, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected.value = entry },
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        } else {
            // Details view
            EntryDetailsScreen(entry = selected.value!!, onBack = { selected.value = null })
        }
    }
}

