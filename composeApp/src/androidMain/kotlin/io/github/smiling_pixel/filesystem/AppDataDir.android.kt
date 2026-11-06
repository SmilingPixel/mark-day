package io.github.smiling_pixel.filesystem

import io.github.smiling_pixel.preference.AndroidContextProvider

internal actual fun getAppDataDir(): String = AndroidContextProvider.context.filesDir.absolutePath
