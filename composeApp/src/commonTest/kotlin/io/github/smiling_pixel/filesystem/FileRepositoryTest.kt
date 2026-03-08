package io.github.smiling_pixel.filesystem

import io.github.smiling_pixel.database.InMemoryFileMetadataDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FileRepositoryTest {

    @Test
    fun testSaveAndGetFileContent() = runTest {
        val fileManager = InMemoryFileManager()
        val metadataDao = InMemoryFileMetadataDao()
        val repository = FileRepository(fileManager, metadataDao)

        val content = "File content".encodeToByteArray()
        repository.saveFile("test_file.txt", content, listOf("tag1", "tag2"))

        val files = repository.files.first()
        assertEquals(1, files.size)
        
        val fileMeta = files.first()
        assertEquals("test_file.txt", fileMeta.originalFileName)
        assertEquals(listOf("tag1", "tag2"), fileMeta.tags)

        val readContent = repository.getFileContent(fileMeta)
        assertNotNull(readContent)
        assertEquals("File content", readContent.decodeToString())
    }

    @Test
    fun testUpdateExistingFileMetadata() = runTest {
        val fileManager = InMemoryFileManager()
        val metadataDao = InMemoryFileMetadataDao()
        val repository = FileRepository(fileManager, metadataDao)

        repository.saveFile("test.txt", byteArrayOf(), listOf("tag1"))
        
        // Save again with new tags
        repository.saveFile("test.txt", byteArrayOf(), listOf("updated_tag"))
        
        val files = repository.files.first()
        assertEquals(1, files.size)
        assertEquals(listOf("updated_tag"), files.first().tags)
    }

    @Test
    fun testDeleteFile() = runTest {
        val fileManager = InMemoryFileManager()
        val metadataDao = InMemoryFileMetadataDao()
        val repository = FileRepository(fileManager, metadataDao)

        repository.saveFile("test_delete.txt", byteArrayOf())
        val files = repository.files.first()
        assertEquals(1, files.size)

        repository.deleteFile(files.first())
        
        val updatedFiles = repository.files.first()
        assertEquals(0, updatedFiles.size)
        // Also ensure raw file is deleted
        val content = fileManager.read("test_delete.txt")
        assertNull(content)
    }
}