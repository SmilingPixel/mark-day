package io.github.smiling_pixel.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    primaryKeys = ["fileId", "entrySyncId"],
    foreignKeys = [
        ForeignKey(
            entity = RoomFileMetadata::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RoomDiaryEntry::class,
            parentColumns = ["syncId"],
            childColumns = ["entrySyncId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("fileId"), Index("entrySyncId")],
)
data class RoomMomentEntryLink(
    val fileId: Long,
    val entrySyncId: String,
)
