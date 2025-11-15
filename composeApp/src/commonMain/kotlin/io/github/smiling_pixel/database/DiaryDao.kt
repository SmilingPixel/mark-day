package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.DiaryEntry
import kotlinx.coroutines.flow.Flow

// Common interface for DAO operations. Platform implementations (e.g., Room) should
// implement this interface in their source set. It exposes a reactive Flow of entries
// so UI can observe changes automatically.
interface IDiaryDao {
    val entriesFlow: Flow<List<DiaryEntry>>
    suspend fun getAll(): List<DiaryEntry>
    suspend fun insert(entry: DiaryEntry)
    suspend fun delete(entry: DiaryEntry)
}
