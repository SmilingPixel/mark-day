package io.github.smiling_pixel.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.smiling_pixel.model.RoomDiaryEntry

@Database(entities = [RoomDiaryEntry::class], version = 2)
abstract class AppDatabase : RoomDatabase(), IAppDatabase {
    abstract fun roomDiaryDao(): DiaryRoomDao

    // Provide a common IDiaryDao by wrapping the Room DAO with an implementation that maps
    // platform-specific Room entities to the common `DiaryEntry`.
    override fun diaryDao(): IDiaryDao = DiaryDaoImpl(roomDiaryDao())

    companion object {
        fun build(context: Context): AppDatabase {
            // Use destructive migration during development to keep schema changes simple.
            return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "app-db")
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
