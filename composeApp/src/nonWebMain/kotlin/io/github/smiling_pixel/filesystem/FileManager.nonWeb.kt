package io.github.smiling_pixel.filesystem

import okio.FileSystem

internal expect fun getAppDataDir(): String

actual val fileManager: FileManager by lazy {
    LocalFileManager(FileSystem.SYSTEM, getAppDataDir())
}
