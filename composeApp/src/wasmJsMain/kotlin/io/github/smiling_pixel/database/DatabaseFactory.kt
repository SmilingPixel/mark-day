package io.github.smiling_pixel.database

actual fun createDatabase(platformContext: Any?): IAppDatabase {
    return InMemoryAppDatabase()
}
