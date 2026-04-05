package io.github.smiling_pixel.sync

import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.client.getCloudDriveClient
import io.github.smiling_pixel.preference.getSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger

private val autoSyncLogger: Logger = Logger.getLogger("AutoSync")

actual fun startAutoSync(repo: DiaryRepository) {
    // JVM platform: runs a simple timer loop in the background while the application is alive.
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
                autoSyncLogger.log(
                    Level.SEVERE,
                    AUTO_SYNC_ERROR_MESSAGE,
                    e,
                )
                nextDelayMs = (nextDelayMs * 2).coerceAtMost(AUTO_SYNC_MAX_BACKOFF_MS)
            }
        }
    }
}
