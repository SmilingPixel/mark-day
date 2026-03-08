package io.github.smiling_pixel.filesystem

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.Uri
import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
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
    fun testFetchSuccess() = runTest {
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
    fun testFetchFailure() = runTest {
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
        
        // This is a dummy implementation of PlatformContext and ImageLoader because they are not used in the Factory.create()
        val options = Options(coil3.PlatformContext.INSTANCE)
        val imageLoader = ImageLoader(coil3.PlatformContext.INSTANCE)

        val fetcher = factory.create(Uri("localfile:///image.jpg"), options, imageLoader)
        assertNotNull(fetcher)
        
        // Factory should trim the leading slash
        val fetcher2 = factory.create(Uri("localfile:image.jpg"), options, imageLoader)
        assertNotNull(fetcher2)
    }

    @Test
    fun testFactoryCreateWithInvalidScheme() {
        val fileManager = InMemoryFileManager()
        val factory = LocalFileFetcher.Factory(fileManager)
        
        val options = Options(coil3.PlatformContext.INSTANCE)
        val imageLoader = ImageLoader(coil3.PlatformContext.INSTANCE)

        val fetcher = factory.create(Uri("https://example.com/image.jpg"), options, imageLoader)
        assertNull(fetcher)
    }

    @Test
    fun testFactoryCreateWithUnsafePath() {
        val fileManager = InMemoryFileManager()
        val factory = LocalFileFetcher.Factory(fileManager)
        
        val options = Options(coil3.PlatformContext.INSTANCE)
        val imageLoader = ImageLoader(coil3.PlatformContext.INSTANCE)

        // Traversal path
        val fetcher = factory.create(Uri("localfile://../image.jpg"), options, imageLoader)
        assertNull(fetcher)
        
        // Empty path
        val fetcher2 = factory.create(Uri("localfile:"), options, imageLoader)
        assertNull(fetcher2)
    }
}