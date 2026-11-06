package io.github.smiling_pixel.filesystem

import io.github.smiling_pixel.database.InMemoryFileMetadataDao
import io.github.smiling_pixel.model.FileMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileRepositoryTest {
    @Test
    fun savePersistsContentAndMetadataUnderOwnedName() =
        runTest {
            val fileManager = InMemoryFileManager()
            val repository = FileRepository(fileManager, InMemoryFileMetadataDao(), backgroundScope)
            val content = "File content".encodeToByteArray()

            val saved = repository.saveFile("test_file.txt", content, listOf("tag1", "tag2"))

            assertEquals("test_file.txt", saved.originalFileName)
            assertEquals("text/plain", saved.mimeType)
            assertEquals(content.size.toLong(), saved.sizeBytes)
            assertEquals(listOf("tag1", "tag2"), saved.tags)
            assertTrue(saved.filePath.startsWith(MOMENT_STORAGE_PREFIX))
            assertContentEquals(content, repository.getFileContent(saved))
        }

    @Test
    fun duplicateSourceNamesCreateDistinctMoments() =
        runTest {
            val repository = FileRepository(InMemoryFileManager(), InMemoryFileMetadataDao(), backgroundScope)

            val first = repository.saveFile("same-name.txt", "first".encodeToByteArray())
            val second = repository.saveFile("same-name.txt", "second".encodeToByteArray())

            assertNotEquals(first.id, second.id)
            assertNotEquals(first.filePath, second.filePath)
            assertEquals(2, repository.files.first().size)
            assertEquals("first", repository.getFileContent(first)?.decodeToString())
            assertEquals("second", repository.getFileContent(second)?.decodeToString())
        }

    @Test
    fun replaceAndRestoreEntryLinks() =
        runTest {
            val repository = FileRepository(InMemoryFileManager(), InMemoryFileMetadataDao(), backgroundScope)
            val first = repository.saveFile("first.pdf", byteArrayOf(1))
            val second = repository.saveFile("second.pdf", byteArrayOf(2))

            repository.replaceLinksForEntry("entry-one", setOf(first.id, second.id))
            val snapshot = repository.snapshotLinksForEntries(setOf("entry-one"))
            repository.replaceLinksForEntry("entry-one", setOf(second.id))

            assertEquals(setOf(second.id), repository.getFileIdsForEntry("entry-one"))
            repository.restoreLinks(snapshot)
            assertEquals(setOf(first.id, second.id), repository.getFileIdsForEntry("entry-one"))
            assertEquals(setOf("entry-one"), repository.getEntrySyncIdsForFile(first.id))
        }

    @Test
    fun stagedDeleteCanBeUndoneOrFinalized() =
        runTest {
            val fileManager = InMemoryFileManager()
            val repository = FileRepository(fileManager, InMemoryFileMetadataDao(), backgroundScope)
            val saved = repository.saveFile("delete.txt", byteArrayOf(1, 2, 3))
            repository.replaceLinksForEntry("entry-one", setOf(saved.id))

            val firstAttempt = repository.stageDelete(setOf(saved.id))
            assertEquals(setOf(saved.id), repository.pendingDeletionIds.value)
            assertTrue(repository.undoDelete(firstAttempt.token))
            assertTrue(fileManager.exists(saved.filePath))
            assertEquals(setOf(saved.id), repository.getFileIdsForEntry("entry-one"))

            val secondAttempt = repository.stageDelete(setOf(saved.id))
            assertTrue(repository.finalizeDelete(secondAttempt.token))
            assertFalse(fileManager.exists(saved.filePath))
            assertTrue(repository.files.first().isEmpty())
            assertTrue(repository.getFileIdsForEntry("entry-one").isEmpty())
        }

    @Test
    fun reconcileRelocatesLegacyFilesAndBackfillsMetadata() =
        runTest {
            val fileManager = InMemoryFileManager()
            val metadataDao = InMemoryFileMetadataDao()
            fileManager.save("legacy photo.jpg", byteArrayOf(1, 2, 3, 4))
            metadataDao.insertFile(
                FileMetadata(
                    originalFileName = "legacy photo.jpg",
                    filePath = "legacy photo.jpg",
                    tags = emptyList(),
                    createdAt = 1,
                ),
            )
            val repository = FileRepository(fileManager, metadataDao, backgroundScope)

            repository.reconcileStorage()

            val relocated = assertNotNull(repository.files.first().singleOrNull())
            assertTrue(relocated.filePath.startsWith(MOMENT_STORAGE_PREFIX))
            assertFalse(fileManager.exists("legacy photo.jpg"))
            assertTrue(fileManager.exists(relocated.filePath))
            assertEquals(4, relocated.sizeBytes)
            assertEquals("image/jpeg", relocated.mimeType)
        }

    @Test
    fun reconcileDeletesOnlyUnreferencedOwnedFiles() =
        runTest {
            val fileManager = InMemoryFileManager()
            val repository = FileRepository(fileManager, InMemoryFileMetadataDao(), backgroundScope)
            val standalone = repository.saveFile("standalone.txt", byteArrayOf(1))
            fileManager.save("moment_abandoned.txt", byteArrayOf(2))
            fileManager.save("unrelated-app-file.txt", byteArrayOf(3))

            repository.reconcileStorage()

            assertTrue(fileManager.exists(standalone.filePath))
            assertFalse(fileManager.exists("moment_abandoned.txt"))
            assertTrue(fileManager.exists("unrelated-app-file.txt"))
        }
}
