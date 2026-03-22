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
    // TODO: Ideally, implement Android WorkManager for guaranteed background execution.
    // For now, running a periodic coroutine in the app's lifecycle to sync every 15 minutes.
    CoroutineScope(Dispatchers.Default).launch {
        while (isActive) {
            delay(15 * 60 * 1000L) // 15 mins
            try {
                val settings = getSettingsRepository()
                if (settings.isCloudSyncEnabled.first()) {
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
