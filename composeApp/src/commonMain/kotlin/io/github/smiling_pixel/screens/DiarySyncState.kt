package io.github.smiling_pixel.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.smiling_pixel.client.getCloudDriveClient
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.sync.performCloudSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class DiarySyncState(
    private val repo: DiaryRepository,
    private val scope: CoroutineScope,
) {
    var isSyncing by mutableStateOf(false)
        private set

    var summary by mutableStateOf<String?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun requestSync() {
        if (isSyncing) return
        isSyncing = true
        scope.launch {
            try {
                val result =
                    performCloudSync(
                        client = getCloudDriveClient(),
                        repo = repo,
                        localEntries = repo.entries.value,
                    )
                summary =
                    "Sync completed!\nUploaded: ${result.uploaded}\nDownloaded: ${result.downloaded}" +
                        "\nUnchanged: ${result.unchanged}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "An unknown error occurred during sync"
            } finally {
                isSyncing = false
            }
        }
    }

    fun dismissSummary() {
        summary = null
    }

    fun dismissError() {
        error = null
    }
}

@Composable
internal fun rememberDiarySyncState(repo: DiaryRepository): DiarySyncState {
    val scope = rememberCoroutineScope()
    return remember(repo, scope) { DiarySyncState(repo, scope) }
}

@Composable
internal fun DiarySyncDialogs(state: DiarySyncState) {
    state.summary?.let { summary ->
        AlertDialog(
            onDismissRequest = state::dismissSummary,
            title = { Text("Sync Summary") },
            text = { Text(summary) },
            confirmButton = { Button(onClick = state::dismissSummary) { Text("OK") } },
        )
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = state::dismissError,
            title = { Text("Sync Error") },
            text = { Text(error) },
            confirmButton = { Button(onClick = state::dismissError) { Text("OK") } },
        )
    }
}
