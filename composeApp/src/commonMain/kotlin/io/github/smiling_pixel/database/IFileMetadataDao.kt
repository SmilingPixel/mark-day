package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.FileMetadata
import kotlinx.coroutines.flow.Flow

interface IFileMetadataDao {
    fun getAllFiles(): Flow<List<FileMetadata>>
    suspend fun getFileById(id: Long): FileMetadata?
    suspend fun getFileByPath(path: String): FileMetadata?
    suspend fun insertFile(fileMetadata: FileMetadata): Long
    suspend fun updateFile(fileMetadata: FileMetadata)
    suspend fun deleteFile(fileMetadata: FileMetadata)
}
