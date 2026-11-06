package io.github.smiling_pixel.database

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
    builder
        // Moments is unreleased, so schema changes may recreate local development databases.
        .fallbackToDestructiveMigration(true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
