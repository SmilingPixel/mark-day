package io.github.smiling_pixel.filesystem

import io.github.smiling_pixel.database.IFileMetadataDao
import io.github.smiling_pixel.model.FileMetadata
import io.github.smiling_pixel.model.LoadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.time.Clock

class FileRepository(
    private val fileManager: FileManager,
    private val metadataDao: IFileMetadataDao,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    val files: Flow<List<FileMetadata>> = metadataDao.getAllFiles()

    private val _filesState = MutableStateFlow<LoadState<List<FileMetadata>>>(LoadState.Loading)

    /** Emits loading, content, or error state for file metadata. */
    val filesState: StateFlow<LoadState<List<FileMetadata>>> = _filesState

    init {
        scope.launch {
            try {
                metadataDao.getAllFiles().collect { value ->
                    _filesState.value = LoadState.Content(value)
                }
            } catch (e: Exception) {
                _filesState.value =
                    LoadState.Error(
                        message = "Moments could not be loaded.",
                        technicalDetails = e.message,
                    )
            }
        }
    }

    suspend fun saveFile(
        fileName: String,
        content: ByteArray,
        tags: List<String> = emptyList(),
    ) {
        // 1. Save raw file
        fileManager.save(fileName, content)

        // 2. Save metadata
        // Check if metadata exists
        // File path stores the relative path or filename used in FileManager.
        val existing = metadataDao.getFileByPath(fileName)
        if (existing != null) {
            // Update tags if provided, otherwise keep existing?
            // For now, let's update tags if the list is not empty, or just update the file.
            // The requirement says "manage them better", so keeping it simple.
            metadataDao.updateFile(existing.copy(tags = tags))
        } else {
            metadataDao.insertFile(
                FileMetadata(
                    originalFileName = fileName,
                    filePath = fileName,
                    tags = tags,
                    createdAt = Clock.System.now().toEpochMilliseconds(),
                ),
            )
        }
    }

    suspend fun deleteFile(fileMetadata: FileMetadata) {
        // 1. Delete raw file
        fileManager.delete(fileMetadata.filePath)

        // 2. Delete metadata
        metadataDao.deleteFile(fileMetadata)
    }

    suspend fun getFileContent(fileMetadata: FileMetadata): ByteArray? = fileManager.read(fileMetadata.filePath)
}
