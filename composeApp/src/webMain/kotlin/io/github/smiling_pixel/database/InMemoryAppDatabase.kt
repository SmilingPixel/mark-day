package io.github.smiling_pixel.database

class InMemoryAppDatabase : IAppDatabase {
    private val dao = InMemoryDiaryDao()
    private val fileDao = InMemoryFileMetadataDao()

    override fun diaryDao(): IDiaryDao = dao
    override fun fileMetadataDao(): IFileMetadataDao = fileDao
}
