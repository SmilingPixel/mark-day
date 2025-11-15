package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.DiaryEntry
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
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
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

    suspend fun insert(entry: DiaryEntry) {
        dao.insert(entry)
        // If DAO is flow-backed, the collector will update _entries automatically.
        // For non-flow DAOs the repository initial load above ensures consistency.
    }

    suspend fun delete(entry: DiaryEntry) {
        dao.delete(entry)
    }
}
