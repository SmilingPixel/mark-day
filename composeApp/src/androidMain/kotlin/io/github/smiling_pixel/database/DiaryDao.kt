package io.github.smiling_pixel.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import io.github.smiling_pixel.model.RoomDiaryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryRoomDao {
    @Query("SELECT * FROM RoomDiaryEntry")
    fun getAllFlow(): Flow<List<RoomDiaryEntry>>

    @Query("SELECT * FROM RoomDiaryEntry")
    suspend fun getAll(): List<RoomDiaryEntry>

    @Insert
    suspend fun insert(entry: RoomDiaryEntry): Long

    @Delete
    suspend fun delete(entry: RoomDiaryEntry)
}
