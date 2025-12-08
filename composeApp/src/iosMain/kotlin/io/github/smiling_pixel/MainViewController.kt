package io.github.smiling_pixel

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import io.github.smiling_pixel.database.createDatabase
import io.github.smiling_pixel.database.DiaryRepository

fun MainViewController() = ComposeUIViewController {
    val repo = remember {
        val db = createDatabase(null)
        DiaryRepository(db.diaryDao())
    }
    App(repo)
}