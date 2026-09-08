package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.FileMetadata
import io.github.smiling_pixel.model.MomentEntryLink
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

    override fun getAllLinks(): Flow<List<MomentEntryLink>> =
        roomDao.getAllLinks().map { links -> links.map { MomentEntryLink(it.fileId, it.entrySyncId) } }

    override suspend fun getFileIdsForEntry(entrySyncId: String): Set<Long> =
        roomDao.getFileIdsForEntry(entrySyncId).toSet()

    override suspend fun getEntrySyncIdsForFile(fileId: Long): Set<String> =
        roomDao.getEntrySyncIdsForFile(fileId).toSet()

    override suspend fun replaceLinksForEntry(
        entrySyncId: String,
        fileIds: Set<Long>,
    ) = roomDao.replaceLinksForEntry(entrySyncId, fileIds)

    override suspend fun restoreLinks(links: List<MomentEntryLink>) {
        roomDao.insertLinks(links.map { io.github.smiling_pixel.model.RoomMomentEntryLink(it.fileId, it.entrySyncId) })
    }

    override suspend fun deleteLinksForEntry(entrySyncId: String) = roomDao.deleteLinksForEntry(entrySyncId)

    override suspend fun deleteDanglingLinks(validEntrySyncIds: Set<String>) {
        if (validEntrySyncIds.isEmpty()) {
            roomDao.deleteDanglingLinks(setOf("__no_valid_entries__"))
        } else {
            roomDao.deleteDanglingLinks(validEntrySyncIds)
        }
    }

    private fun RoomFileMetadata.toDomain(): FileMetadata =
        FileMetadata(id, originalFileName, filePath, tags, createdAt, mimeType, sizeBytes)

    private fun FileMetadata.toRoom(): RoomFileMetadata =
        RoomFileMetadata(id, originalFileName, filePath, tags, createdAt, mimeType, sizeBytes)
}
