package io.github.smiling_pixel.filesystem

import io.github.smiling_pixel.database.IFileMetadataDao
import io.github.smiling_pixel.model.FileMetadata
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

class FileRepository(
    private val fileManager: FileManager,
    private val metadataDao: IFileMetadataDao
) {
    val files: Flow<List<FileMetadata>> = metadataDao.getAllFiles()

    suspend fun saveFile(fileName: String, content: ByteArray, tags: List<String> = emptyList()) {
        // 1. Save raw file
        fileManager.save(fileName, content)

        // 2. Save metadata
        // Check if metadata exists
        val existing = metadataDao.getFileByPath(fileName) // Assuming filePath stores the relative path/filename used in FileManager
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
                    createdAt = Clock.System.now().toEpochMilliseconds()
                )
            )
        }
    }

    suspend fun deleteFile(fileMetadata: FileMetadata) {
        // 1. Delete raw file
        fileManager.delete(fileMetadata.filePath)

        // 2. Delete metadata
        metadataDao.deleteFile(fileMetadata)
    }
    
    suspend fun getFileContent(fileMetadata: FileMetadata): ByteArray? {
        return fileManager.read(fileMetadata.filePath)
    }
}
