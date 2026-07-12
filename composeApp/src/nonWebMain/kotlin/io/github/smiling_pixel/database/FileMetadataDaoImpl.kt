package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.FileMetadata
import io.github.smiling_pixel.model.RoomFileMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FileMetadataDaoImpl(
    private val roomDao: FileMetadataRoomDao,
) : IFileMetadataDao {
    override fun getAllFiles(): Flow<List<FileMetadata>> =
        roomDao.getAllFiles().map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun getFileById(id: Long): FileMetadata? = roomDao.getFileById(id)?.toDomain()

    override suspend fun getFileByPath(path: String): FileMetadata? = roomDao.getFileByPath(path)?.toDomain()

    override suspend fun insertFile(fileMetadata: FileMetadata): Long = roomDao.insertFile(fileMetadata.toRoom())

    override suspend fun updateFile(fileMetadata: FileMetadata) {
        roomDao.updateFile(fileMetadata.toRoom())
    }

    override suspend fun deleteFile(fileMetadata: FileMetadata) {
        roomDao.deleteFile(fileMetadata.toRoom())
    }

    private fun RoomFileMetadata.toDomain(): FileMetadata =
        FileMetadata(id, originalFileName, filePath, tags, createdAt)

    private fun FileMetadata.toRoom(): RoomFileMetadata =
        RoomFileMetadata(id, originalFileName, filePath, tags, createdAt)
}
