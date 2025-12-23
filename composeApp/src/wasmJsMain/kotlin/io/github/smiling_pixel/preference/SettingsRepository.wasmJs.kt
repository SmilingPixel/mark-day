package io.github.smiling_pixel.preference

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WasmJsSettingsRepository : SettingsRepository {
    private val _apiKey = MutableStateFlow(localStorage.getItem("weather_api_key"))
    override val googleWeatherApiKey: Flow<String?> = _apiKey.asStateFlow()

    override suspend fun setGoogleWeatherApiKey(key: String?) {
        if (key != null) {
            localStorage.setItem("weather_api_key", key)
        } else {
            localStorage.removeItem("weather_api_key")
        }
        _apiKey.value = key
    }
}

actual fun getSettingsRepository(): SettingsRepository = WasmJsSettingsRepository()
