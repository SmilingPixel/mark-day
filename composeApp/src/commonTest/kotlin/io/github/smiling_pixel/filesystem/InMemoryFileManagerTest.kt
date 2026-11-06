package io.github.smiling_pixel.filesystem

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryFileManagerTest {
    @Test
    fun testSaveAndRead() =
        runTest {
            val fileManager = InMemoryFileManager()
            val content = "Hello, World!".encodeToByteArray()

            fileManager.save("test.txt", content)

            val readContent = fileManager.read("test.txt")
            assertEquals(content.decodeToString(), readContent?.decodeToString())
        }

    @Test
    fun testExists() =
        runTest {
            val fileManager = InMemoryFileManager()

            assertFalse(fileManager.exists("new_file.txt"))

            fileManager.save("new_file.txt", byteArrayOf())
            assertTrue(fileManager.exists("new_file.txt"))
        }

    @Test
    fun testDelete() =
        runTest {
            val fileManager = InMemoryFileManager()
            fileManager.save("to_delete.txt", byteArrayOf())

            assertTrue(fileManager.exists("to_delete.txt"))

            fileManager.delete("to_delete.txt")
            assertFalse(fileManager.exists("to_delete.txt"))
        }

    @Test
    fun testList() =
        runTest {
            val fileManager = InMemoryFileManager()
            fileManager.save("file1.txt", byteArrayOf())
            fileManager.save("file2.txt", byteArrayOf())

            val files = fileManager.list()
            assertEquals(2, files.size)
            assertTrue(files.contains("file1.txt"))
            assertTrue(files.contains("file2.txt"))
        }
}
