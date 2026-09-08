package io.github.smiling_pixel.filesystem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

internal actual suspend fun writeMomentExportFiles(files: List<MomentExportFile>): MomentExportResult =
    try {
        val chooser =
            JFileChooser().apply {
                dialogTitle = if (files.size == 1) "Save Moment" else "Export Moments"
                fileSelectionMode = if (files.size == 1) JFileChooser.FILES_ONLY else JFileChooser.DIRECTORIES_ONLY
                if (files.size == 1) selectedFile = File(files.first().fileName)
            }
        var result = JFileChooser.CANCEL_OPTION
        val show = Runnable { result = chooser.showSaveDialog(null) }
        if (SwingUtilities.isEventDispatchThread()) show.run() else SwingUtilities.invokeAndWait(show)
        if (result != JFileChooser.APPROVE_OPTION) return MomentExportResult.Cancelled
        val destination = chooser.selectedFile ?: return MomentExportResult.Cancelled
        withContext(Dispatchers.IO) {
            if (files.size == 1) {
                destination.writeBytes(files.first().content)
            } else {
                destination.mkdirs()
                files.forEach { file ->
                    var output = File(destination, file.fileName)
                    var suffix = 2
                    while (output.exists()) {
                        val dot = file.fileName.lastIndexOf('.')
                        val base = if (dot > 0) file.fileName.substring(0, dot) else file.fileName
                        val extension = if (dot > 0) file.fileName.substring(dot) else ""
                        output = File(destination, "$base ($suffix)$extension")
                        suffix++
                    }
                    output.writeBytes(file.content)
                }
            }
        }
        MomentExportResult.Success(files.size, destination.absolutePath)
    } catch (e: Exception) {
        MomentExportResult.Failure("Moments could not be exported (${e::class.simpleName}).")
    }
