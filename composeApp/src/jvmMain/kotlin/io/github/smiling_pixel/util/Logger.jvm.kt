package io.github.smiling_pixel.util

actual object Logger {
    actual fun log(level: LogLevel, tag: String, message: String) {
        val stream = if (level == LogLevel.ERROR || level == LogLevel.WARN) System.err else System.out
        stream.println("[$level] $tag: $message")
    }
}
