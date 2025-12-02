package io.github.smiling_pixel

import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.client.WeatherClient
import io.github.smiling_pixel.model.Location
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun EntryDetailsScreen(
    entry: DiaryEntry?,
    weatherClient: WeatherClient,
    onSave: (DiaryEntry) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isEditing by remember { mutableStateOf(entry == null) }
    var title by remember { mutableStateOf(entry?.title ?: "") }
    var content by remember { mutableStateOf(entry?.content ?: "") }
    
    var weatherCondition by remember { mutableStateOf(entry?.weatherCondition ?: "") }
    var minTemp by remember { mutableStateOf(entry?.minTemperature) }
    var maxTemp by remember { mutableStateOf(entry?.maxTemperature) }

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
                        weatherCondition = entry.weatherCondition ?: ""
                        minTemp = entry.minTemperature
                        maxTemp = entry.maxTemperature
                    }
                }) {
                    Text("Cancel")
                }
                Button(onClick = {
                    val now = Clock.System.now()
                    val newEntry = entry?.copy(
                        title = title,
                        content = content,
                        updatedAt = now,
                        weatherCondition = weatherCondition,
                        minTemperature = minTemp,
                        maxTemperature = maxTemp
                    ) ?: DiaryEntry(
                        id = 0, // 0 means new entry, DAO should handle ID generation
                        title = title,
                        content = content,
                        createdAt = now,
                        updatedAt = now,
                        weatherCondition = weatherCondition,
                        minTemperature = minTemp,
                        maxTemperature = maxTemp
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
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = weatherCondition,
                    onValueChange = { weatherCondition = it },
                    label = { Text("Condition") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = if (minTemp != null && maxTemp != null) "$minTemp / $maxTemp" else "",
                    onValueChange = { },
                    label = { Text("Min/Max Temp") },
                    modifier = Modifier.weight(1f),
                    readOnly = true
                )
                IconButton(onClick = {
                    scope.launch {
                        try {
                            val location = Location(0.0, 0.0) // TODO: Get actual location @SmilingPixel
                            val targetDate = entry?.createdAt?.toLocalDateTime(TimeZone.currentSystemDefault())?.date 
                                ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                            
                            val start = LocalDateTime(targetDate, LocalTime(5, 0)).toInstant(TimeZone.currentSystemDefault())
                            val end = LocalDateTime(targetDate, LocalTime(23, 59)).toInstant(TimeZone.currentSystemDefault())
                            
                            val now = Clock.System.now()
                            val todayStart = LocalDateTime(now.toLocalDateTime(TimeZone.currentSystemDefault()).date, LocalTime(0, 0)).toInstant(TimeZone.currentSystemDefault())
                            
                            val hourly = if (start < todayStart) {
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
                            println("Weather fetch failed: $e")
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

            if (entry.weatherCondition != null) {
                 Text(
                    text = "Weather: ${entry.weatherCondition}, Temp: ${entry.minTemperature}°C - ${entry.maxTemperature}°C",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            HorizontalDivider(thickness = 1.dp, color = Color.Gray)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
