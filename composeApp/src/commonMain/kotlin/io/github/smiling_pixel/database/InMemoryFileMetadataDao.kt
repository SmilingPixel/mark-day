package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.FileMetadata
import io.github.smiling_pixel.model.MomentEntryLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class InMemoryFileMetadataDao : IFileMetadataDao {
    private val files = MutableStateFlow<List<FileMetadata>>(emptyList())
    private val links = MutableStateFlow<List<MomentEntryLink>>(emptyList())
    private var nextId = 1L

    override fun getAllFiles(): Flow<List<FileMetadata>> = files

    override suspend fun getFileById(id: Long): FileMetadata? = files.value.find { it.id == id }

    override suspend fun getFileByPath(path: String): FileMetadata? = files.value.find { it.filePath == path }

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
        links.update { current -> current.filterNot { it.fileId == fileMetadata.id } }
    }

    override fun getAllLinks(): Flow<List<MomentEntryLink>> = links

    override suspend fun getFileIdsForEntry(entrySyncId: String): Set<Long> =
        links.value.filter { it.entrySyncId == entrySyncId }.mapTo(mutableSetOf()) { it.fileId }

    override suspend fun getEntrySyncIdsForFile(fileId: Long): Set<String> =
        links.value.filter { it.fileId == fileId }.mapTo(mutableSetOf()) { it.entrySyncId }

    override suspend fun replaceLinksForEntry(
        entrySyncId: String,
        fileIds: Set<Long>,
    ) {
        val validFileIds = files.value.mapTo(mutableSetOf()) { it.id }
        links.update { current ->
            current.filterNot { it.entrySyncId == entrySyncId } +
                fileIds.filter { it in validFileIds }.map { MomentEntryLink(it, entrySyncId) }
        }
    }

    override suspend fun restoreLinks(links: List<MomentEntryLink>) {
        val validFileIds = files.value.mapTo(mutableSetOf()) { it.id }
        this.links.update { current ->
            (current + links.filter { it.fileId in validFileIds }).distinct()
        }
    }

    override suspend fun deleteLinksForEntry(entrySyncId: String) {
        links.update { current -> current.filterNot { it.entrySyncId == entrySyncId } }
    }

    override suspend fun deleteDanglingLinks(validEntrySyncIds: Set<String>) {
        val validFileIds = files.value.mapTo(mutableSetOf()) { it.id }
        links.update { current ->
            current.filter { it.fileId in validFileIds && it.entrySyncId in validEntrySyncIds }
        }
    }
}
