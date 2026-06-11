package io.github.smiling_pixel.util

actual object Logger {
    private var minLogLevel: LogLevel = LogLevel.ERROR

    actual fun setLogLevel(level: LogLevel) {
        minLogLevel = level
    }

    actual fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (level.ordinal < minLogLevel.ordinal) return
        val stream = if (level == LogLevel.ERROR || level == LogLevel.WARN) System.err else System.out
        val fullMessage = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        stream.println("[$level] $tag: $fullMessage")
    }
}
