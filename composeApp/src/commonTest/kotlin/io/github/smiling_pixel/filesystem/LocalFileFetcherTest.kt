package io.github.smiling_pixel.filesystem

import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import coil3.toUri
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalFileFetcherTest {
    @Test
    fun testFetchSuccess() =
        runTest {
            val fileManager = InMemoryFileManager()
            val content = "Image Data".encodeToByteArray()
            fileManager.save("image.jpg", content)

            val fetcher = LocalFileFetcher("image.jpg", fileManager)
            val result = fetcher.fetch()

            assertTrue(result is SourceFetchResult)
            assertEquals(DataSource.DISK, result.dataSource)
            // Consume the source to verify its content
            val buffer = okio.Buffer()
            result.source.source().readAll(buffer)
            assertEquals("Image Data", buffer.readUtf8())
        }

    @Test
    fun testFetchFailure() =
        runTest {
            val fileManager = InMemoryFileManager()

            val fetcher = LocalFileFetcher("image.jpg", fileManager)

            assertFailsWith<IOException> {
                fetcher.fetch()
            }
        }

    @Test
    fun testFactoryCreateWithValidUri() {
        val fileManager = InMemoryFileManager()
        val factory = LocalFileFetcher.Factory(fileManager)

        val fetcher = factory.createForUri("localfile:///image.jpg".toUri())
        assertNotNull(fetcher)
    }

    @Test
    fun testFactoryCreateWithInvalidScheme() {
        val fileManager = InMemoryFileManager()
        val factory = LocalFileFetcher.Factory(fileManager)

        val fetcher = factory.createForUri("https://example.com/image.jpg".toUri())
        assertNull(fetcher)
    }

    @Test
    fun testFactoryCreateWithUnsafePath() {
        val fileManager = InMemoryFileManager()
        val factory = LocalFileFetcher.Factory(fileManager)

        val fetcher = factory.createForUri("localfile:///../image.jpg".toUri())
        assertNull(fetcher)
    }

    @Test
    fun testFactoryRejectsNonCanonicalLocalFileUri() {
        val fileManager = InMemoryFileManager()
        val factory = LocalFileFetcher.Factory(fileManager)

        val opaqueUriFetcher = factory.createForUri("localfile:image.jpg".toUri())
        assertNull(opaqueUriFetcher)

        val authorityUriFetcher = factory.createForUri("localfile://image.jpg".toUri())
        assertNull(authorityUriFetcher)

        val emptyPathFetcher = factory.createForUri("localfile:".toUri())
        assertNull(emptyPathFetcher)

        val nestedPathFetcher = factory.createForUri("localfile:///images/image.jpg".toUri())
        assertNull(nestedPathFetcher)
    }
}
