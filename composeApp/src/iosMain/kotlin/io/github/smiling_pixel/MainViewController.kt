package io.github.smiling_pixel

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import io.github.smiling_pixel.database.createDatabase
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.filesystem.FileRepository
import io.github.smiling_pixel.filesystem.fileManager

fun MainViewController() = ComposeUIViewController {
    val (repo, fileRepo) = remember {
        val db = createDatabase(null)
        Pair(
            DiaryRepository(db.diaryDao()),
            FileRepository(fileManager, db.fileMetadataDao())
        )
    }
    App(repo, fileRepo)
}