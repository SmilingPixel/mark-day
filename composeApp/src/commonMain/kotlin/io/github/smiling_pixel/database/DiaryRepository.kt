package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.preference.SettingsRepository
import io.github.smiling_pixel.preference.getSettingsRepository
import io.github.smiling_pixel.sync.clearLocalDeletionTombstone
import io.github.smiling_pixel.sync.recordLocalDeletionTombstone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Repository that exposes a StateFlow of diary entries and provides suspend helpers
 * to perform CRUD operations via the provided IDiaryDao.
 */
class DiaryRepository(
    private val dao: IDiaryDao,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val settings: SettingsRepository = getSettingsRepository(),
) {
    private val _entries = MutableStateFlow<List<DiaryEntry>>(emptyList())
    val entries: StateFlow<List<DiaryEntry>> = _entries

    init {
        // Collect the DAO's flow and update our StateFlow so Compose can collect it as state
        scope.launch {
            dao.entriesFlow.collect { list ->
                _entries.value = list
            }
        }
        // initial load in case DAO isn't Flow-backed
        scope.launch {
            val list = dao.getAll()
            if (list.isNotEmpty()) _entries.value = list
        }
    }

    /**
     * Returns the current diary entries directly from persistent storage.
     *
     * Use this for operations that require an authoritative snapshot. [entries] is updated
     * asynchronously and may briefly contain stale data while the repository is initializing or
     * after a write.
     *
     * @return The diary entries currently stored by the DAO.
     */
    suspend fun getAll(): List<DiaryEntry> = dao.getAll()

    suspend fun insert(entry: DiaryEntry): Int {
        val id = dao.insert(entry)
        // If DAO is flow-backed, the collector will update _entries automatically.
        // For non-flow DAOs the repository initial load above ensures consistency.
        return id
    }

    suspend fun update(entry: DiaryEntry) {
        dao.update(entry)
    }

    suspend fun delete(
        entry: DiaryEntry,
        recordSyncTombstone: Boolean = true,
    ) {
        if (recordSyncTombstone) {
            recordLocalDeletionTombstone(entry.syncId, settings = settings)
        }
        dao.delete(entry)
    }

    /**
     * Restores a previously deleted diary entry by its stable sync identifier.
     *
     * If the entry already exists, its current local database ID is preserved. Otherwise, the DAO assigns a new local
     * ID. The local sync tombstone is removed only after the entry has been restored successfully.
     *
     * @param entry The entry snapshot to restore.
     */
    suspend fun restore(entry: DiaryEntry) {
        val existing = dao.getAll().firstOrNull { it.syncId == entry.syncId }
        if (existing == null) {
            dao.insert(entry.copy(id = 0))
        } else {
            dao.update(entry.copy(id = existing.id))
        }
        clearLocalDeletionTombstone(entry.syncId, settings = settings)
    }
}
