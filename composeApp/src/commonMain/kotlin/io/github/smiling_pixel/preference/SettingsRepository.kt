package io.github.smiling_pixel.preference

import io.github.smiling_pixel.theme.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /**
     * A flow emitting the current [ThemeMode] setting of the application.
     * Default is [ThemeMode.SYSTEM].
     */
    val themeMode: Flow<ThemeMode>

    /**
     * Updates the application's theme mode setting.
     * @param mode The new [ThemeMode] to be applied.
     */
    suspend fun setThemeMode(mode: ThemeMode)

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
