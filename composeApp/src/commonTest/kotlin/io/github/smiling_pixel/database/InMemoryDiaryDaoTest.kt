package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.DiaryEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
    }

    @Test
    fun testUpdate() = runTest {
        val dao = InMemoryDiaryDao()
        val entry = DiaryEntry(id = 0, title = "Title", content = "Content")
        val id = dao.insert(entry)
        
        val updatedEntry = dao.getAll().first().copy(title = "Updated Title")
        dao.update(updatedEntry)
        
        val allEntries = dao.getAll()
        assertEquals(1, allEntries.size)
        assertEquals("Updated Title", allEntries.first().title)
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