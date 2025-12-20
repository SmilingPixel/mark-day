package io.github.smiling_pixel.filesystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

actual class PlatformFile(val file: File)

actual suspend fun PlatformFile.readBytes(): ByteArray = withContext(Dispatchers.IO) {
    file.readBytes()
}

actual fun PlatformFile.name(): String = file.name

@Composable
actual fun rememberFilePicker(onFilesSelected: (List<PlatformFile>) -> Unit): FilePickerLauncher {
    return remember {
        object : FilePickerLauncher {
            override fun launch() {
                SwingUtilities.invokeLater {
                    val chooser = JFileChooser()
                    chooser.isMultiSelectionEnabled = true
                    val result = chooser.showOpenDialog(null)
                    if (result == JFileChooser.APPROVE_OPTION) {
                        val files = chooser.selectedFiles.map { PlatformFile(it) }
                        onFilesSelected(files)
                    }
                }
            }
        }
    }
}
