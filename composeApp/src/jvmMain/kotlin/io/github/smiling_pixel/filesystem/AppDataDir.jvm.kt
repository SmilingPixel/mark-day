package io.github.smiling_pixel.filesystem

import java.io.File

internal actual fun getAppDataDir(): String {
    val userHome = System.getProperty("user.home")
    val appDir = File(userHome, ".markday")
    if (!appDir.exists()) {
        appDir.mkdirs()
    }
    return appDir.absolutePath
}
