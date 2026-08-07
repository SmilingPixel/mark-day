package io.github.smiling_pixel

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.database.createDatabase
import io.github.smiling_pixel.draft.EditorExitGuard
import io.github.smiling_pixel.filesystem.FileRepository
import io.github.smiling_pixel.filesystem.fileManager
import kotlinx.coroutines.launch

fun main() =
    application {
        val db = createDatabase(null)
        val repo = DiaryRepository(db.diaryDao())
        val fileRepo = FileRepository(fileManager, db.fileMetadataDao())
        var editorExitGuard by remember { mutableStateOf<EditorExitGuard?>(null) }
        var showUnsafeExitDialog by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        Window(
            onCloseRequest = {
                val guard = editorExitGuard
                if (guard?.hasUnpersistedChanges != true) {
                    exitApplication()
                } else {
                    // Window close is outside App's navigation callbacks, so perform the same immediate flush here and
                    // close automatically when it succeeds.
                    scope.launch {
                        if (guard.persistLatest()) {
                            exitApplication()
                        } else {
                            showUnsafeExitDialog = true
                        }
                    }
                }
            },
            title = "MarkDay",
        ) {
            App(repo, fileRepo, onExitGuardChange = { editorExitGuard = it })
            if (showUnsafeExitDialog) {
                AlertDialog(
                    onDismissRequest = { showUnsafeExitDialog = false },
                    title = { Text("Draft not saved") },
                    text = { Text("The latest changes couldn’t be saved. Closing now may lose them.") },
                    confirmButton = {
                        TextButton(onClick = ::exitApplication) { Text("Close anyway") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUnsafeExitDialog = false }) { Text("Stay") }
                    },
                )
            }
        }
    }
