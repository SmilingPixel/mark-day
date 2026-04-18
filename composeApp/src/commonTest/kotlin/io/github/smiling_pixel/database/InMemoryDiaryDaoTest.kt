package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.DiaryEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryDiaryDaoTest {

    @Test
    fun testInsertAndGetAll() = runTest {
        val dao = InMemoryDiaryDao()
        val entry = DiaryEntry(id = 0, title = "Test Title", content = "Test Content")
        
        val id = dao.insert(entry)
        val allEntries = dao.getAll()
        
        assertEquals(1, allEntries.size)
        assertEquals(id, allEntries.first().id)
        assertEquals("Test Title", allEntries.first().title)
        assertTrue(allEntries.first().syncId.isNotBlank())
    }

    @Test
    fun testUpdate() = runTest {
        val dao = InMemoryDiaryDao()
        val entry = DiaryEntry(id = 0, title = "Title", content = "Content")
        dao.insert(entry)
        
        val original = dao.getAll().first()
        val updatedEntry = original.copy(title = "Updated Title")
        dao.update(updatedEntry)
        
        val allEntries = dao.getAll()
        assertEquals(1, allEntries.size)
        assertEquals("Updated Title", allEntries.first().title)
        assertEquals(original.syncId, allEntries.first().syncId)
    }

    @Test
    fun testInsertPreservesUpdatedAt() = runTest {
        val dao = InMemoryDiaryDao()
        val expectedUpdatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000)
        val entry = DiaryEntry(
            id = 0,
            title = "Remote",
            content = "From sync",
            updatedAt = expectedUpdatedAt,
        )

        dao.insert(entry)

        val inserted = dao.getAll().first()
        assertEquals(expectedUpdatedAt, inserted.updatedAt)
    }

    @Test
    fun testUpdatePreservesUpdatedAt() = runTest {
        val dao = InMemoryDiaryDao()
        val initial = DiaryEntry(id = 0, title = "Title", content = "Content")
        dao.insert(initial)
        val existing = dao.getAll().first()

        val expectedUpdatedAt = Instant.fromEpochMilliseconds(1_800_000_000_000)
        val updated = existing.copy(title = "Edited", updatedAt = expectedUpdatedAt)

        dao.update(updated)

        val saved = dao.getAll().first()
        assertEquals(expectedUpdatedAt, saved.updatedAt)
    }

    @Test
    fun testDelete() = runTest {
        val dao = InMemoryDiaryDao()
        val entry = DiaryEntry(id = 0, title = "Title", content = "Content")
        dao.insert(entry)
        
        val savedEntry = dao.getAll().first()
        dao.delete(savedEntry)
        
        val allEntries = dao.getAll()
        assertTrue(allEntries.isEmpty())
    }

    @Test
    fun testFlowEmitsUpdates() = runTest {
        val dao = InMemoryDiaryDao()
        val entry = DiaryEntry(id = 0, title = "Title", content = "Content")
        dao.insert(entry)
        
        val flowEntries = dao.entriesFlow.first()
        assertEquals(1, flowEntries.size)
    }
}