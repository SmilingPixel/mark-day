package io.github.smiling_pixel.util

actual object Logger {
    private var minLogLevel: LogLevel = LogLevel.ERROR

    actual fun setLogLevel(level: LogLevel) {
        minLogLevel = level
    }

    actual fun getLogLevel(): LogLevel = minLogLevel

    actual fun setPersistenceEnabled(enabled: Boolean) {
        // Log persistence is intentionally unavailable for the web trial target.
    }

    actual fun isPersistenceEnabled(): Boolean = false

    actual fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        if (level.ordinal < minLogLevel.ordinal) return
        val fullMessage =
            if (throwable != null) {
                "$message\n${throwable.stackTraceToString()}"
            } else {
                message
            }
        println("[$level] $tag: $fullMessage")
    }

    actual suspend fun exportPersistedLogs(): LogExportResult = LogExportResult.Unavailable

    actual suspend fun clearPersistedLogs() {
        // Log persistence is intentionally unavailable for the web trial target.
    }
}
