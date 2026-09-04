package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.FileMetadata
import io.github.smiling_pixel.model.MomentEntryLink
import kotlinx.coroutines.flow.Flow

/** Persists Moments metadata and device-local entry relationships. */
interface IFileMetadataDao {
    /** Observes all Moment metadata. */
    fun getAllFiles(): Flow<List<FileMetadata>>

    /** Returns the Moment with [id], or null. */
    suspend fun getFileById(id: Long): FileMetadata?

    /** Returns the Moment stored at [path], or null. */
    suspend fun getFileByPath(path: String): FileMetadata?

    /** Inserts [fileMetadata] and returns its local identifier. */
    suspend fun insertFile(fileMetadata: FileMetadata): Long

    /** Replaces persisted metadata for an existing Moment. */
    suspend fun updateFile(fileMetadata: FileMetadata)

    /** Deletes [fileMetadata] and its relationships. */
    suspend fun deleteFile(fileMetadata: FileMetadata)

    /** Observes all Moment-to-entry relationships. */
    fun getAllLinks(): Flow<List<MomentEntryLink>>

    /** Returns Moment identifiers linked to [entrySyncId]. */
    suspend fun getFileIdsForEntry(entrySyncId: String): Set<Long>

    /** Returns entry identifiers linked to [fileId]. */
    suspend fun getEntrySyncIdsForFile(fileId: Long): Set<String>

    /** Atomically replaces all Moment relationships for [entrySyncId]. */
    suspend fun replaceLinksForEntry(
        entrySyncId: String,
        fileIds: Set<Long>,
    )

    /** Inserts [links], ignoring relationships that already exist. */
    suspend fun restoreLinks(links: List<MomentEntryLink>)

    /** Removes every Moment relationship belonging to [entrySyncId]. */
    suspend fun deleteLinksForEntry(entrySyncId: String)

    /** Removes relationships whose file or entry no longer exists. */
    suspend fun deleteDanglingLinks(validEntrySyncIds: Set<String>)
}
