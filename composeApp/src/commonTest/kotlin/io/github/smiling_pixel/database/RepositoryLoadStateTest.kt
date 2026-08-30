package io.github.smiling_pixel.database

import io.github.smiling_pixel.filesystem.FileRepository
import io.github.smiling_pixel.filesystem.InMemoryFileManager
import io.github.smiling_pixel.model.LoadState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepositoryLoadStateTest {
    @Test
    fun diaryRepositoryReportsLoadedEmptyContent() = runTest {
        val repository = DiaryRepository(InMemoryDiaryDao(), scope = backgroundScope)
        val state = repository.entriesState.first { it is LoadState.Content }
        assertEquals(emptyList(), (state as LoadState.Content).value)
    }

    @Test
    fun fileRepositoryReportsLoadedEmptyContent() = runTest {
        val repository = FileRepository(InMemoryFileManager(), InMemoryFileMetadataDao(), scope = backgroundScope)
        val state = repository.filesState.first { it is LoadState.Content }
        assertTrue((state as LoadState.Content).value.isEmpty())
    }

    @Test
    fun diaryRepositoryReportsDaoFailure() = runTest {
        val repository = DiaryRepository(FailingDiaryDao(), scope = backgroundScope)
        val state = repository.entriesState.first { it is LoadState.Error }
        assertEquals("Your entries could not be loaded.", (state as LoadState.Error).message)
    }

    private class FailingDiaryDao : IDiaryDao {
        private val flow = MutableSharedFlow<List<io.github.smiling_pixel.model.DiaryEntry>>()
        override val entriesFlow = flow
        override suspend fun getAll(): List<io.github.smiling_pixel.model.DiaryEntry> = error("database unavailable")
        override suspend fun insert(entry: io.github.smiling_pixel.model.DiaryEntry): Int = error("unsupported")
        override suspend fun update(entry: io.github.smiling_pixel.model.DiaryEntry) = error("unsupported")
        override suspend fun delete(entry: io.github.smiling_pixel.model.DiaryEntry) = error("unsupported")
    }
}
