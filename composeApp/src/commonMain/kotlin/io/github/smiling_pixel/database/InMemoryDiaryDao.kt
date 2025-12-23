package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.DiaryEntry
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Simple in-memory DAO implementation for commonMain/testing.
 * Uses a MutableStateFlow to provide reactive updates.
 */
@OptIn(kotlin.time.ExperimentalTime::class)
class InMemoryDiaryDao(initial: List<DiaryEntry> = emptyList()) : IDiaryDao {
    private val state = MutableStateFlow(initial.toList())

    override val entriesFlow: Flow<List<DiaryEntry>> = state

    override suspend fun getAll(): List<DiaryEntry> = state.value

    override suspend fun insert(entry: DiaryEntry): Int {
        // update the `updatedAt` timestamp on insert to mark the latest change
        val now = Clock.System.now()
        // Generate ID if 0
        val newId = if (entry.id == 0) (state.value.maxOfOrNull { it.id } ?: 0) + 1 else entry.id
        val e = entry.copy(id = newId, updatedAt = now)
        state.value = state.value + e
        return newId
    }

    override suspend fun update(entry: DiaryEntry) {
        val now = Clock.System.now()
        val updated = entry.copy(updatedAt = now)
        state.value = state.value.map { if (it.id == entry.id) updated else it }
    }

    override suspend fun delete(entry: DiaryEntry) {
        state.value = state.value.filterNot { it.id == entry.id }
    }
}
