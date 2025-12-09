package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.FileMetadata
import io.github.smiling_pixel.model.RoomFileMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FileMetadataDaoImpl(private val roomDao: FileMetadataRoomDao) : IFileMetadataDao {
    override fun getAllFiles(): Flow<List<FileMetadata>> {
        return roomDao.getAllFiles().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getFileById(id: Long): FileMetadata? {
        return roomDao.getFileById(id)?.toDomain()
    }

    override suspend fun getFileByPath(path: String): FileMetadata? {
        return roomDao.getFileByPath(path)?.toDomain()
    }

    override suspend fun insertFile(fileMetadata: FileMetadata): Long {
        return roomDao.insertFile(fileMetadata.toRoom())
    }

    override suspend fun updateFile(fileMetadata: FileMetadata) {
        roomDao.updateFile(fileMetadata.toRoom())
    }

    override suspend fun deleteFile(fileMetadata: FileMetadata) {
        roomDao.deleteFile(fileMetadata.toRoom())
    }

    private fun RoomFileMetadata.toDomain(): FileMetadata {
        return FileMetadata(id, originalFileName, filePath, tags, createdAt)
    }

    private fun FileMetadata.toRoom(): RoomFileMetadata {
        return RoomFileMetadata(id, originalFileName, filePath, tags, createdAt)
    }
}
