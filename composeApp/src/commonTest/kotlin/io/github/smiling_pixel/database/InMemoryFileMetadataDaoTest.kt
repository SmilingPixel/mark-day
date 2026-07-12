package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.FileMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryFileMetadataDaoTest {
    @Test
    fun testInsertAndGetFileList() =
        runTest {
            val dao = InMemoryFileMetadataDao()
            val metadata =
                FileMetadata(
                    originalFileName = "test.txt",
                    filePath = "/path/to/test.txt",
                    tags = listOf("test"),
                    createdAt = 123456789L,
                )

            val id = dao.insertFile(metadata)
            val files = dao.getAllFiles().first()

            assertEquals(1, files.size)
            assertEquals(id, files.first().id)
            assertEquals("test.txt", files.first().originalFileName)
        }

    @Test
    fun testGetFileByIdAndPath() =
        runTest {
            val dao = InMemoryFileMetadataDao()
            val metadata =
                FileMetadata(
                    originalFileName = "file.txt",
                    filePath = "/docs/file.txt",
                    tags = emptyList(),
                    createdAt = 0L,
                )
            val id = dao.insertFile(metadata)

            val byId = dao.getFileById(id)
            assertEquals(id, byId?.id)

            val byPath = dao.getFileByPath("/docs/file.txt")
            assertEquals(id, byPath?.id)

            val notFound = dao.getFileById(999L)
            assertNull(notFound)
        }

    @Test
    fun testDeleteFile() =
        runTest {
            val dao = InMemoryFileMetadataDao()
            val metadata =
                FileMetadata(
                    originalFileName = "todelete.txt",
                    filePath = "/del",
                    tags = emptyList(),
                    createdAt = 0L,
                )
            val id = dao.insertFile(metadata)

            val savedMetadata = dao.getFileById(id)!!
            dao.deleteFile(savedMetadata)

            val files = dao.getAllFiles().first()
            assertEquals(0, files.size)
        }
}
