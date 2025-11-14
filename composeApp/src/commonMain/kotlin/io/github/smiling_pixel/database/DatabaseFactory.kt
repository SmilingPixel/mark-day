package io.github.smiling_pixel.database

// Platform-specific creation of the database should be implemented per target.
// For now we provide a no-op implementation that returns null. Platform sources
// (for example `androidMain`) can provide a concrete builder and wiring.
fun createDatabase(platformContext: Any?): IAppDatabase? = null
