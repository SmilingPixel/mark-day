package io.github.smiling_pixel.preference

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val googleWeatherApiKey: Flow<String?>
    suspend fun setGoogleWeatherApiKey(key: String?)

    val isCloudSyncEnabled: Flow<Boolean>
    suspend fun setCloudSyncEnabled(enabled: Boolean)

    val isAutoSyncEnabled: Flow<Boolean>
    suspend fun setAutoSyncEnabled(enabled: Boolean)

    val cloudSyncPath: Flow<String>
    suspend fun setCloudSyncPath(path: String)

    val cloudSyncDeletionTombstonesJson: Flow<String?>
    suspend fun setCloudSyncDeletionTombstonesJson(value: String?)
}

expect fun getSettingsRepository(): SettingsRepository
