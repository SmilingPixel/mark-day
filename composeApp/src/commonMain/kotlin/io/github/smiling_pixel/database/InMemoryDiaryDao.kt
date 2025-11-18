package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.DiaryEntry
import kotlinx.datetime.Clock
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

    override suspend fun insert(entry: DiaryEntry) {
        // update the `updatedAt` timestamp on insert to mark the latest change
        val now = Clock.System.now()
        val e = entry.copy(updatedAt = now)
        state.value = state.value + e
    }

    override suspend fun delete(entry: DiaryEntry) {
        state.value = state.value.filterNot { it.id == entry.id }
    }
}
