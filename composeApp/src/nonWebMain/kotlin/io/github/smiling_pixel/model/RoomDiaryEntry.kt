package io.github.smiling_pixel.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index(value = ["syncId"], unique = true)])
data class RoomDiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val syncId: String,
    val title: String,
    val content: String,
    // store timestamps as epoch milliseconds for Room persistence
    val createdAt: Long,
    val updatedAt: Long,
    val entryDate: Long, // epoch days
)
