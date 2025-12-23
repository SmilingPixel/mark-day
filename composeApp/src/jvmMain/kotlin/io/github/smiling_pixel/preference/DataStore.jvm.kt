package io.github.smiling_pixel.preference

import java.io.File

actual fun producePath(): String {
    return File(System.getProperty("user.home"), DATA_STORE_FILE_NAME).absolutePath
}
