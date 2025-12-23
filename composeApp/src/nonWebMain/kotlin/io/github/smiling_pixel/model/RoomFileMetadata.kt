package io.github.smiling_pixel.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class RoomFileMetadata(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalFileName: String,
    val filePath: String,
    val tags: List<String>,
    val createdAt: Long
)
