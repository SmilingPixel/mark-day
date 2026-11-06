package io.github.smiling_pixel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.smiling_pixel.client.GoogleWeatherClient
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.draft.EditorExitGuard
import io.github.smiling_pixel.draft.EntryDraftRepository
import io.github.smiling_pixel.filesystem.FileRepository
import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.screens.EntriesScreen
import io.github.smiling_pixel.screens.EntryDetailsScreen

/** Resolves a typed entry route and renders its editor, optionally beside the Entries list on wide screens. */
@Composable
internal fun EntryDetailsDestination(
    route: AppRoute,
    repo: DiaryRepository,
    fileRepo: FileRepository,
    draftRepository: EntryDraftRepository,
    weatherClient: GoogleWeatherClient,
    isSyncing: Boolean,
    onSyncRequest: () -> Unit,
    onExitGuardChange: (EditorExitGuard?) -> Unit,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onSave: suspend (DiaryEntry, Set<Long>) -> DiaryEntry,
) {
    val entries by repo.entries.collectAsState()
    val entry = (route as? EntryDetailsRoute)?.let { requested ->
        entries.firstOrNull { it.syncId == requested.syncId }
    }
    if (route is EntryDetailsRoute && entry == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("This entry is no longer available.")
        }
        return
    }

    val origin = when (route) {
        is EntryDetailsRoute -> route.origin
        is NewEntryRoute -> route.origin
        else -> AppTab.ENTRIES
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (origin == AppTab.ENTRIES && maxWidth >= 840.dp) {
            // The route remains the source of truth for the selected detail while the list becomes a companion pane.
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.42f).fillMaxSize()) {
                    EntriesScreen(
                        repo = repo,
                        isSelectionMode = false,
                        selectedIds = emptySet(),
                        onSelectionModeChange = {},
                        onSelectionChange = {},
                        isSyncing = isSyncing,
                        onSyncRequest = onSyncRequest,
                        onOpenEntry = onOpenEntry,
                        onCreateEntry = {},
                    )
                }
                Box(Modifier.weight(0.58f).fillMaxSize()) {
                    EntryDetailsScreen(
                        entry = entry,
                        weatherClient = weatherClient,
                        fileRepo = fileRepo,
                        isSyncing = isSyncing,
                        onSyncRequest = onSyncRequest,
                        draftRepository = draftRepository,
                        onExitGuardChange = onExitGuardChange,
                        onSave = onSave,
                        onCancel = onBack,
                    )
                }
            }
        } else {
            EntryDetailsScreen(
                entry = entry,
                weatherClient = weatherClient,
                fileRepo = fileRepo,
                isSyncing = isSyncing,
                onSyncRequest = onSyncRequest,
                draftRepository = draftRepository,
                onExitGuardChange = onExitGuardChange,
                onSave = onSave,
                onCancel = onBack,
            )
        }
    }
}
