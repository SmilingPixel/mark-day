package io.github.smiling_pixel.filesystem

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.FetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.IOException

import io.github.smiling_pixel.util.Logger
import io.github.smiling_pixel.util.e
import io.github.smiling_pixel.util.w

class LocalFileFetcher(
    private val fileName: String,
    private val fileManager: FileManager
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bytes = fileManager.read(fileName) ?: run {
            val errorMessage = "LocalFileFetcher: File not found: $fileName"
            runCatching { Logger.e("LocalFileFetcher", errorMessage) }
            // Returning null here would let Coil try other fetchers, but since we handle 
            // the 'localfile' scheme, no other fetcher is expected to succeed.
            // Throwing an exception provides a more informative error result.
            throw IOException(errorMessage)
        }
        val buffer = Buffer().write(bytes)
        
        return SourceFetchResult(
            source = ImageSource(buffer, EmptyFileSystem),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    /**
     * Factory for creating [LocalFileFetcher] instances for URIs with the `localfile` scheme.
     *
     * Expected formats include:
     * - `localfile:image.jpg`
     * - `localfile:/image.jpg`
     * - `localfile:///image.jpg`
     *
     * In all cases, the path part is normalized by trimming leading '/' characters before
     * being passed to [FileManager]. Only the `localfile` scheme is recognized here.
     */
    class Factory(private val fileManager: FileManager) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            return createForUri(data)
        }

        internal fun createForUri(data: Uri): Fetcher? {
            // Coil's multiplatform Uri parser does not expose custom `localfile` URIs consistently
            // across targets. On JVM, for example, `Uri("localfile:///image.jpg")` can report the
            // whole string through `scheme`, while `Uri("localfile:image.jpg")` may have no usable
            // `path`. The raw string remains the stable source of truth for the URI forms this
            // factory documents, so we use it as the primary guard and normalize it below.
            val rawUri = data.toString()
            if (rawUri == "localfile" || rawUri == "localfile:" || rawUri == "localfile://") {
                Logger.w("LocalFileFetcher", "Empty path in URI: $data")
                return null
            }
            if (data.scheme == "localfile" || rawUri.startsWith("localfile:")) {
                val fileName = localFileName(rawUri, data.path) ?: run {
                    Logger.w("LocalFileFetcher", "Empty path in URI: $data")
                    return null
                }

                // Reject potentially unsafe paths to prevent directory traversal.
                // This ensures inputs like "../secret.png" or "a/../../etc/passwd" are not used.
                if (fileName.isEmpty() || fileName.contains("..")) {
                    Logger.w("LocalFileFetcher", "Rejected potentially unsafe or empty path: $fileName")
                    return null
                }
                return LocalFileFetcher(fileName, fileManager)
            }
            return null
        }

        private fun localFileName(rawUri: String, uriPath: String?): String? {
            // Prefer the raw URI for opaque values like `localfile:image.jpg`; fall back to
            // `Uri.path` for targets that parse hierarchical values into a normal path. Some JVM
            // string forms include a trailing ':' for custom schemes, so the candidate helper trims
            // that before the empty/traversal checks in `createForUri`.
            if (rawUri.startsWith("localfile:")) {
                val rawPath = rawUri.removePrefix("localfile:")
                return rawPath.toLocalFileNameCandidate()
            }

            return uriPath?.toLocalFileNameCandidate()
        }

        private fun String.toLocalFileNameCandidate(): String? {
            return trimStart('/')
                .removeSuffix(":")
                .takeIf { it.isNotEmpty() && it != "localfile" && !it.startsWith("localfile:") }
        }
    }
}

/**
 * A dummy [FileSystem] implementation used for [ImageSource] when the data is already buffered in memory.
 * 
 * Since we load the file content into an Okio [Buffer] using [FileManager] and pass that buffer to [ImageSource],
 * Coil does not need to read from the file system directly for this source. 
 * This implementation safely returns null or throws strict exceptions to ensure no unintended file system usage occurs.
 */
object EmptyFileSystem : FileSystem() {
    override fun canonicalize(path: Path) = path
    override fun metadataOrNull(path: Path) = null
    override fun list(dir: Path) = throw IOException("Not supported")
    override fun listOrNull(dir: Path): List<Path>? = null
    override fun openReadOnly(file: Path) = throw IOException("Not supported")
    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean) = throw IOException("Not supported")
    override fun source(file: Path) = throw IOException("Not supported")
    override fun sink(file: Path, mustCreate: Boolean) = throw IOException("Not supported")
    override fun appendingSink(file: Path, mustExist: Boolean) = throw IOException("Not supported")
    override fun createDirectory(dir: Path, mustCreate: Boolean) = throw IOException("Not supported")
    override fun atomicMove(source: Path, target: Path) = throw IOException("Not supported")
    override fun delete(path: Path, mustExist: Boolean) = throw IOException("Not supported")
    override fun createSymlink(source: Path, target: Path) = throw IOException("Not supported")
}
