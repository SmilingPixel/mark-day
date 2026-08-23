package io.github.smiling_pixel

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil3.compose.setSingletonImageLoaderFactory
import io.github.smiling_pixel.client.GoogleWeatherClient
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.database.InMemoryDiaryDao
import io.github.smiling_pixel.database.InMemoryFileMetadataDao
import io.github.smiling_pixel.draft.EditorExitGuard
import io.github.smiling_pixel.draft.EntryDraftRepository
import io.github.smiling_pixel.draft.InMemoryEntryDraftRepository
import io.github.smiling_pixel.draft.PlatformDraftExitProtection
import io.github.smiling_pixel.draft.getEntryDraftRepository
import io.github.smiling_pixel.filesystem.FileRepository
import io.github.smiling_pixel.filesystem.InMemoryFileManager
import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.preference.getSettingsRepository
import io.github.smiling_pixel.screens.DiarySyncDialogs
import io.github.smiling_pixel.screens.EntriesScreen
import io.github.smiling_pixel.screens.InsightsScreen
import io.github.smiling_pixel.screens.MomentsScreen
import io.github.smiling_pixel.screens.ProfileScreen
import io.github.smiling_pixel.screens.SearchScreen
import io.github.smiling_pixel.screens.SettingsScreen
import io.github.smiling_pixel.screens.rememberDiarySyncState
import io.github.smiling_pixel.sync.startAutoSync
import io.github.smiling_pixel.theme.MarkDayTheme
import io.github.smiling_pixel.theme.ThemeMode
import io.github.smiling_pixel.util.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute

@Serializable
object EntriesRoute : AppRoute

/** Destination for searching and filtering diary entries. */
@Serializable
object SearchRoute : AppRoute

@Serializable
object MomentsRoute : AppRoute

@Serializable
object InsightsRoute : AppRoute

@Serializable
object SettingsRoute : AppRoute

@Serializable
object ProfileRoute : AppRoute

/**
 * Displays the MarkDay application.
 *
 * @param providedRepo Optional diary repository used by hosts, tests, and previews.
 * @param providedFileRepo Optional file repository used by hosts, tests, and previews.
 * @param providedDraftRepository Optional durable draft repository used by hosts, tests, and previews.
 * @param onExitGuardChange Reports the active editor guard to a platform window host.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    providedRepo: io.github.smiling_pixel.database.DiaryRepository? = null,
    providedFileRepo: FileRepository? = null,
    providedDraftRepository: EntryDraftRepository? = null,
    onExitGuardChange: (EditorExitGuard?) -> Unit = {},
) {
    // Memoize the image loader factory to prevent it from being re-created on every recomposition.
    // While setSingletonImageLoaderFactory is a @Composable, we use remember here to ensure the
    // factory lambda remains stable across theme changes and other UI updates.
    val imageLoaderFactory =
        remember {
            { context: coil3.PlatformContext -> getAsyncImageLoader(context) }
        }
    setSingletonImageLoaderFactory(imageLoaderFactory)

    val settingsRepository = remember { getSettingsRepository() }
    val themeMode by settingsRepository.themeMode.collectAsState(initial = null)
    val isPureBlackEnabled by settingsRepository.isPureBlackEnabled.collectAsState(initial = null)
    val isCloudSyncEnabled by settingsRepository.isCloudSyncEnabled.collectAsState(initial = false)
    val logLevel by settingsRepository.logLevel.collectAsState(initial = null)
    val isLogPersistenceEnabled by settingsRepository.isLogPersistenceEnabled.collectAsState(initial = null)

    if (themeMode == null || isPureBlackEnabled == null || logLevel == null || isLogPersistenceEnabled == null) {
        return
    }

    LaunchedEffect(logLevel, isLogPersistenceEnabled) {
        Logger.setLogLevel(logLevel!!)
        Logger.setPersistenceEnabled(isLogPersistenceEnabled!!)
    }

    val useDarkTheme = themeMode == ThemeMode.DARK || (themeMode == ThemeMode.SYSTEM && isSystemInDarkTheme())

    MarkDayTheme(
        useDarkTheme = useDarkTheme,
        isPureBlack = isPureBlackEnabled!!,
    ) {
        val repo = providedRepo ?: remember { DiaryRepository(InMemoryDiaryDao()) }
        val fileRepo =
            providedFileRepo ?: remember {
                FileRepository(InMemoryFileManager(), InMemoryFileMetadataDao())
            }
        val draftRepository =
            providedDraftRepository ?: remember(providedRepo) {
                if (providedRepo == null) InMemoryEntryDraftRepository() else getEntryDraftRepository()
            }
        val weatherClient = remember { GoogleWeatherClient(settingsRepository) }
        val scope = rememberCoroutineScope()
        val diarySyncState = rememberDiarySyncState(repo)
        val snackbarHostState = remember { SnackbarHostState() }
        val navController = rememberNavController()
        var selected by remember { mutableStateOf<AppRoute>(EntriesRoute) }
        // remember previous to return from profile
        var previous by remember { mutableStateOf<AppRoute>(EntriesRoute) }

        var isSelectionMode by remember { mutableStateOf(false) }
        var selectedIds by remember { mutableStateOf(emptySet<String>()) }
        var isEntriesListVisible by remember { mutableStateOf(false) }
        var searchSelectedEntrySyncId by rememberSaveable { mutableStateOf<String?>(null) }
        var editorExitGuard by remember { mutableStateOf<EditorExitGuard?>(null) }
        var showUnsafeNavigationDialog by remember { mutableStateOf(false) }
        var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
        var showDeleteConfirmation by remember { mutableStateOf(false) }
        var pendingDeletedEntries by remember { mutableStateOf<List<DiaryEntry>>(emptyList()) }
        var undoToken by remember { mutableStateOf(0) }
        var undoSnackbarJob by remember { mutableStateOf<Job?>(null) }

        DisposableEffect(repo) {
            val autoSyncJob = startAutoSync(repo)
            onDispose { autoSyncJob?.cancel() }
        }
        DiarySyncDialogs(diarySyncState)

        PlatformDraftExitProtection(
            guard = editorExitGuard,
            onBackRequest =
                if (selected == SearchRoute) {
                    {
                        if (searchSelectedEntrySyncId != null) {
                            searchSelectedEntrySyncId = null
                        } else {
                            selected = EntriesRoute
                            navController.popBackStack()
                        }
                    }
                } else {
                    null
                },
        )
        // Desktop owns its Window outside this composable, so publish the same guard used by in-app navigation to the
        // host. DisposableEffect also clears stale callbacks when the Entries destination leaves composition.
        DisposableEffect(editorExitGuard) {
            onExitGuardChange(editorExitGuard)
            onDispose { onExitGuardChange(null) }
        }

        fun requestNavigation(action: () -> Unit) {
            val guard = editorExitGuard
            if (guard?.hasUnpersistedChanges != true) {
                action()
                return
            }
            // Bypass the remaining debounce before changing destinations. Only persistence failure produces a dialog;
            // already-saved drafts are intentionally retained and navigation remains silent.
            scope.launch {
                if (guard.persistLatest()) {
                    action()
                } else {
                    pendingNavigation = action
                    showUnsafeNavigationDialog = true
                }
            }
        }

        if (showUnsafeNavigationDialog) {
            AlertDialog(
                onDismissRequest = {
                    showUnsafeNavigationDialog = false
                    pendingNavigation = null
                },
                title = { Text("Draft not saved") },
                text = { Text("The latest changes couldn’t be saved. Leaving now may lose them.") },
                confirmButton = {
                    TextButton(onClick = {
                        val navigation = pendingNavigation
                        showUnsafeNavigationDialog = false
                        pendingNavigation = null
                        navigation?.invoke()
                    }) { Text("Leave anyway") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showUnsafeNavigationDialog = false
                        pendingNavigation = null
                    }) { Text("Stay") }
                },
            )
        }

        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Delete ${selectedIds.size} entries?") },
                text = {
                    Text(
                        if (isCloudSyncEnabled) {
                            "Cloud Sync is enabled. This deletion will also propagate to Google Drive on the next sync."
                        } else {
                            "Cloud Sync is disabled. This deletion is local for now, but it may propagate to Google " +
                                "Drive if Cloud Sync is enabled later."
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmation = false
                            scope.launch {
                                val entriesToDelete = repo.getAll().filter { it.syncId in selectedIds }
                                if (entriesToDelete.isEmpty()) {
                                    isSelectionMode = false
                                    selectedIds = emptySet()
                                    return@launch
                                }

                                entriesToDelete.forEach { repo.delete(it) }
                                isSelectionMode = false
                                selectedIds = emptySet()
                                pendingDeletedEntries = entriesToDelete
                                undoToken += 1
                                val token = undoToken
                                undoSnackbarJob?.cancel()
                                snackbarHostState.currentSnackbarData?.dismiss()
                                undoSnackbarJob =
                                    scope.launch {
                                        val result =
                                            snackbarHostState.showSnackbar(
                                                message = "${entriesToDelete.size} entries deleted",
                                                actionLabel = "Undo",
                                                duration = SnackbarDuration.Short,
                                            )
                                        if (result == SnackbarResult.ActionPerformed && token == undoToken) {
                                            pendingDeletedEntries.forEach { repo.restore(it) }
                                        }
                                        if (token == undoToken) {
                                            pendingDeletedEntries = emptyList()
                                        }
                                    }
                            }
                        },
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
                },
            )
        }

        LaunchedEffect(selected) {
            isSelectionMode = false
            selectedIds = emptySet()
        }

        Scaffold(
            modifier =
                Modifier
                    .safeContentPadding()
                    .fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (isSelectionMode) {
                    CenterAlignedTopAppBar(
                        title = { Text("${selectedIds.size} Selected") },
                        navigationIcon = {
                            IconButton(onClick = {
                                isSelectionMode = false
                                selectedIds = emptySet()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Selection")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                showDeleteConfirmation = true
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                            }
                        },
                    )
                } else {
                    CenterAlignedTopAppBar(
                        title = {
                            val title =
                                when (selected) {
                                    EntriesRoute -> "Entries"
                                    SearchRoute -> "Search"
                                    MomentsRoute -> "Moments"
                                    InsightsRoute -> "Insights"
                                    SettingsRoute -> "Settings"
                                    ProfileRoute -> "Profile"
                                }
                            Text(title)
                        },
                        navigationIcon = {
                            if (selected == SearchRoute) {
                                IconButton(onClick = {
                                    requestNavigation {
                                        if (searchSelectedEntrySyncId != null) {
                                            searchSelectedEntrySyncId = null
                                        } else {
                                            selected = EntriesRoute
                                            navController.popBackStack()
                                        }
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        },
                        actions = {
                            if (selected == EntriesRoute && isEntriesListVisible) {
                                IconButton(onClick = {
                                    searchSelectedEntrySyncId = null
                                    selected = SearchRoute
                                    navController.navigate(SearchRoute)
                                }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search entries")
                                }
                            }
                            if (selected != ProfileRoute && selected != SearchRoute) {
                                IconButton(onClick = {
                                    requestNavigation {
                                        previous = selected
                                        selected = ProfileRoute
                                        navController.navigate(ProfileRoute)
                                    }
                                }) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
                                }
                            }
                        },
                    )
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selected == EntriesRoute || selected == SearchRoute,
                        onClick = {
                            requestNavigation {
                                if (selected == SearchRoute) {
                                    searchSelectedEntrySyncId = null
                                    selected = EntriesRoute
                                    navController.popBackStack()
                                } else {
                                    selected = EntriesRoute
                                    navController.navigate(EntriesRoute)
                                }
                            }
                        },
                        icon = { Text("E") },
                        label = { Text("Entries") },
                    )
                    NavigationBarItem(
                        selected = selected == MomentsRoute,
                        onClick = {
                            requestNavigation {
                                selected = MomentsRoute
                                navController.navigate(MomentsRoute)
                            }
                        },
                        icon = { Text("M") },
                        label = { Text("Moments") },
                    )
                    NavigationBarItem(
                        selected = selected == InsightsRoute,
                        onClick = {
                            requestNavigation {
                                selected = InsightsRoute
                                navController.navigate(InsightsRoute)
                            }
                        },
                        icon = { Text("I") },
                        label = { Text("Insights") },
                    )
                    NavigationBarItem(
                        selected = selected == SettingsRoute,
                        onClick = {
                            requestNavigation {
                                selected = SettingsRoute
                                navController.navigate(SettingsRoute)
                            }
                        },
                        icon = { Text("S") },
                        label = { Text("Settings") },
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                NavHost(navController = navController, startDestination = EntriesRoute) {
                    composable<EntriesRoute> {
                        EntriesScreen(
                            repo = repo,
                            draftRepository = draftRepository,
                            weatherClient = weatherClient,
                            isSelectionMode = isSelectionMode,
                            selectedIds = selectedIds,
                            onSelectionModeChange = { isSelectionMode = it },
                            onSelectionChange = { selectedIds = it },
                            isSyncing = diarySyncState.isSyncing,
                            onSyncRequest = diarySyncState::requestSync,
                            onListVisibilityChange = { isEntriesListVisible = it },
                            onExitGuardChange = { editorExitGuard = it },
                        )
                    }
                    composable<SearchRoute> {
                        SearchScreen(
                            repo = repo,
                            draftRepository = draftRepository,
                            weatherClient = weatherClient,
                            selectedEntrySyncId = searchSelectedEntrySyncId,
                            onSelectedEntryChange = { searchSelectedEntrySyncId = it },
                            isSyncing = diarySyncState.isSyncing,
                            onSyncRequest = diarySyncState::requestSync,
                            onExitGuardChange = { editorExitGuard = it },
                        )
                    }
                    composable<MomentsRoute> {
                        MomentsScreen(fileRepo = fileRepo)
                    }
                    composable<InsightsRoute> {
                        InsightsScreen()
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(repo = repo)
                    }
                    composable<ProfileRoute> { backStackEntry ->
                        ProfileScreen(onBack = {
                            // return to previous selection when profile is dismissed
                            selected = previous
                            navController.popBackStack()
                        })
                    }
                }
            }
        }
    }
}
