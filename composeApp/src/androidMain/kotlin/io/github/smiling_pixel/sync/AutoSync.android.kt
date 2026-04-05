package io.github.smiling_pixel.sync

import android.util.Log
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.client.getCloudDriveClient
import io.github.smiling_pixel.preference.getSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

actual fun startAutoSync(repo: DiaryRepository) {
    // TODO: Ideally, implement Android WorkManager for guaranteed background execution.
    // For now, running a periodic coroutine in the app's lifecycle to sync every 15 minutes.
    CoroutineScope(Dispatchers.Default).launch {
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
            } catch (e: Exception) {
                Log.e(
                    AUTO_SYNC_LOG_TAG,
                    AUTO_SYNC_ERROR_MESSAGE,
                    e,
                )
                nextDelayMs = (nextDelayMs * 2).coerceAtMost(AUTO_SYNC_MAX_BACKOFF_MS)
            }
        }
    }
}
