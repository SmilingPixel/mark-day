package io.github.smiling_pixel.draft

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath

internal const val DRAFT_DATA_STORE_FILE_NAME = "entry_drafts.preferences_pb"

internal expect fun produceDraftPath(): String

private val draftDataStore: DataStore<Preferences> by lazy {
    // Drafts use a separate file so their schema and write frequency remain independent from user preferences.
    PreferenceDataStoreFactory.createWithPath(produceFile = { produceDraftPath().toPath() })
}

private class DataStoreEntryDraftStorage(
    private val dataStore: DataStore<Preferences>,
) : EntryDraftStorage {
    override suspend fun read(): String? = dataStore.data.first()[ENTRY_DRAFTS]

    override suspend fun write(value: String?) {
        dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(ENTRY_DRAFTS)
            } else {
                preferences[ENTRY_DRAFTS] = value
            }
        }
    }

    companion object {
        private val ENTRY_DRAFTS = stringPreferencesKey("entry_drafts_json")
    }
}

private val repository by lazy {
    // A single repository instance is required because it owns read-modify-write serialization for the JSON envelope.
    PersistentEntryDraftRepository(DataStoreEntryDraftStorage(draftDataStore))
}

actual fun getEntryDraftRepository(): EntryDraftRepository = repository
