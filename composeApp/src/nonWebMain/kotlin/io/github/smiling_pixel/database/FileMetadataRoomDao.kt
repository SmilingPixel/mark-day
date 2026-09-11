package io.github.smiling_pixel.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.smiling_pixel.model.RoomFileMetadata
import io.github.smiling_pixel.model.RoomMomentEntryLink
import kotlinx.coroutines.flow.Flow

@Dao
interface FileMetadataRoomDao {
    @Query("SELECT * FROM RoomFileMetadata")
    fun getAllFiles(): Flow<List<RoomFileMetadata>>

    @Query("SELECT * FROM RoomFileMetadata WHERE id = :id")
    suspend fun getFileById(id: Long): RoomFileMetadata?

    @Query("SELECT * FROM RoomFileMetadata WHERE filePath = :path")
    suspend fun getFileByPath(path: String): RoomFileMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(fileMetadata: RoomFileMetadata): Long

    @Update
    suspend fun updateFile(fileMetadata: RoomFileMetadata)

    @Delete
    suspend fun deleteFile(fileMetadata: RoomFileMetadata)

    @Query("SELECT * FROM RoomMomentEntryLink")
    fun getAllLinks(): Flow<List<RoomMomentEntryLink>>

    @Query("SELECT fileId FROM RoomMomentEntryLink WHERE entrySyncId = :entrySyncId")
    suspend fun getFileIdsForEntry(entrySyncId: String): List<Long>

    @Query("SELECT entrySyncId FROM RoomMomentEntryLink WHERE fileId = :fileId")
    suspend fun getEntrySyncIdsForFile(fileId: Long): List<String>

    @Query("DELETE FROM RoomMomentEntryLink WHERE entrySyncId = :entrySyncId")
    suspend fun deleteLinksForEntry(entrySyncId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLinks(links: List<RoomMomentEntryLink>)

    @Transaction
    suspend fun replaceLinksForEntry(
        entrySyncId: String,
        fileIds: Set<Long>,
    ) {
        deleteLinksForEntry(entrySyncId)
        insertLinks(fileIds.map { RoomMomentEntryLink(it, entrySyncId) })
    }

    @Query(
        "DELETE FROM RoomMomentEntryLink WHERE entrySyncId NOT IN (:validEntrySyncIds) " +
            "OR fileId NOT IN (SELECT id FROM RoomFileMetadata)",
    )
    suspend fun deleteDanglingLinks(validEntrySyncIds: Set<String>)
}
