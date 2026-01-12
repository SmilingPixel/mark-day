package io.github.smiling_pixel.util

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

expect object Logger {
    fun log(level: LogLevel, tag: String, message: String)
}

fun Logger.d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
fun Logger.i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
fun Logger.w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
fun Logger.e(tag: String, message: String) = log(LogLevel.ERROR, tag, message)
