package io.github.smiling_pixel.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import io.github.smiling_pixel.client.WeatherClient
import io.github.smiling_pixel.filesystem.fileManager
import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.model.Location
import io.github.smiling_pixel.util.Logger
import io.github.smiling_pixel.util.e
import io.github.smiling_pixel.util.w
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Screen displaying the details of a diary entry.
 * It allows the user to view the entry's content, title, date, and weather,
 * or edit these details if they switch to edit mode.
 *
 * @param entry The [DiaryEntry] to display or edit. If null, creates a new entry.
 * @param weatherClient The [WeatherClient] to fetch weather information.
 * @param onSave Callback invoked when the user saves the entry.
 * @param onCancel Callback invoked when the user cancels editing or goes back.
 */
@OptIn(ExperimentalTime::class, ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailsScreen(
    entry: DiaryEntry?,
    weatherClient: WeatherClient,
    isSyncing: Boolean = false,
    onSyncRequest: () -> Unit = {},
    onSave: (DiaryEntry) -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isEditing by remember { mutableStateOf(entry == null) }
    var title by remember { mutableStateOf(entry?.title ?: "") }
    var content by remember { mutableStateOf(entry?.content ?: "") }
    var entryDate by remember {
        mutableStateOf(
            entry?.entryDate ?: Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date,
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }

    var weatherCondition by remember { mutableStateOf(entry?.weatherCondition ?: "") }
    var minTemp by remember { mutableStateOf(entry?.minTemperature) }
    var maxTemp by remember { mutableStateOf(entry?.maxTemperature) }

    if (showDatePicker) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = entryDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
            )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        entryDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
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
                        entryDate = entry.entryDate
                        weatherCondition = entry.weatherCondition ?: ""
                        minTemp = entry.minTemperature
                        maxTemp = entry.maxTemperature
                    }
                }) {
                    Text("Cancel")
                }
                Button(onClick = {
                    val now = Clock.System.now()
                    val newEntry =
                        entry?.copy(
                            title = title,
                            content = content,
                            updatedAt = now,
                            entryDate = entryDate,
                            weatherCondition = weatherCondition,
                            minTemperature = minTemp,
                            maxTemperature = maxTemp,
                        ) ?: DiaryEntry(
                            id = 0, // 0 means new entry, DAO should handle ID generation
                            title = title,
                            content = content,
                            createdAt = now,
                            updatedAt = now,
                            entryDate = entryDate,
                            weatherCondition = weatherCondition,
                            minTemperature = minTemp,
                            maxTemperature = maxTemp,
                        )
                    onSave(newEntry)
                    isEditing = false
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
                IconButton(onClick = onSyncRequest, enabled = !isSyncing) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = if (isSyncing) "Syncing" else "Sync Cloud",
                    )
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
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
                placeholder = { Text("Enter title...") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                        .padding(vertical = 8.dp),
            ) {
                Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Date: $entryDate",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = weatherCondition,
                    onValueChange = { weatherCondition = it },
                    label = { Text("Condition") },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = if (minTemp != null && maxTemp != null) "$minTemp / $maxTemp" else "",
                    onValueChange = { },
                    label = { Text("Min/Max Temp") },
                    modifier = Modifier.weight(1f),
                    readOnly = true,
                )
                IconButton(onClick = {
                    scope.launch {
                        try {
                            val location = Location(0.0, 0.0) // TODO: Get actual location @SmilingPixel
                            Logger.w("EntryDetailsScreen", "Using hardcoded location: $location")
                            val targetDate =
                                entry?.createdAt?.toLocalDateTime(TimeZone.currentSystemDefault())?.date
                                    ?: Clock.System
                                        .now()
                                        .toLocalDateTime(TimeZone.currentSystemDefault())
                                        .date

                            val start =
                                LocalDateTime(
                                    targetDate,
                                    LocalTime(5, 0),
                                ).toInstant(TimeZone.currentSystemDefault())
                            val end =
                                LocalDateTime(
                                    targetDate,
                                    LocalTime(23, 59),
                                ).toInstant(TimeZone.currentSystemDefault())

                            val now = Clock.System.now()
                            val todayStart =
                                LocalDateTime(
                                    now.toLocalDateTime(TimeZone.currentSystemDefault()).date,
                                    LocalTime(0, 0),
                                ).toInstant(TimeZone.currentSystemDefault())

                            val hourly =
                                if (start < todayStart) {
                                    weatherClient.getHourlyHistory(location, start, end)
                                } else {
                                    weatherClient.getHourlyForecast(location)
                                }

                            if (hourly.isNotEmpty()) {
                                // Filter for the relevant time window if forecast returns more
                                val relevant = hourly.filter { it.startTime >= start && it.endTime <= end }
                                if (relevant.isNotEmpty()) {
                                    minTemp = relevant.minOf { it.minTemperature }
                                    maxTemp = relevant.maxOf { it.maxTemperature }
                                    // Simple condition aggregation: take the most frequent or just the first/middle?
                                    // Let's take the one at noon or middle of list
                                    weatherCondition = relevant[relevant.size / 2].condition
                                } else if (hourly.isNotEmpty()) {
                                    // Fallback if filter fails (e.g. forecast boundaries)
                                    minTemp = hourly.minOf { it.minTemperature }
                                    maxTemp = hourly.maxOf { it.maxTemperature }
                                    weatherCondition = hourly[hourly.size / 2].condition
                                }
                            } else {
                                // Fallback to current weather if hourly fails or returns empty
                                val current = weatherClient.getWeather(location)
                                weatherCondition = current.condition
                                minTemp = current.temperature
                                maxTemp = current.temperature
                            }
                        } catch (e: Exception) {
                            // TODO: Show error to user @SmilingPixel
                            Logger.e("EntryDetailsScreen", "Weather fetch failed: $e")
                        }
                    }
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Weather")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Content") },
                placeholder = { Text("Type anything... Markdown is supported.") },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            var fileCount by remember(entry) { mutableStateOf(0) }
            var totalFileSize by remember(entry) { mutableStateOf(0L) }

            LaunchedEffect(entry!!.content) {
                var count = 0
                var size = 0L
                val regex = Regex("localfile:///([^)/\\s]+)")
                val matches = regex.findAll(entry.content)
                for (match in matches) {
                    val filePath = match.groupValues[1]
                    // Reject potentially unsafe paths to prevent directory traversal.
                    if (filePath.isEmpty() || filePath.contains("..")) {
                        Logger.w("EntryDetailsScreen", "Rejected potentially unsafe or empty path: $filePath")
                        continue
                    }
                    count++
                    try {
                        size += fileManager.getSize(filePath)
                    } catch (e: Exception) {
                        Logger.w("EntryDetailsScreen", "Failed to get size for $filePath: $e")
                    }
                }
                fileCount = count
                totalFileSize = size
            }

            // show timestamps
            val createdLocal = entry!!.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
            val updatedLocal = entry.updatedAt.toLocalDateTime(TimeZone.currentSystemDefault())

            Text(
                text = "Date: ${entry.entryDate}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(6.dp))

            val createdTimeStr = "${createdLocal.hour.toString().padStart(
                2,
                '0',
            )}:${createdLocal.minute.toString().padStart(2, '0')}:${createdLocal.second.toString().padStart(2, '0')}"
            Text(
                text = "Created: ${createdLocal.date} $createdTimeStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Spacer(modifier = Modifier.height(6.dp))

            val updatedTimeStr = "${updatedLocal.hour.toString().padStart(
                2,
                '0',
            )}:${updatedLocal.minute.toString().padStart(2, '0')}:${updatedLocal.second.toString().padStart(2, '0')}"
            Text(
                text = "Updated: ${updatedLocal.date} $updatedTimeStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Spacer(modifier = Modifier.height(6.dp))

            val statsText = "${entry.content.length} chars | $fileCount files, ${formatBytes(totalFileSize)}"
            Text(
                text = statsText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (entry.weatherCondition != null) {
                val weatherText =
                    "Weather: ${entry.weatherCondition}, Temp: ${entry.minTemperature}°C - ${entry.maxTemperature}°C"
                Text(
                    text = weatherText,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(modifier = Modifier.height(12.dp))

            Markdown(
                content = entry.content,
                imageTransformer = Coil3ImageTransformerImpl,
            )
        }
    }
}

/**
 * A utility to format byte sizes into human-readable strings (e.g., KB, MB).
 * We need this custom utility because Kotlin Multiplatform does not provide
 * Java's java.text.DecimalFormat out of the box, and we want a consistent
 * way to calculate and display file sizes across Android, JVM, and Wasm/JS.
 * It progressively divides by 1024 to find the correct magnitude and manually
 * rounds to one decimal place using simple math.
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val prefixes = "KMGTPE"
    val exp =
        (kotlin.math.ln(bytes.toDouble()) / kotlin.math.ln(1024.0))
            .toInt()
            .coerceIn(1, prefixes.length)
    val pre = prefixes[exp - 1]
    val value = bytes / 1024.0.pow(exp.toDouble())
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return "$rounded ${pre}B"
}
