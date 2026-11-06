package io.github.smiling_pixel.draft

import kotlinx.browser.localStorage

private class LocalStorageEntryDraftStorage : EntryDraftStorage {
    override suspend fun read(): String? = localStorage.getItem(STORAGE_KEY)

    override suspend fun write(value: String?) {
        if (value == null) {
            localStorage.removeItem(STORAGE_KEY)
        } else {
            localStorage.setItem(STORAGE_KEY, value)
        }
    }
}

private val repository by lazy {
    // Keep one repository instance so concurrent editor writes share its read-modify-write mutex.
    PersistentEntryDraftRepository(LocalStorageEntryDraftStorage())
}

actual fun getEntryDraftRepository(): EntryDraftRepository = repository

private const val STORAGE_KEY = "markday_entry_drafts_v1"
