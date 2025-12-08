package io.github.smiling_pixel.database

import android.content.Context
import androidx.room.Room

actual fun createDatabase(platformContext: Any?): IAppDatabase {
    val context = platformContext as Context
    return getRoomDatabase(
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "app.db"
        )
    )
}
