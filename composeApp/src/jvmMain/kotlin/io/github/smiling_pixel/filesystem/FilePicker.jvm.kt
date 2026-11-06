package io.github.smiling_pixel.filesystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

actual class PlatformFile(
    val file: File,
)

actual suspend fun PlatformFile.readBytes(): ByteArray =
    withContext(Dispatchers.IO) {
        file.readBytes()
    }

actual suspend fun PlatformFile.readBytes(maxBytes: Long): ByteArray =
    withContext(Dispatchers.IO) {
        require(file.length() <= maxBytes) { "Selected file exceeds the size limit." }
        file.readBytes()
    }

actual fun PlatformFile.name(): String = file.name

actual fun PlatformFile.sizeBytes(): Long? = file.length()

actual fun PlatformFile.mimeType(): String? = runCatching { Files.probeContentType(file.toPath()) }.getOrNull()

@Composable
actual fun rememberFilePicker(onFilesSelected: (List<PlatformFile>) -> Unit): FilePickerLauncher =
    remember {
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
