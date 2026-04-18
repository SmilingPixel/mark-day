package io.github.smiling_pixel.preference

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WasmJsSettingsRepository : SettingsRepository {
    private val _apiKey = MutableStateFlow(localStorage.getItem("weather_api_key"))
    private val _isCloudSyncEnabled = MutableStateFlow(localStorage.getItem("is_cloud_sync_enabled") == "true")
    private val _isAutoSyncEnabled = MutableStateFlow(localStorage.getItem("is_auto_sync_enabled") == "true")
    private val _cloudSyncPath = MutableStateFlow(localStorage.getItem("cloud_sync_path") ?: "/MarkDay")
    private val _cloudSyncDeletionTombstonesJson =
        MutableStateFlow(localStorage.getItem("cloud_sync_deletion_tombstones_json"))

    override val googleWeatherApiKey: Flow<String?> = _apiKey.asStateFlow()

    override suspend fun setGoogleWeatherApiKey(key: String?) {
        if (key != null) {
            localStorage.setItem("weather_api_key", key)
        } else {
            localStorage.removeItem("weather_api_key")
        }
        _apiKey.value = key
    }

    override val isCloudSyncEnabled: Flow<Boolean> = _isCloudSyncEnabled.asStateFlow()

    override suspend fun setCloudSyncEnabled(enabled: Boolean) {
        localStorage.setItem("is_cloud_sync_enabled", enabled.toString())
        _isCloudSyncEnabled.value = enabled
    }

    override val isAutoSyncEnabled: Flow<Boolean> = _isAutoSyncEnabled.asStateFlow()

    override suspend fun setAutoSyncEnabled(enabled: Boolean) {
        localStorage.setItem("is_auto_sync_enabled", enabled.toString())
        _isAutoSyncEnabled.value = enabled
    }

    override val cloudSyncPath: Flow<String> = _cloudSyncPath.asStateFlow()

    override suspend fun setCloudSyncPath(path: String) {
        localStorage.setItem("cloud_sync_path", path)
        _cloudSyncPath.value = path
    }

    override val cloudSyncDeletionTombstonesJson: Flow<String?> = _cloudSyncDeletionTombstonesJson.asStateFlow()

    override suspend fun setCloudSyncDeletionTombstonesJson(value: String?) {
        if (value.isNullOrBlank()) {
            localStorage.removeItem("cloud_sync_deletion_tombstones_json")
            _cloudSyncDeletionTombstonesJson.value = null
        } else {
            localStorage.setItem("cloud_sync_deletion_tombstones_json", value)
            _cloudSyncDeletionTombstonesJson.value = value
        }
    }
}

actual fun getSettingsRepository(): SettingsRepository = WasmJsSettingsRepository()
