package io.github.smiling_pixel

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.smiling_pixel.database.createDatabase
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.filesystem.FileRepository
import io.github.smiling_pixel.filesystem.fileManager

fun main() = application {
    val db = createDatabase(null)
    val repo = DiaryRepository(db.diaryDao())
    val fileRepo = FileRepository(fileManager, db.fileMetadataDao())
    Window(
        onCloseRequest = ::exitApplication,
        title = "MarkDay",
    ) {
        App(repo, fileRepo)
    }
}