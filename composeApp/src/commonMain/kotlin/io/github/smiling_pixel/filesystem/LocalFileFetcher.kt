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

class LocalFileFetcher(
    private val fileName: String,
    private val fileManager: FileManager
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bytes = fileManager.read(fileName) ?: return null
        val buffer = Buffer().write(bytes)
        
        return SourceFetchResult(
            source = ImageSource(buffer, EmptyFileSystem),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val fileManager: FileManager) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme == "localfile") {
                // data.path might start with /, e.g. /image.jpg
                val fileName = data.path?.trimStart('/') ?: return null
                return LocalFileFetcher(fileName, fileManager)
            }
            return null
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
