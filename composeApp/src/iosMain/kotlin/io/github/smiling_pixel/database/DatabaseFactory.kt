package io.github.smiling_pixel.database

import androidx.room.Room
import platform.Foundation.NSHomeDirectory

actual fun createDatabase(platformContext: Any?): IAppDatabase {
    val dbFile = NSHomeDirectory() + "/markday.db"
    return getRoomDatabase(
        Room.databaseBuilder<AppDatabase>(
            name = dbFile,
        )
    )
}
