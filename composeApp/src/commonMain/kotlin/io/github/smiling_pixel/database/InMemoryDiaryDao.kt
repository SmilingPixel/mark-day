package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.DiaryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Simple in-memory DAO implementation for commonMain/testing.
 * Uses a MutableStateFlow to provide reactive updates.
 */
class InMemoryDiaryDao(initial: List<DiaryEntry> = emptyList()) : IDiaryDao {
    private val state = MutableStateFlow(initial.toList())

    override val entriesFlow: Flow<List<DiaryEntry>> = state

    override suspend fun getAll(): List<DiaryEntry> = state.value

    override suspend fun insert(entry: DiaryEntry): Int {
        // Generate ID if 0
        val newId = if (entry.id == 0) (state.value.maxOfOrNull { it.id } ?: 0) + 1 else entry.id
        // Preserve caller-provided timestamps so sync-imported entries keep remote updatedAt.
        val e = entry.copy(id = newId)
        state.value = state.value + e
        return newId
    }

    override suspend fun update(entry: DiaryEntry) {
        // Do not rewrite updatedAt here: sync conflict resolution compares timestamps,
        // and mutating them in the DAO can cause unnecessary upload/download churn.
        state.value = state.value.map { if (it.syncId == entry.syncId) entry else it }
    }

    override suspend fun delete(entry: DiaryEntry) {
        state.value = state.value.filterNot { it.syncId == entry.syncId }
    }
}
