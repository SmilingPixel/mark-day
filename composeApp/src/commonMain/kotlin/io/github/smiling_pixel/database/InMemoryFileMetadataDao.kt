package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.FileMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class InMemoryFileMetadataDao : IFileMetadataDao {
    private val files = MutableStateFlow<List<FileMetadata>>(emptyList())
    private var nextId = 1L

    override fun getAllFiles(): Flow<List<FileMetadata>> = files

    override suspend fun getFileById(id: Long): FileMetadata? {
        return files.value.find { it.id == id }
    }

    override suspend fun getFileByPath(path: String): FileMetadata? {
        return files.value.find { it.filePath == path }
    }

    override suspend fun insertFile(fileMetadata: FileMetadata): Long {
        val id = if (fileMetadata.id == 0L) nextId++ else fileMetadata.id
        val newFile = fileMetadata.copy(id = id)
        files.update { current ->
            val existingIndex = current.indexOfFirst { it.id == id }
            if (existingIndex >= 0) {
                current.toMutableList().apply { set(existingIndex, newFile) }
            } else {
                current + newFile
            }
        }
        return id
    }

    override suspend fun updateFile(fileMetadata: FileMetadata) {
        insertFile(fileMetadata)
    }

    override suspend fun deleteFile(fileMetadata: FileMetadata) {
        files.update { current ->
            current.filter { it.id != fileMetadata.id }
        }
    }
}
