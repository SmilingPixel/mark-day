package io.github.smiling_pixel.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.smiling_pixel.client.UserInfo
import io.github.smiling_pixel.client.getCloudDriveClient
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.filesystem.name
import io.github.smiling_pixel.filesystem.readBytes
import io.github.smiling_pixel.filesystem.rememberFilePicker
import io.github.smiling_pixel.getPlatform
import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.preference.getSettingsRepository
import io.github.smiling_pixel.sync.DiaryEntryExportResult
import io.github.smiling_pixel.sync.DiaryEntryImportFile
import io.github.smiling_pixel.sync.DiaryEntryImportPreview
import io.github.smiling_pixel.sync.DiaryEntryImportResult
import io.github.smiling_pixel.sync.applyDiaryEntryImport
import io.github.smiling_pixel.sync.exportDiaryEntries
import io.github.smiling_pixel.sync.performCloudSync
import io.github.smiling_pixel.sync.previewDiaryEntryImport
import io.github.smiling_pixel.util.LogExportResult
import io.github.smiling_pixel.util.LogLevel
import io.github.smiling_pixel.util.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(repo: DiaryRepository) {
    val scope = rememberCoroutineScope()
    // Remember the settings repository so recomposition does not recreate a new DataStore-backed
    // repository instance and resubscribe all mapped flows unnecessarily.
    val settingsRepository = remember { getSettingsRepository() }
    val themeMode by settingsRepository.themeMode.collectAsState(
        initial = io.github.smiling_pixel.theme.ThemeMode.SYSTEM,
    )
    val isPureBlackEnabled by settingsRepository.isPureBlackEnabled.collectAsState(initial = false)
    val apiKey by settingsRepository.googleWeatherApiKey.collectAsState(initial = null)
    val uriHandler = LocalUriHandler.current

    val isCloudSyncEnabled by settingsRepository.isCloudSyncEnabled.collectAsState(initial = false)
    val isAutoSyncEnabled by settingsRepository.isAutoSyncEnabled.collectAsState(initial = false)
    val cloudSyncPath by settingsRepository.cloudSyncPath.collectAsState(initial = "/MarkDay")
    val logLevel by settingsRepository.logLevel.collectAsState(initial = LogLevel.ERROR)
    val isLogPersistenceEnabled by settingsRepository.isLogPersistenceEnabled.collectAsState(initial = false)
    val platform = remember { getPlatform() }
    val isWebTrial = platform.name.contains("Web", ignoreCase = true)
    val isDiaryImportAvailable =
        remember(platform.name) {
            platform.name.contains("Android", ignoreCase = true) || platform.name.contains("Java", ignoreCase = true)
        }

    val cloudDriveClient = remember { getCloudDriveClient() }
    var userInfo by remember { mutableStateOf<UserInfo?>(null) }
    var isAuthorized by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var diagnosticsMessage by remember { mutableStateOf<String?>(null) }
    var isCheckingAuth by remember { mutableStateOf(false) }
    var pendingImportPreview by remember { mutableStateOf<DiaryEntryImportPreview?>(null) }

    suspend fun applyImportPreview(
        preview: DiaryEntryImportPreview,
        overrideConflicts: Boolean,
    ) {
        val result = applyDiaryEntryImport(preview, repo, overrideConflicts)
        // DiaryRepository writes to the DAO first, then its StateFlow is refreshed by a separate
        // entriesFlow collector. Build the exact entries changed by this import so post-import sync
        // can wait for those changes instead of racing against a potentially stale repo.entries.value.
        val changedEntries =
            preview.newEntries +
                if (overrideConflicts) {
                    preview.conflicts.map { it.importedEntry }
                } else {
                    emptyList()
                }
        diagnosticsMessage =
            buildImportDiagnosticsMessage(
                result = result,
                syncMessage =
                    runSyncAfterImport(
                        changedEntries = changedEntries,
                        isCloudSyncEnabled = isCloudSyncEnabled,
                        isAuthorized = isAuthorized,
                        repo = repo,
                        sync = { localEntries ->
                            performCloudSync(
                                client = cloudDriveClient,
                                repo = repo,
                                localEntries = localEntries,
                            )
                        },
                    ),
            )
    }

    val diaryImportPicker =
        rememberFilePicker { platformFiles ->
            scope.launch {
                if (!isDiaryImportAvailable) {
                    diagnosticsMessage = "Diary entry import is unavailable on this platform."
                    return@launch
                }

                val files =
                    platformFiles.map { file ->
                        DiaryEntryImportFile(
                            name = file.name(),
                            content = file.readBytes(),
                        )
                    }
                val preview = previewDiaryEntryImport(files, repo.entries.value)
                if (!preview.hasImportableEntries) {
                    diagnosticsMessage =
                        "No diary entries to import. Ignored ${preview.invalidFileNames.size} invalid files."
                    return@launch
                }

                if (preview.conflicts.isNotEmpty()) {
                    pendingImportPreview = preview
                } else {
                    applyImportPreview(preview, overrideConflicts = false)
                }
            }
        }

    val checkAuthStatus by rememberUpdatedState {
        // use isCheckingAuth to prevent concurrent execution of the authentication status check
        if (!isCheckingAuth) {
            isCheckingAuth = true
            scope.launch {
                try {
                    isAuthorized = cloudDriveClient.isAuthorized()
                    if (isAuthorized) {
                        userInfo = cloudDriveClient.getUserInfo()
                    } else {
                        userInfo = null
                    }
                } catch (e: CancellationException) {
                    // Don't catch structured concurrency cancellation exceptions
                    throw e
                } catch (e: Exception) {
                    isAuthorized = false
                    userInfo = null
                    // Fail silently on background check or set error if critical
                    // errorMessage = "Failed to refresh status: ${e.message}"
                } finally {
                    isCheckingAuth = false
                }
            }
        }
    }

    pendingImportPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { pendingImportPreview = null },
            title = { Text("Import Conflicts") },
            text = {
                Text(
                    "Found ${preview.conflicts.size} diary entries that already exist locally. " +
                        "Override all conflicts or skip them?",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingImportPreview = null
                        scope.launch {
                            applyImportPreview(preview, overrideConflicts = true)
                        }
                    },
                ) {
                    Text("Override All")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        pendingImportPreview = null
                        scope.launch {
                            applyImportPreview(preview, overrideConflicts = false)
                        }
                    },
                ) {
                    Text("Skip Conflicts")
                }
            },
        )
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        checkAuthStatus()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(32.dp))

        /*
         * A section to allow the user to select their preferred Theme Mode.
         * The user can select from SYSTEM, LIGHT, or DARK modes using a group of rounded rectangles.
         */
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            io.github.smiling_pixel.theme.ThemeMode.entries.forEach { mode ->
                val isSelected = themeMode == mode
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = {
                                    scope.launch {
                                        settingsRepository.setThemeMode(mode)
                                    }
                                },
                            ).background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ).then(
                                if (isSelected) {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                } else {
                                    Modifier
                                },
                            ).padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = "Pure Black Mode",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Use pure black backgrounds in dark mode instead of dark gray, optimizing for OLED screens.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = isPureBlackEnabled,
                onCheckedChange = { isChecked ->
                    scope.launch {
                        settingsRepository.setPureBlackEnabled(isChecked)
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Third-party Services",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = apiKey ?: "",
            onValueChange = { newKey ->
                scope.launch {
                    settingsRepository.setGoogleWeatherApiKey(newKey)
                }
            },
            label = { Text("Google Weather API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Cloud Drive Sync",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))

        errorMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (isAuthorized) {
            Text(
                text = "Connected to Google Drive",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Signed in as: ${userInfo?.name ?: "Loading..."}")
            Text("Email: ${userInfo?.email ?: ""}")

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = cloudSyncPath,
                onValueChange = {
                    scope.launch {
                        settingsRepository.setCloudSyncPath(it)
                    }
                },
                label = { Text("Cloud Sync Save Path") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            ) // TODO: better user experience for selecting folder in Google Drive @SmilingPixel

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        settingsRepository.setCloudSyncEnabled(!isCloudSyncEnabled)
                    }
                },
            ) {
                Text(if (isCloudSyncEnabled) "Disable Cloud Sync" else "Enable Cloud Sync")
            }
            if (isCloudSyncEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            settingsRepository.setAutoSyncEnabled(!isAutoSyncEnabled)
                        }
                    },
                ) {
                    Text(if (isAutoSyncEnabled) "Disable Auto Sync" else "Enable Auto Sync")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            cloudDriveClient.signOut()
                            isAuthorized = false
                            userInfo = null
                            settingsRepository.setCloudSyncEnabled(false)
                            settingsRepository.setAutoSyncEnabled(false)
                        } catch (e: CancellationException) {
                            // Don't catch structured concurrency cancellation exceptions
                            throw e
                        } catch (e: Exception) {
                            errorMessage = "Sign out failed: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Revoke Authorization")
            }
        } else {
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            if (cloudDriveClient.authorize()) {
                                isAuthorized = true
                                userInfo = cloudDriveClient.getUserInfo()
                            } else {
                                errorMessage = "Authorization was cancelled or failed."
                            }
                        } catch (e: CancellationException) {
                            // Don't catch structured concurrency cancellation exceptions
                            throw e
                        } catch (e: Exception) {
                            errorMessage = "Authorization error: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Connect to Google Drive")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Diagnostics",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Log Level",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogLevel.entries.forEach { level ->
                val isSelected = logLevel == level
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = {
                                    scope.launch {
                                        settingsRepository.setLogLevel(level)
                                        diagnosticsMessage = "Log level set to ${level.name}."
                                    }
                                },
                            ).background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ).then(
                                if (isSelected) {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                } else {
                                    Modifier
                                },
                            ).padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = level.name,
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = "Persist Logs",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        if (isWebTrial) {
                            "Persistence is unavailable on the web trial. Console logging still works."
                        } else {
                            "Store filtered logs locally so they can be exported for troubleshooting."
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = if (isWebTrial) false else isLogPersistenceEnabled,
                onCheckedChange = { isChecked ->
                    scope.launch {
                        settingsRepository.setLogPersistenceEnabled(isChecked)
                        diagnosticsMessage =
                            if (isChecked) {
                                "Log persistence is unavailable on this platform."
                            } else if (isChecked) {
                                "Log persistence enabled."
                            } else {
                                "Log persistence disabled."
                            }
                    }
                },
                enabled = !isWebTrial,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    if (isDiaryImportAvailable) {
                        diaryImportPicker.launch()
                    } else {
                        diagnosticsMessage = "Diary entry import is unavailable on this platform."
                    }
                },
            ) {
                Text("Import Diary Entries")
            }
            Button(
                onClick = {
                    scope.launch {
                        diagnosticsMessage =
                            when (val result = exportDiaryEntries(repo.entries.value)) {
                                is DiaryEntryExportResult.Success -> {
                                    "Exported ${result.fileCount} diary entries to ${result.destinationDescription}."
                                }
                                DiaryEntryExportResult.NoEntries -> "No diary entries to export."
                                DiaryEntryExportResult.Unavailable -> {
                                    "Diary entry export is unavailable on this platform."
                                }
                                is DiaryEntryExportResult.Failure -> result.message
                            }
                    }
                },
            ) {
                Text("Export Diary Entries")
            }
            Button(
                onClick = {
                    scope.launch {
                        diagnosticsMessage =
                            when (val result = Logger.exportPersistedLogs()) {
                                is LogExportResult.Success -> "Logs exported to ${result.destinationDescription}."
                                LogExportResult.NoLogs -> "No logs to export."
                                LogExportResult.Unavailable -> "Log export is unavailable on this platform."
                                is LogExportResult.Failure -> result.message
                            }
                    }
                },
            ) {
                Text("Export Logs")
            }
            Button(
                onClick = {
                    scope.launch {
                        Logger.clearPersistedLogs()
                        diagnosticsMessage = "Logs cleared."
                    }
                },
            ) {
                Text("Clear Logs")
            }
        }
        diagnosticsMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "About",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "MarkDay Diary App v1.0.0",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "A cross-platform diary application built with Kotlin Multiplatform and Compose.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "View on GitHub",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier.clickable {
                    uriHandler.openUri("https://github.com/SmilingPixel/mark-day")
                },
        )
    }
}

/**
 * Runs cloud sync after import using local entries that include the just-applied import changes.
 *
 * The repository's public [DiaryRepository.entries] value is a StateFlow updated asynchronously from
 * the DAO. Immediately reading it after [applyDiaryEntryImport] can miss newly inserted entries or
 * still contain the pre-override version of a conflicting entry. To avoid syncing stale data, this
 * function waits briefly until the expected imported sync IDs and update timestamps are visible, then
 * falls back to a merged snapshot if the repository flow has not caught up yet.
 */
private suspend fun runSyncAfterImport(
    changedEntries: List<DiaryEntry>,
    isCloudSyncEnabled: Boolean,
    isAuthorized: Boolean,
    repo: DiaryRepository,
    sync: suspend (List<DiaryEntry>) -> io.github.smiling_pixel.sync.SyncResult,
): String {
    if (changedEntries.isEmpty()) {
        return "Cloud sync skipped because no entries changed."
    }
    if (!isCloudSyncEnabled) {
        return "Cloud sync skipped because cloud sync is disabled."
    }
    if (!isAuthorized) {
        return "Cloud sync skipped because Google Drive is not authorized."
    }

    return try {
        val localEntries = awaitLocalEntriesAfterImport(repo, changedEntries)
        val result = sync(localEntries)
        "Cloud sync completed. Uploaded: ${result.uploaded}; Downloaded: ${result.downloaded}; Unchanged: ${result.unchanged}."
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        "Cloud sync failed after import: ${e.message ?: "unknown error"}."
    }
}

/**
 * Waits for repository state to reflect imported entries before cloud sync reads local state.
 */
private suspend fun awaitLocalEntriesAfterImport(
    repo: DiaryRepository,
    changedEntries: List<DiaryEntry>,
): List<DiaryEntry> {
    val expectedBySyncId = changedEntries.associateBy { it.syncId }
    return withTimeoutOrNull(2_000) {
        repo.entries.first { entries ->
            expectedBySyncId.all { (syncId, expected) ->
                entries.any { entry -> entry.syncId == syncId && entry.updatedAt == expected.updatedAt }
            }
        }
    } ?: mergeChangedEntries(repo.entries.value, changedEntries)
}

/**
 * Merges imported entries into the latest repository snapshot when the repository flow lags.
 */
private fun mergeChangedEntries(
    localEntries: List<DiaryEntry>,
    changedEntries: List<DiaryEntry>,
): List<DiaryEntry> {
    val changedBySyncId = changedEntries.associateBy { it.syncId }
    val merged =
        localEntries.map { local ->
            val changed = changedBySyncId[local.syncId]
            if (changed == null) {
                local
            } else {
                changed.copy(id = local.id)
            }
        }
    val existingSyncIds = localEntries.mapTo(mutableSetOf()) { it.syncId }
    return merged + changedEntries.filter { it.syncId !in existingSyncIds }
}

private fun buildImportDiagnosticsMessage(
    result: DiaryEntryImportResult,
    syncMessage: String,
): String =
    "Import complete. Inserted: ${result.inserted}; Updated: ${result.updated}; " +
        "Skipped conflicts: ${result.skippedConflicts}; Ignored invalid files: ${result.ignoredInvalidFiles}; " +
        "Skipped duplicate files: ${result.skippedDuplicateFiles}. $syncMessage"
