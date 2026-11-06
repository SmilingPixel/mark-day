package io.github.smiling_pixel.database

// Common abstraction for the application database. Platform-specific implementations
// (for example a Room-based AppDatabase) should implement this interface.
interface IAppDatabase {
    fun diaryDao(): IDiaryDao

    fun fileMetadataDao(): IFileMetadataDao
}
