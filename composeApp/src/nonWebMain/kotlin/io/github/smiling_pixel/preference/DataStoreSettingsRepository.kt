package io.github.smiling_pixel.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

internal const val DATA_STORE_FILE_NAME = "prefs.preferences_pb"

expect fun producePath(): String

fun createDataStore(producePath: () -> String): DataStore<Preferences> {
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = { producePath().toPath() }
    )
}

private val dataStore: DataStore<Preferences> by lazy {
    createDataStore { producePath() }
}

class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {
    private val WEATHER_API_KEY = stringPreferencesKey("weather_api_key")

    override val googleWeatherApiKey: Flow<String?> = dataStore.data
        .map { preferences ->
            preferences[WEATHER_API_KEY]
        }

    override suspend fun setGoogleWeatherApiKey(key: String?) {
        dataStore.edit { preferences ->
            if (key != null) {
                preferences[WEATHER_API_KEY] = key
            } else {
                preferences.remove(WEATHER_API_KEY)
            }
        }
    }
}

actual fun getSettingsRepository(): SettingsRepository = DataStoreSettingsRepository(dataStore)
