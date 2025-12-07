package io.github.smiling_pixel.filesystem

import io.github.smiling_pixel.preference.AndroidContextProvider

internal actual fun getAppDataDir(): String {
    return AndroidContextProvider.context.filesDir.absolutePath
}
