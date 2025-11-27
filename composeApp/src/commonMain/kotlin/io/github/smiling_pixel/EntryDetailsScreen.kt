package io.github.smiling_pixel

import io.github.smiling_pixel.model.DiaryEntry
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun EntryDetailsScreen(
    entry: DiaryEntry?,
    onSave: (DiaryEntry) -> Unit,
    onCancel: () -> Unit
) {
    var isEditing by remember { mutableStateOf(entry == null) }
    var title by remember { mutableStateOf(entry?.title ?: "") }
    var content by remember { mutableStateOf(entry?.content ?: "") }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isEditing) {
                Text(
                    text = if (entry == null) "New Entry" else "Edit Entry",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    if (entry == null) {
                        onCancel()
                    } else {
                        isEditing = false
                        title = entry.title
                        content = entry.content
                    }
                }) {
                    Text("Cancel")
                }
                Button(onClick = {
                    val now = Clock.System.now()
                    val newEntry = entry?.copy(
                        title = title,
                        content = content,
                        updatedAt = now
                    ) ?: DiaryEntry(
                        id = 0, // 0 means new entry, DAO should handle ID generation
                        title = title,
                        content = content,
                        createdAt = now,
                        updatedAt = now
                    )
                    onSave(newEntry)
                }) {
                    Text("Save")
                }
            } else {
                Text(
                    text = entry!!.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { isEditing = true }) {
                    Text("Edit")
                }
                TextButton(onClick = onCancel) {
                    Text("Back")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isEditing) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Content") },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // show timestamps
            val createdLocal = entry!!.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
            val updatedLocal = entry.updatedAt.toLocalDateTime(TimeZone.currentSystemDefault())

            Text(
                text = "Created: ${createdLocal.date} ${createdLocal.time}",
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Updated: ${updatedLocal.date} ${updatedLocal.time}",
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
