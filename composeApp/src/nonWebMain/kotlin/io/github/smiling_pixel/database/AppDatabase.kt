package io.github.smiling_pixel.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import io.github.smiling_pixel.model.RoomDiaryEntry
import io.github.smiling_pixel.model.RoomFileMetadata

@Database(entities = [RoomDiaryEntry::class, RoomFileMetadata::class], version = 3)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase(), IAppDatabase {
    abstract fun roomDiaryDao(): DiaryRoomDao
    abstract fun fileMetadataRoomDao(): FileMetadataRoomDao

    // Provide a common IDiaryDao by wrapping the Room DAO with an implementation that maps
    // platform-specific Room entities to the common `DiaryEntry`.
    override fun diaryDao(): IDiaryDao = DiaryDaoImpl(roomDiaryDao())
    override fun fileMetadataDao(): IFileMetadataDao = FileMetadataDaoImpl(fileMetadataRoomDao())
}

expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
