package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.DiaryEntry

// Common interface for DAO operations. Platform implementations (e.g., Room) should
// implement this interface in their source set.
interface IDiaryDao {
    suspend fun getAll(): List<DiaryEntry>
    suspend fun insert(entry: DiaryEntry)
    suspend fun delete(entry: DiaryEntry)
}
