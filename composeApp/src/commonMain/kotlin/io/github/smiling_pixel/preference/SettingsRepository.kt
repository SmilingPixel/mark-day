package io.github.smiling_pixel.preference

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val googleWeatherApiKey: Flow<String?>
    suspend fun setGoogleWeatherApiKey(key: String?)
}

expect fun getSettingsRepository(): SettingsRepository
