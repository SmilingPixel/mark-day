package io.github.smiling_pixel

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.github.smiling_pixel.database.createDatabase
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.filesystem.FileRepository
import io.github.smiling_pixel.filesystem.InMemoryFileManager

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val db = createDatabase(null)
    val repo = DiaryRepository(db.diaryDao())
    val fileRepo = FileRepository(InMemoryFileManager(), db.fileMetadataDao())
    ComposeViewport {
        App(repo, fileRepo)
    }
}