package io.github.smiling_pixel.filesystem

import androidx.compose.runtime.Composable

expect class PlatformFile

expect suspend fun PlatformFile.readBytes(): ByteArray
expect fun PlatformFile.name(): String

interface FilePickerLauncher {
    fun launch()
}

@Composable
expect fun rememberFilePicker(onFilesSelected: (List<PlatformFile>) -> Unit): FilePickerLauncher
