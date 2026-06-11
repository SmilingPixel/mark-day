package io.github.smiling_pixel.util

import android.util.Log

actual object Logger {
    private var minLogLevel: LogLevel = LogLevel.ERROR

    actual fun setLogLevel(level: LogLevel) {
        minLogLevel = level
    }

    actual fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (level.ordinal < minLogLevel.ordinal) return
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
    }
}
