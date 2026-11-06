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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.collectAsState
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
import io.github.smiling_pixel.filesystem.FileRepository
import io.github.smiling_pixel.location.CurrentLocationResult
import io.github.smiling_pixel.location.rememberCurrentLocationRequester
import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.model.Location
import io.github.smiling_pixel.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
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
 * @param fileRepo Repository containing local Moment attachments.
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
    fileRepo: FileRepository,
    isSyncing: Boolean = false,
    onSyncRequest: () -> Unit = {},
    draftRepository: EntryDraftRepository,
    onExitGuardChange: (EditorExitGuard?) -> Unit = {},
    onSave: suspend (DiaryEntry, Set<Long>) -> DiaryEntry,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val allMoments by fileRepo.files.collectAsState(initial = emptyList())
    val allMomentLinks by fileRepo.links.collectAsState(initial = emptyList())
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
    var moodEmoji by rememberSaveable(editorKey) { mutableStateOf(entry?.moodEmoji) }
    var showMoodMenu by rememberSaveable(editorKey) { mutableStateOf(false) }
    var isHydrated by rememberSaveable(editorKey) { mutableStateOf(false) }
    var saveState by rememberSaveable(editorKey) { mutableStateOf(DraftSaveState.IDLE) }
    var editorError by rememberSaveable(editorKey) { mutableStateOf<String?>(null) }
    var isCommitting by remember(editorKey) { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showUnsafeExitDialog by remember { mutableStateOf(false) }
    var pendingConflictDraft by remember { mutableStateOf<EntryDraft?>(null) }
    var selectedMomentIds by rememberSaveable(editorKey) { mutableStateOf<List<Long>>(emptyList()) }
    var showAttachmentPicker by remember { mutableStateOf(false) }
    var showWeatherDialog by remember(editorKey) { mutableStateOf(false) }
    var weatherDialogState by remember(editorKey) { mutableStateOf(WeatherDialogState.READY) }
    var latitudeText by remember(editorKey) { mutableStateOf("") }
    var longitudeText by remember(editorKey) { mutableStateOf("") }
    var latitudeError by remember(editorKey) { mutableStateOf<String?>(null) }
    var longitudeError by remember(editorKey) { mutableStateOf<String?>(null) }

    val draftKey = entry?.syncId?.let(EntryDraftKey::ExistingEntry) ?: EntryDraftKey.NewEntry
    // The baseline is the committed entry (or the untouched initial new-entry form). Equality with it means there is no
    // meaningful recovery data to retain, so persistSnapshot removes any previously parked draft.
    var baseline by remember(entry, targetSyncId, createdAtEpochMilliseconds) {
        mutableStateOf(EntryFormSnapshot.fromEntry(entry, targetSyncId, createdAtEpochMilliseconds, entryDateText))
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
            moodEmoji = moodEmoji,
            createdAtEpochMilliseconds = createdAtEpochMilliseconds,
            momentIds = selectedMomentIds,
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
        moodEmoji = snapshot.moodEmoji
        selectedMomentIds = snapshot.momentIds
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
            val persistedMomentIds = entry?.let { fileRepo.getFileIdsForEntry(it.syncId) }.orEmpty()
            baseline =
                EntryFormSnapshot.fromEntry(
                    entry,
                    targetSyncId,
                    createdAtEpochMilliseconds,
                    entryDateText,
                    persistedMomentIds,
                )
            selectedMomentIds = persistedMomentIds.toList()
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

    fun applyWeather(values: WeatherValues) {
        weatherCondition = values.condition.orEmpty()
        minTemp = values.minTemperature
        maxTemp = values.maxTemperature
    }

    fun fetchWeather(location: Location) {
        scope.launch {
            weatherDialogState = WeatherDialogState.LOADING
            try {
                if (!weatherClient.isConfigured()) {
                    weatherDialogState = WeatherDialogState.API_KEY_MISSING
                    return@launch
                }
                val values =
                    fetchWeatherForDate(
                        weatherClient = weatherClient,
                        location = location,
                        targetDate = entryDate,
                        now = Clock.System.now(),
                        timeZone = TimeZone.currentSystemDefault(),
                    )
                if (values == null) {
                    weatherDialogState = WeatherDialogState.UNAVAILABLE
                } else {
                    applyWeather(values)
                    showWeatherDialog = false
                    weatherDialogState = WeatherDialogState.READY
                }
            } catch (e: Exception) {
                Logger.e("EntryDetailsScreen", "Weather fetch failed: $e")
                weatherDialogState = WeatherDialogState.UNAVAILABLE
            }
        }
    }

    val currentLocationRequester =
        rememberCurrentLocationRequester { result ->
            when (result) {
                is CurrentLocationResult.Success -> fetchWeather(result.location)
                CurrentLocationResult.PermissionDenied -> {
                    weatherDialogState = WeatherDialogState.PERMISSION_DENIED
                }
                CurrentLocationResult.Unavailable,
                CurrentLocationResult.Unsupported,
                -> weatherDialogState = WeatherDialogState.UNAVAILABLE
            }
        }

    fun openWeatherDialog() {
        latitudeText = ""
        longitudeText = ""
        latitudeError = null
        longitudeError = null
        showWeatherDialog = true
        weatherDialogState = WeatherDialogState.LOADING
        scope.launch {
            weatherDialogState =
                try {
                    if (weatherClient.isConfigured()) {
                        WeatherDialogState.READY
                    } else {
                        WeatherDialogState.API_KEY_MISSING
                    }
                } catch (e: Exception) {
                    Logger.e("EntryDetailsScreen", "Weather configuration check failed: $e")
                    WeatherDialogState.UNAVAILABLE
                }
        }
    }

    if (showWeatherDialog) {
        WeatherLocationDialog(
            targetDate = entryDate,
            state = weatherDialogState,
            supportsCurrentLocation = currentLocationRequester.isSupported,
            latitude = latitudeText,
            longitude = longitudeText,
            latitudeError = latitudeError,
            longitudeError = longitudeError,
            onLatitudeChange = {
                latitudeText = it
                latitudeError = null
            },
            onLongitudeChange = {
                longitudeText = it
                longitudeError = null
            },
            onUseCurrentLocation = {
                scope.launch {
                    weatherDialogState = WeatherDialogState.LOADING
                    try {
                        if (weatherClient.isConfigured()) {
                            // Permission is requested only here, after the user has read the explanation and opted in.
                            currentLocationRequester.request()
                        } else {
                            weatherDialogState = WeatherDialogState.API_KEY_MISSING
                        }
                    } catch (e: Exception) {
                        Logger.e("EntryDetailsScreen", "Weather configuration check failed: $e")
                        weatherDialogState = WeatherDialogState.UNAVAILABLE
                    }
                }
            },
            onFetchManual = {
                when (val result = parseWeatherLocation(latitudeText, longitudeText)) {
                    is LocationInputResult.Valid -> fetchWeather(result.location)
                    is LocationInputResult.Invalid -> {
                        latitudeError = result.latitudeError
                        longitudeError = result.longitudeError
                    }
                }
            },
            onDismiss = { showWeatherDialog = false },
        )
    }

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

    if (showAttachmentPicker) {
        EntryAttachmentPicker(
            fileRepo = fileRepo,
            files = allMoments,
            initialSelection = selectedMomentIds.toSet(),
            onConfirm = {
                selectedMomentIds = it.toList()
                showAttachmentPicker = false
            },
            onDismiss = { showAttachmentPicker = false },
        )
    }

    val displayedMomentIds =
        if (isEditing) {
            selectedMomentIds.toSet()
        } else {
            allMomentLinks.filter { it.entrySyncId == entry?.syncId }.mapTo(mutableSetOf()) { it.fileId }
        }
    val attachedMoments = allMoments.filter { it.id in displayedMomentIds }

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
                                    moodEmoji = moodEmoji,
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
                                    moodEmoji = moodEmoji,
                                )
                            try {
                                // Draft persistence is attempted first but does not block an explicit diary
                                // Save. The diary commit is itself durable; stable targetSyncId lets startup
                                // recognize a committed new entry if the following best-effort draft deletion
                                // is interrupted or fails.
                                persistLatest()
                                onSave(candidate, selectedMomentIds.toSet())
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Mood",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = isHydrated,
                    onClick = { showMoodMenu = true },
                ) {
                    Text(moodEmoji ?: "None")
                }
                DropdownMenu(
                    expanded = showMoodMenu,
                    onDismissRequest = { showMoodMenu = false },
                ) {
                    MOOD_OPTIONS.forEach { (emoji, label) ->
                        DropdownMenuItem(
                            text = { Text("$emoji  $label") },
                            onClick = {
                                moodEmoji = emoji
                                showMoodMenu = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Clear mood") },
                        onClick = {
                            moodEmoji = null
                            showMoodMenu = false
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            val hasWeather = weatherCondition.isNotBlank() || minTemp != null || maxTemp != null
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Weather", style = MaterialTheme.typography.titleSmall)
                    if (hasWeather) {
                        WeatherSummaryText(weatherCondition.ifBlank { null }, minTemp, maxTemp)
                    } else {
                        Text(
                            "No weather added",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = { openWeatherDialog() }, enabled = isHydrated) {
                    Text(if (hasWeather) "Replace" else "Add weather")
                }
                if (hasWeather) {
                    TextButton(
                        enabled = isHydrated,
                        onClick = { applyWeather(emptyWeatherValues()) },
                    ) {
                        Text("Remove")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Attachments", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAttachmentPicker = true }, enabled = isHydrated) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Attach")
                }
            }
            EntryAttachmentStrip(
                files = attachedMoments,
                editable = true,
                onRemove = { fileId -> selectedMomentIds = selectedMomentIds.filterNot { it == fileId } },
            )
            if (attachedMoments.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                enabled = isHydrated,
                label = { Text("Content") },
                placeholder = { Text("Type anything... Markdown is supported.") },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val displayedEntry = requireNotNull(entry)
            val fileCount = attachedMoments.size
            val totalFileSize = attachedMoments.sumOf { it.sizeBytes.coerceAtLeast(0) }

            // show timestamps
            val createdLocal = displayedEntry.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
            val updatedLocal = displayedEntry.updatedAt.toLocalDateTime(TimeZone.currentSystemDefault())

            Text(
                text = "Date: ${displayedEntry.entryDate}",
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

            val statsText = "${displayedEntry.content.length} chars | $fileCount files, ${formatBytes(totalFileSize)}"
            Text(
                text = statsText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            EntryAttachmentStrip(files = attachedMoments, editable = false)
            if (attachedMoments.isNotEmpty()) Spacer(modifier = Modifier.height(12.dp))

            if (
                !displayedEntry.weatherCondition.isNullOrBlank() ||
                displayedEntry.minTemperature != null ||
                displayedEntry.maxTemperature != null
            ) {
                WeatherSummaryText(
                    condition = displayedEntry.weatherCondition,
                    minTemperature = displayedEntry.minTemperature,
                    maxTemperature = displayedEntry.maxTemperature,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (displayedEntry.moodEmoji != null) {
                Text(
                    text = "Mood: ${displayedEntry.moodEmoji}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(modifier = Modifier.height(12.dp))

            Markdown(
                content = displayedEntry.content,
                imageTransformer = Coil3ImageTransformerImpl,
            )
        }
    }
}

@Composable
private fun WeatherSummaryText(
    condition: String?,
    minTemperature: Double?,
    maxTemperature: Double?,
) {
    val parts =
        buildList {
            condition?.takeIf { it.isNotBlank() }?.let(::add)
            when {
                minTemperature != null && maxTemperature != null -> add("$minTemperature°C – $maxTemperature°C")
                minTemperature != null -> add("Minimum $minTemperature°C")
                maxTemperature != null -> add("Maximum $maxTemperature°C")
            }
        }
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun WeatherLocationDialog(
    targetDate: LocalDate,
    state: WeatherDialogState,
    supportsCurrentLocation: Boolean,
    latitude: String,
    longitude: String,
    latitudeError: String?,
    longitudeError: String?,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onFetchManual: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isLoading = state == WeatherDialogState.LOADING
    val inputsEnabled = !isLoading && state != WeatherDialogState.API_KEY_MISSING

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Add weather") },
        text = {
            Column {
                Text(
                    "MarkDay needs a coordinate to retrieve weather for $targetDate. " +
                        "The coordinate is used for this lookup only and is not saved.",
                )
                Spacer(Modifier.height(12.dp))

                when (state) {
                    WeatherDialogState.LOADING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Loading weather…")
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    WeatherDialogState.API_KEY_MISSING -> {
                        WeatherDialogMessage("Add a Google Weather API key in Settings before adding weather.")
                    }
                    WeatherDialogState.PERMISSION_DENIED -> {
                        WeatherDialogMessage("Location permission was denied. You can enter coordinates manually.")
                    }
                    WeatherDialogState.UNAVAILABLE -> {
                        WeatherDialogMessage("Weather is unavailable for this location and date. Try again later.")
                    }
                    WeatherDialogState.READY -> Unit
                }

                if (supportsCurrentLocation) {
                    Button(
                        onClick = onUseCurrentLocation,
                        enabled = inputsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Use current location")
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Or enter coordinates", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                }

                // TODO: Add city selection and resolve/display a human-readable location in a future iteration.
                OutlinedTextField(
                    value = latitude,
                    onValueChange = onLatitudeChange,
                    enabled = inputsEnabled,
                    isError = latitudeError != null,
                    label = { Text("Latitude") },
                    supportingText = latitudeError?.let { error -> ({ Text(error) }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = onLongitudeChange,
                    enabled = inputsEnabled,
                    isError = longitudeError != null,
                    label = { Text("Longitude") },
                    supportingText = longitudeError?.let { error -> ({ Text(error) }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onFetchManual, enabled = inputsEnabled) {
                Text("Fetch weather")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun WeatherDialogMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(12.dp))
}

private enum class WeatherDialogState {
    READY,
    LOADING,
    API_KEY_MISSING,
    PERMISSION_DENIED,
    UNAVAILABLE,
}

private data class EntryFormSnapshot(
    val targetSyncId: String,
    val title: String,
    val content: String,
    val entryDate: String,
    val weatherCondition: String?,
    val minTemperature: Double?,
    val maxTemperature: Double?,
    val moodEmoji: String?,
    val createdAtEpochMilliseconds: Long,
    val momentIds: List<Long>,
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
            moodEmoji = moodEmoji,
            createdAtEpochMilliseconds = createdAtEpochMilliseconds,
            draftUpdatedAtEpochMilliseconds = updatedAtEpochMilliseconds,
            momentIds = momentIds,
        )

    companion object {
        fun fromEntry(
            entry: DiaryEntry?,
            targetSyncId: String,
            initialNow: Long,
            initialDate: String,
            momentIds: Set<Long> = emptySet(),
        ): EntryFormSnapshot =
            EntryFormSnapshot(
                targetSyncId = targetSyncId,
                title = entry?.title.orEmpty(),
                content = entry?.content.orEmpty(),
                entryDate = entry?.entryDate?.toString() ?: initialDate,
                weatherCondition = entry?.weatherCondition,
                minTemperature = entry?.minTemperature,
                maxTemperature = entry?.maxTemperature,
                moodEmoji = entry?.moodEmoji,
                createdAtEpochMilliseconds = entry?.createdAt?.toEpochMilliseconds() ?: initialNow,
                momentIds = momentIds.toList(),
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
                moodEmoji = draft.moodEmoji,
                createdAtEpochMilliseconds = draft.createdAtEpochMilliseconds,
                momentIds = draft.momentIds,
            )
    }
}

private const val NEW_ENTRY_EDITOR_KEY = "new-entry"

private val MOOD_OPTIONS =
    listOf(
        "😀" to "Joyful",
        "😄" to "Excited",
        "🙂" to "Content",
        "😐" to "Neutral",
        "😔" to "Sad",
        "😢" to "Grieving",
        "😡" to "Angry",
        "😰" to "Anxious",
        "😴" to "Tired",
        "🤩" to "Inspired",
    )

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
