package io.github.smiling_pixel.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.smiling_pixel.client.CloudDriveClient
import io.github.smiling_pixel.client.getCloudDriveClient
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.sync.performCloudSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Current availability of cloud synchronization. */
enum class SyncAvailability { Checking, Available, NotConnected, Unsupported, Offline }

/** Coordinates cloud-drive capability checks and manual sync requests. */
internal class DiarySyncState(
    private val repo: DiaryRepository,
    private val scope: CoroutineScope,
    private val onEvent: (OperationEvent) -> Unit,
    private val client: CloudDriveClient = getCloudDriveClient(),
) {
    var isSyncing by mutableStateOf(false)
        private set
    var availability by mutableStateOf(SyncAvailability.Checking)
        private set

    /** Refreshes authorization and capability state without prompting the user. */
    fun refreshAvailability() {
        if (!client.isSupported) {
            availability = SyncAvailability.Unsupported
            return
        }
        scope.launch {
            try {
                availability = if (client.isAuthorized()) SyncAvailability.Available else SyncAvailability.NotConnected
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                availability = SyncAvailability.NotConnected
                onEvent(OperationEvent("Google Drive status could not be checked.", e.message, ::refreshAvailability))
            }
        }
    }

    /** Starts a manual sync, or emits an explanatory event when it cannot run. */
    fun requestSync() {
        if (isSyncing) return
        if (!client.isSupported) {
            availability = SyncAvailability.Unsupported
            onEvent(OperationEvent("Google Drive is unavailable on this platform."))
            return
        }
        isSyncing = true
        scope.launch {
            try {
                if (!client.isAuthorized()) {
                    availability = SyncAvailability.NotConnected
                    onEvent(OperationEvent("Connect Google Drive in Settings."))
                    return@launch
                }
                availability = SyncAvailability.Available
                val result = performCloudSync(client, repo, repo.entries.value)
                onEvent(
                    OperationEvent(
                        "Sync complete. Uploaded ${result.uploaded}, downloaded ${result.downloaded}, unchanged ${result.unchanged}.",
                        result.warnings.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isLikelyOffline(e)) {
                    availability = SyncAvailability.Offline
                    onEvent(OperationEvent("You’re offline; local changes are safe and will sync later.", e.message, ::requestSync))
                } else {
                    onEvent(OperationEvent("Sync could not be completed.", e.message, ::requestSync))
                }
            } finally {
                isSyncing = false
            }
        }
    }
}

/** Returns whether an exception is likely caused by a temporary network outage. */
internal fun isLikelyOffline(error: Throwable): Boolean {
    val text = (error.message.orEmpty() + " " + error.cause?.message.orEmpty()).lowercase()
    return listOf("network", "offline", "timeout", "timed out", "connection", "unreachable", "unknown host")
        .any(text::contains)
}

@Composable
internal fun rememberDiarySyncState(repo: DiaryRepository, onEvent: (OperationEvent) -> Unit): DiarySyncState {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val state = remember(repo, scope) { DiarySyncState(repo, scope, onEvent) }
    LaunchedEffect(state) { state.refreshAvailability() }
    return state
}
