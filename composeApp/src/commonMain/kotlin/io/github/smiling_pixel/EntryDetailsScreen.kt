package io.github.smiling_pixel

import io.github.smiling_pixel.model.DiaryEntry
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun EntryDetailsScreen(entry: DiaryEntry, onBack: () -> Unit) {
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) {
                Text("Back")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        // show timestamps
        val createdLocal = entry.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
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
