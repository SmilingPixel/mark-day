package io.github.smiling_pixel.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class RoomDiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val title: String,
    val content: String,
    // store timestamps as epoch milliseconds for Room persistence
    val createdAt: Long,
    val updatedAt: Long,
)
