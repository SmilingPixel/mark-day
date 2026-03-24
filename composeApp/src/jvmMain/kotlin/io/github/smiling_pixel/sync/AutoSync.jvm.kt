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

actual fun startAutoSync(repo: DiaryRepository) {
    // JVM platform: runs a simple timer loop in the background while the application is alive.
    CoroutineScope(Dispatchers.Default).launch {
        while (isActive) {
            delay(15 * 60 * 1000L) // 15 mins
            try {
                val settings = getSettingsRepository()
                if (settings.isCloudSyncEnabled.first() && settings.isAutoSyncEnabled.first()) {
                    val client = getCloudDriveClient()
                    if (client.isAuthorized()) {
                        performCloudSync(client, repo, repo.entries.value)
                    }
                }
            } catch (e: Exception) {
                // Ignore silent sync errors
            }
        }
    }
}
