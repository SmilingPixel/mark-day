package io.github.smiling_pixel.database

class InMemoryAppDatabase : IAppDatabase {
    private val dao = InMemoryDiaryDao()
    override fun diaryDao(): IDiaryDao = dao
}
