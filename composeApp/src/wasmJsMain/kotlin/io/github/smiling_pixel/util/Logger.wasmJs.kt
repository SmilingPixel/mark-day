package io.github.smiling_pixel.util

actual object Logger {
    actual fun log(level: LogLevel, tag: String, message: String) {
        // In Kotlin Wasm, println writes to the console log
        println("[$level] $tag: $message")
    }
}
