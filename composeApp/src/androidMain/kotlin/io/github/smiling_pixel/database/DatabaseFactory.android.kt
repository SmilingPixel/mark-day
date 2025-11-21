package io.github.smiling_pixel.database

import android.content.Context

/**
 * Helper to create an Android-backed `IDiaryDao` from an Android `Context`.
 * This is not an `expect/actual` pairing; it's an Android-only helper that
 * callers on Android can invoke to obtain a ready-to-use DAO.
 */
fun createAndroidDiaryDao(context: Context): IDiaryDao {
    val db = AppDatabase.build(context)
    return db.diaryDao()
}
