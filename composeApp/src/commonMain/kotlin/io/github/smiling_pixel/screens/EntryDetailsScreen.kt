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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import io.github.smiling_pixel.client.WeatherClient
import io.github.smiling_pixel.draft.DEFAULT_DRAFT_AUTOSAVE_DEBOUNCE_MILLIS
import io.github.smiling_pixel.draft.DraftSaveState
import io.github.smiling_pixel.draft.EditorExitGuard
import io.github.smiling_pixel.draft.EntryDraft
import io.github.smiling_pixel.draft.EntryDraftKey
import io.github.smiling_pixel.draft.EntryDraftRepository
import io.github.smiling_pixel.draft.debounceDraftChanges
import io.github.smiling_pixel.filesystem.fileManager
import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.model.Location
import io.github.smiling_pixel.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
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
 * @param isSyncing Whether a cloud synchronization operation is running.
 * @param onSyncRequest Callback that requests cloud synchronization.
 * @param draftRepository Device-local repository used for interrupted editor drafts.
 * @param onExitGuardChange Reports the active editor's protection callback to its parent.
 * @param onSave Suspends while committing the entry and returns the canonical saved value.
 * @param onCancel Callback invoked when the user cancels editing or goes back.
 */
@OptIn(ExperimentalTime::class, ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailsScreen(
    entry: DiaryEntry?,
    weatherClient: WeatherClient,
    isSyncing: Boolean = false,
    onSyncRequest: () -> Unit = {},
    draftRepository: EntryDraftRepository,
    onExitGuardChange: (EditorExitGuard?) -> Unit = {},
    onSave: suspend (DiaryEntry) -> DiaryEntry,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // The editor has two recovery layers. These saveable values handle short-lived UI recreation, while the draft
    // repository below survives disposal of this composable and application restarts. A new editor keeps the generated
    // identity saveable so every autosave and the eventual committed entry refer to the same logical entry.
    val editorKey = entry?.syncId ?: NEW_ENTRY_EDITOR_KEY
    var createdAtEpochMilliseconds by rememberSaveable(editorKey) {
        mutableStateOf(entry?.createdAt?.toEpochMilliseconds() ?: Clock.System.now().toEpochMilliseconds())
    }
    var targetSyncId by rememberSaveable(editorKey) {
        mutableStateOf(
            entry?.syncId ?: io.github.smiling_pixel.util
                .generateSyncId(),
        )
    }
    var isEditing by rememberSaveable(editorKey) { mutableStateOf(entry == null) }
    var title by rememberSaveable(editorKey) { mutableStateOf(entry?.title ?: "") }
    var content by rememberSaveable(editorKey) { mutableStateOf(entry?.content ?: "") }
    var entryDateText by rememberSaveable(editorKey) {
        mutableStateOf(
            (
                entry?.entryDate ?: Instant
                    .fromEpochMilliseconds(createdAtEpochMilliseconds)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            ).toString(),
        )
    }
    var showDatePicker by rememberSaveable(editorKey) { mutableStateOf(false) }
    var weatherCondition by rememberSaveable(editorKey) { mutableStateOf(entry?.weatherCondition ?: "") }
    var minTemp by rememberSaveable(editorKey) { mutableStateOf(entry?.minTemperature) }
    var maxTemp by rememberSaveable(editorKey) { mutableStateOf(entry?.maxTemperature) }
    var isHydrated by rememberSaveable(editorKey) { mutableStateOf(false) }
    var saveState by rememberSaveable(editorKey) { mutableStateOf(DraftSaveState.IDLE) }
    var editorError by rememberSaveable(editorKey) { mutableStateOf<String?>(null) }
    var isCommitting by remember(editorKey) { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showUnsafeExitDialog by remember { mutableStateOf(false) }
    var pendingConflictDraft by remember { mutableStateOf<EntryDraft?>(null) }

    val draftKey = entry?.syncId?.let(EntryDraftKey::ExistingEntry) ?: EntryDraftKey.NewEntry
    // The baseline is the committed entry (or the untouched initial new-entry form). Equality with it means there is no
    // meaningful recovery data to retain, so persistSnapshot removes any previously parked draft.
    val baseline =
        remember(entry, targetSyncId, createdAtEpochMilliseconds) {
            EntryFormSnapshot.fromEntry(entry, targetSyncId, createdAtEpochMilliseconds, entryDateText)
        }

    fun currentSnapshot(): EntryFormSnapshot =
        EntryFormSnapshot(
            targetSyncId = targetSyncId,
            title = title,
            content = content,
            entryDate = entryDateText,
            weatherCondition = weatherCondition.ifBlank { null },
            minTemperature = minTemp,
            maxTemperature = maxTemp,
            createdAtEpochMilliseconds = createdAtEpochMilliseconds,
        )

    fun applySnapshot(snapshot: EntryFormSnapshot) {
        targetSyncId = snapshot.targetSyncId
        createdAtEpochMilliseconds = snapshot.createdAtEpochMilliseconds
        title = snapshot.title
        content = snapshot.content
        entryDateText = snapshot.entryDate
        weatherCondition = snapshot.weatherCondition.orEmpty()
        minTemp = snapshot.minTemperature
        maxTemp = snapshot.maxTemperature
    }

    suspend fun persistSnapshot(snapshot: EntryFormSnapshot): Boolean =
        try {
            if (snapshot == baseline) {
                draftRepository.delete(draftKey)
                saveState = DraftSaveState.IDLE
            } else {
                draftRepository.upsert(
                    snapshot.toDraft(
                        sourceEntry = entry,
                        updatedAtEpochMilliseconds = Clock.System.now().toEpochMilliseconds(),
                    ),
                )
                // A write may complete after the user has typed again. Report SAVED only for the exact snapshot that is
                // still visible; otherwise leave the state pending for the next debounced write.
                saveState = if (currentSnapshot() == snapshot) DraftSaveState.SAVED else DraftSaveState.SAVING
            }
            editorError = null
            true
        } catch (e: Exception) {
            Logger.e("EntryDetailsScreen", "Draft persistence failed: $e")
            saveState = DraftSaveState.FAILED
            editorError = "Couldn’t save draft."
            false
        }

    suspend fun hydrateEditor() {
        try {
            val draft = draftRepository.load(draftKey)
            // Existing-entry drafts are optimistic edits based on a particular updatedAt revision. Never overwrite a
            // newer synced/committed revision silently.
            if (
                draft != null &&
                entry != null &&
                draft.sourceUpdatedAtEpochMilliseconds != entry.updatedAt.toEpochMilliseconds()
            ) {
                pendingConflictDraft = draft
                return
            }
            if (draft != null) {
                applySnapshot(EntryFormSnapshot.fromDraft(draft))
                saveState = DraftSaveState.SAVED
            }
            isHydrated = true
        } catch (e: Exception) {
            Logger.e("EntryDetailsScreen", "Draft restoration failed: $e")
            editorError = "Couldn’t restore the saved draft."
            saveState = DraftSaveState.FAILED
            isHydrated = true
        }
    }

    LaunchedEffect(editorKey, isEditing) {
        // Fields and autosave stay disabled until this finishes. Observing or editing defaults before hydration could
        // overwrite the durable draft that is about to be restored.
        if (isEditing && !isHydrated && pendingConflictDraft == null) {
            hydrateEditor()
        }
    }

    LaunchedEffect(editorKey, isEditing, isHydrated) {
        if (isEditing && isHydrated) {
            // Saved-state recreation can restore a form while its previous debounce was pending. Requeue that snapshot
            // before normal observation so a configuration change does not strand it indefinitely in SAVING.
            if (saveState == DraftSaveState.SAVING) {
                delay(DEFAULT_DRAFT_AUTOSAVE_DEBOUNCE_MILLIS)
                persistSnapshot(currentSnapshot())
            }
            snapshotFlow { currentSnapshot() }
                .debounceDraftChanges { saveState = DraftSaveState.SAVING }
                .collect { persistSnapshot(it) }
        }
    }

    val persistLatest: suspend () -> Boolean = {
        val snapshot = currentSnapshot()
        saveState = DraftSaveState.SAVING
        persistSnapshot(snapshot)
    }
    val hasUnpersistedChanges = isEditing && saveState in setOf(DraftSaveState.SAVING, DraftSaveState.FAILED)

    fun closeEditor() {
        if (entry == null) {
            onCancel()
        } else {
            isEditing = false
            isHydrated = false
        }
    }

    fun requestClose() {
        scope.launch {
            if (!hasUnpersistedChanges || persistLatest()) {
                closeEditor()
            } else {
                showUnsafeExitDialog = true
            }
        }
    }

    val latestPersist by rememberUpdatedState(persistLatest)
    val latestClose by rememberUpdatedState<() -> Unit> { requestClose() }
    // Keep the guard object stable while giving platform handlers the newest lambdas. Recreating it on every keystroke
    // would repeatedly install/remove platform effects and could feed unnecessary recompositions back into App.
    val exitGuard =
        remember(isEditing, hasUnpersistedChanges) {
            if (isEditing) {
                EditorExitGuard(
                    hasUnpersistedChanges = hasUnpersistedChanges,
                    persistLatest = { latestPersist() },
                    requestClose = { latestClose() },
                )
            } else {
                null
            }
        }

    DisposableEffect(exitGuard) {
        onExitGuardChange(exitGuard)
        onDispose { onExitGuardChange(null) }
    }

    if (pendingConflictDraft != null && entry != null) {
        val conflictDraft = pendingConflictDraft!!
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Entry changed") },
            text = {
                Text(
                    "This entry changed after the draft was saved. Keep the draft or reload the current entry.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val acceptedDraft =
                            conflictDraft.copy(
                                sourceUpdatedAtEpochMilliseconds = entry.updatedAt.toEpochMilliseconds(),
                                draftUpdatedAtEpochMilliseconds = Clock.System.now().toEpochMilliseconds(),
                            )
                        val acceptedSnapshot = EntryFormSnapshot.fromDraft(acceptedDraft)
                        applySnapshot(acceptedSnapshot)
                        pendingConflictDraft = null
                        isHydrated = true
                        persistSnapshot(acceptedSnapshot)
                    }
                }) { Text("Keep draft") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            draftRepository.delete(draftKey)
                            applySnapshot(baseline)
                            saveState = DraftSaveState.IDLE
                            pendingConflictDraft = null
                            isHydrated = true
                        } catch (e: Exception) {
                            Logger.e("EntryDetailsScreen", "Stale draft deletion failed: $e")
                            editorError = "Couldn’t discard the old draft."
                        }
                    }
                }) { Text("Reload current") }
            },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard draft?") },
            text = { Text("The saved draft and all uncommitted changes will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            draftRepository.delete(draftKey)
                            showDiscardDialog = false
                            applySnapshot(baseline)
                            saveState = DraftSaveState.IDLE
                            closeEditor()
                        } catch (e: Exception) {
                            Logger.e("EntryDetailsScreen", "Draft deletion failed: $e")
                            editorError = "Couldn’t discard the draft."
                        }
                    }
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") } },
        )
    }

    if (showUnsafeExitDialog) {
        AlertDialog(
            onDismissRequest = { showUnsafeExitDialog = false },
            title = { Text("Draft not saved") },
            text = { Text("The latest changes couldn’t be saved. Leaving now may lose them.") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsafeExitDialog = false
                    closeEditor()
                }) { Text("Leave anyway") }
            },
            dismissButton = { TextButton(onClick = { showUnsafeExitDialog = false }) { Text("Stay") } },
        )
    }

    val entryDate = LocalDate.parse(entryDateText)

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
                        entryDateText =
                            Instant
                                .fromEpochMilliseconds(millis)
                                .toLocalDateTime(TimeZone.UTC)
                                .date
                                .toString()
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
                TextButton(onClick = { showDiscardDialog = true }, enabled = !isCommitting) {
                    Text("Discard")
                }
                TextButton(onClick = { requestClose() }, enabled = !isCommitting) {
                    Text("Back")
                }
                Button(
                    enabled = isHydrated && !isCommitting,
                    onClick = {
                        scope.launch {
                            isCommitting = true
                            editorError = null
                            val now = Clock.System.now()
                            val candidate =
                                entry?.copy(
                                    title = title,
                                    content = content,
                                    updatedAt = now,
                                    entryDate = entryDate,
                                    weatherCondition = weatherCondition.ifBlank { null },
                                    minTemperature = minTemp,
                                    maxTemperature = maxTemp,
                                ) ?: DiaryEntry(
                                    id = 0,
                                    syncId = targetSyncId,
                                    title = title,
                                    content = content,
                                    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMilliseconds),
                                    updatedAt = now,
                                    entryDate = entryDate,
                                    weatherCondition = weatherCondition.ifBlank { null },
                                    minTemperature = minTemp,
                                    maxTemperature = maxTemp,
                                )
                            try {
                                // Draft persistence is attempted first but does not block an explicit diary Save. The
                                // diary commit is itself durable; stable targetSyncId lets startup recognize a committed
                                // new entry if the following best-effort draft deletion is interrupted or fails.
                                persistLatest()
                                onSave(candidate)
                                try {
                                    draftRepository.delete(draftKey)
                                } catch (e: Exception) {
                                    Logger.w("EntryDetailsScreen", "Entry saved but draft cleanup failed: $e")
                                }
                                saveState = DraftSaveState.IDLE
                                isEditing = false
                            } catch (e: Exception) {
                                Logger.e("EntryDetailsScreen", "Entry commit failed: $e")
                                editorError = "Couldn’t save entry. Your draft is still available."
                            } finally {
                                isCommitting = false
                            }
                        }
                    },
                ) {
                    if (isCommitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save")
                    }
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
                TextButton(onClick = {
                    isHydrated = false
                    isEditing = true
                }) {
                    Text("Edit")
                }
                TextButton(onClick = onCancel) {
                    Text("Back")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isEditing) {
            val statusText =
                when (saveState) {
                    DraftSaveState.IDLE -> null
                    DraftSaveState.SAVING -> "Saving…"
                    DraftSaveState.SAVED -> "Saved"
                    DraftSaveState.FAILED -> "Couldn’t save"
                }
            if (statusText != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (saveState == DraftSaveState.FAILED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                    if (saveState == DraftSaveState.FAILED) {
                        TextButton(onClick = { scope.launch { persistLatest() } }) { Text("Retry") }
                    }
                }
            }
            if (editorError != null) {
                Text(
                    text = editorError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                enabled = isHydrated,
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
                        .clickable(enabled = isHydrated) { showDatePicker = true }
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
                    enabled = isHydrated,
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
                IconButton(
                    enabled = isHydrated,
                    onClick = {
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
                    },
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Weather")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                enabled = isHydrated,
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
            val createdLocal = entry.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
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

private data class EntryFormSnapshot(
    val targetSyncId: String,
    val title: String,
    val content: String,
    val entryDate: String,
    val weatherCondition: String?,
    val minTemperature: Double?,
    val maxTemperature: Double?,
    val createdAtEpochMilliseconds: Long,
) {
    fun toDraft(
        sourceEntry: DiaryEntry?,
        updatedAtEpochMilliseconds: Long,
    ): EntryDraft =
        EntryDraft(
            targetSyncId = targetSyncId,
            sourceEntrySyncId = sourceEntry?.syncId,
            sourceUpdatedAtEpochMilliseconds = sourceEntry?.updatedAt?.toEpochMilliseconds(),
            title = title,
            content = content,
            entryDate = entryDate,
            weatherCondition = weatherCondition,
            minTemperature = minTemperature,
            maxTemperature = maxTemperature,
            createdAtEpochMilliseconds = createdAtEpochMilliseconds,
            draftUpdatedAtEpochMilliseconds = updatedAtEpochMilliseconds,
        )

    companion object {
        fun fromEntry(
            entry: DiaryEntry?,
            targetSyncId: String,
            initialNow: Long,
            initialDate: String,
        ): EntryFormSnapshot =
            EntryFormSnapshot(
                targetSyncId = targetSyncId,
                title = entry?.title.orEmpty(),
                content = entry?.content.orEmpty(),
                entryDate = entry?.entryDate?.toString() ?: initialDate,
                weatherCondition = entry?.weatherCondition,
                minTemperature = entry?.minTemperature,
                maxTemperature = entry?.maxTemperature,
                createdAtEpochMilliseconds = entry?.createdAt?.toEpochMilliseconds() ?: initialNow,
            )

        fun fromDraft(draft: EntryDraft): EntryFormSnapshot =
            EntryFormSnapshot(
                targetSyncId = draft.targetSyncId,
                title = draft.title,
                content = draft.content,
                entryDate = draft.entryDate,
                weatherCondition = draft.weatherCondition,
                minTemperature = draft.minTemperature,
                maxTemperature = draft.maxTemperature,
                createdAtEpochMilliseconds = draft.createdAtEpochMilliseconds,
            )
    }
}

private const val NEW_ENTRY_EDITOR_KEY = "new-entry"

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
