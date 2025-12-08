package io.github.smiling_pixel.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import io.github.smiling_pixel.model.RoomDiaryEntry

@Database(entities = [RoomDiaryEntry::class], version = 2)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase(), IAppDatabase {
    abstract fun roomDiaryDao(): DiaryRoomDao

    // Provide a common IDiaryDao by wrapping the Room DAO with an implementation that maps
    // platform-specific Room entities to the common `DiaryEntry`.
    override fun diaryDao(): IDiaryDao = DiaryDaoImpl(roomDiaryDao())
}

expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
