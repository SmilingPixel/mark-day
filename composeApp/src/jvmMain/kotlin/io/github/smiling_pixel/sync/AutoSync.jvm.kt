package io.github.smiling_pixel.sync

import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.client.getCloudDriveClient
import io.github.smiling_pixel.preference.getSettingsRepository
import io.github.smiling_pixel.util.Logger
import io.github.smiling_pixel.util.e
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private var autoSyncJob: Job? = null

actual fun startAutoSync(repo: DiaryRepository): Job? {
    autoSyncJob?.takeIf { it.isActive }?.let { return it }

    // JVM platform: runs a simple timer loop in the background while the application is alive.
    val job = CoroutineScope(Dispatchers.Default).launch {
        var nextDelayMs = AUTO_SYNC_INTERVAL_MS
        while (isActive) {
            delay(nextDelayMs)
            try {
                val settings = getSettingsRepository()
                if (settings.isCloudSyncEnabled.first() && settings.isAutoSyncEnabled.first()) {
                    val client = getCloudDriveClient()
                    if (client.isAuthorized()) {
                        performCloudSync(client, repo, repo.entries.value)
                    }
                }
                nextDelayMs = AUTO_SYNC_INTERVAL_MS
            } catch (e: CancellationException) {
                // Do not treat normal coroutine cancellation as a sync failure.
                throw e
            } catch (e: Exception) {
                Logger.e(AUTO_SYNC_LOG_TAG, AUTO_SYNC_ERROR_MESSAGE, e)
                nextDelayMs = (nextDelayMs * 2).coerceAtMost(AUTO_SYNC_MAX_BACKOFF_MS)
            }
        }
    }

    autoSyncJob = job
    job.invokeOnCompletion {
        if (autoSyncJob === job) {
            autoSyncJob = null
        }
    }

    return job
}
