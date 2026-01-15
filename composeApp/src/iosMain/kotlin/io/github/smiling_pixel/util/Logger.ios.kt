package io.github.smiling_pixel.util

import platform.Foundation.NSLog

actual object Logger {
    actual fun log(level: LogLevel, tag: String, message: String) {
        // NSLog automatically adds timestamp and process info
        NSLog("[$level] $tag: $message")
    }
}
