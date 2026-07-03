package io.github.smiling_pixel.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser

internal actual suspend fun writeDiaryEntryExportFiles(
    files: List<DiaryEntryExportFile>
): DiaryEntryExportResult {
    return try {
        val chooser = JFileChooser().apply {
            dialogTitle = "Export MarkDay diary entries"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        val result = chooser.showSaveDialog(null)
        if (result != JFileChooser.APPROVE_OPTION) {
            return DiaryEntryExportResult.Failure("Diary entry export was cancelled.")
        }

        val directory: File = chooser.selectedFile
        if (!directory.exists()) {
            directory.mkdirs()
        }
        if (!directory.isDirectory) {
            return DiaryEntryExportResult.Failure("Selected destination is not a folder.")
        }

        withContext(Dispatchers.IO) {
            for (file in files) {
                File(directory, file.fileName).writeBytes(file.content)
            }
        }

        DiaryEntryExportResult.Success(files.size, directory.absolutePath)
    } catch (e: Exception) {
        DiaryEntryExportResult.Failure(e.message ?: "Unable to export diary entries.")
    }
}
