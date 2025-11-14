package io.github.smiling_pixel.database

import io.github.smiling_pixel.model.DiaryEntry

// Common abstraction for the application database. Platform-specific implementations
// (for example a Room-based AppDatabase) should implement this interface.
interface IAppDatabase {
    fun diaryDao(): IDiaryDao
}
