package io.github.smiling_pixel.preference

import android.content.Context
import java.io.File

object AndroidContextProvider {
    lateinit var context: Context
}

actual fun producePath(): String {
    return AndroidContextProvider.context.filesDir.resolve(DATA_STORE_FILE_NAME).absolutePath
}
