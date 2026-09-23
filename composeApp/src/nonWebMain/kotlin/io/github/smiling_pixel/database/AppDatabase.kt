package io.github.smiling_pixel.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import io.github.smiling_pixel.model.RoomDiaryEntry
import io.github.smiling_pixel.model.RoomFileMetadata
import io.github.smiling_pixel.model.RoomMomentEntryLink

@Database(
    entities = [RoomDiaryEntry::class, RoomFileMetadata::class, RoomMomentEntryLink::class],
    // Moments is pre-release; the current schema is authoritative and older local databases may be recreated.
    version = 6,
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase :
    RoomDatabase(),
    IAppDatabase {
    abstract fun roomDiaryDao(): DiaryRoomDao

    abstract fun fileMetadataRoomDao(): FileMetadataRoomDao

    // Provide a common IDiaryDao by wrapping the Room DAO with an implementation that maps
    // platform-specific Room entities to the common `DiaryEntry`.
    override fun diaryDao(): IDiaryDao = DiaryDaoImpl(roomDiaryDao())

    override fun fileMetadataDao(): IFileMetadataDao = FileMetadataDaoImpl(fileMetadataRoomDao())
}

expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
