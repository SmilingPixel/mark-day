package io.github.smiling_pixel.preference

import io.github.smiling_pixel.theme.ThemeMode
import io.github.smiling_pixel.util.LogLevel
import kotlinx.coroutines.flow.Flow

/**
 * Provides persisted user settings for the application.
 */
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

    val isPureBlackEnabled: Flow<Boolean>

    suspend fun setPureBlackEnabled(enabled: Boolean)

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

    /**
     * A flow emitting the minimum log level used by the application logger.
     */
    val logLevel: Flow<LogLevel>

    /**
     * Updates the minimum log level used by the application logger.
     *
     * @param level The minimum log severity to emit.
     */
    suspend fun setLogLevel(level: LogLevel)

    /**
     * A flow emitting whether filtered logs should be persisted.
     */
    val isLogPersistenceEnabled: Flow<Boolean>

    /**
     * Updates whether filtered logs should be persisted.
     *
     * @param enabled Whether log persistence should be enabled.
     */
    suspend fun setLogPersistenceEnabled(enabled: Boolean)
}

/**
 * Returns the platform-specific settings repository.
 */
expect fun getSettingsRepository(): SettingsRepository
