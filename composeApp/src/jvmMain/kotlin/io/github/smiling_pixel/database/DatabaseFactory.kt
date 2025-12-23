package io.github.smiling_pixel.database

import androidx.room.Room
import java.io.File

actual fun createDatabase(platformContext: Any?): IAppDatabase {
    val dbFile = File(System.getProperty("user.home"), "markday.db")
    return getRoomDatabase(
        Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
        )
    )
}
