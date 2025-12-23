package io.github.smiling_pixel.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.smiling_pixel.model.RoomFileMetadata
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
}
