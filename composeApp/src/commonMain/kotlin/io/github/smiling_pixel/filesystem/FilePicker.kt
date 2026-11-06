package io.github.smiling_pixel.filesystem

import androidx.compose.runtime.Composable

expect class PlatformFile

expect suspend fun PlatformFile.readBytes(): ByteArray

/** Reads this selection while rejecting content larger than [maxBytes]. */
expect suspend fun PlatformFile.readBytes(maxBytes: Long): ByteArray

expect fun PlatformFile.name(): String

/** Returns the source-reported byte size, or null when unavailable. */
expect fun PlatformFile.sizeBytes(): Long?

/** Returns the source-reported MIME type, or null when unavailable. */
expect fun PlatformFile.mimeType(): String?

interface FilePickerLauncher {
    fun launch()
}

@Composable
expect fun rememberFilePicker(onFilesSelected: (List<PlatformFile>) -> Unit): FilePickerLauncher
