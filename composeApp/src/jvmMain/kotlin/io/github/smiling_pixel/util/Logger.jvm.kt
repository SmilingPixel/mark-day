package io.github.smiling_pixel.util

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JFileChooser

actual object Logger {
    private const val MAX_LOG_BYTES = 1_048_576L
    private const val LOG_FILE_NAME = "markday.log"
    private const val PREVIOUS_LOG_FILE_NAME = "markday.previous.log"

    @Volatile
    private var minLogLevel: LogLevel = LogLevel.ERROR
    @Volatile
    private var isPersistenceEnabled: Boolean = false
    private val lock = Any()

    actual fun setLogLevel(level: LogLevel) {
        minLogLevel = level
    }

    actual fun getLogLevel(): LogLevel = minLogLevel

    actual fun setPersistenceEnabled(enabled: Boolean) {
        isPersistenceEnabled = enabled
    }

    actual fun isPersistenceEnabled(): Boolean = isPersistenceEnabled

    actual fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (level.ordinal < minLogLevel.ordinal) return
        val stream = if (level == LogLevel.ERROR || level == LogLevel.WARN) System.err else System.out
        val fullMessage = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        stream.println("[$level] $tag: $fullMessage")
        if (isPersistenceEnabled) {
            persist(level, tag, message, throwable)
        }
    }

    actual suspend fun exportPersistedLogs(): LogExportResult {
        return try {
            val content = synchronized(lock) { readPersistedLogs() }
            if (content.isBlank()) {
                return LogExportResult.NoLogs
            }

            val chooser = JFileChooser().apply {
                selectedFile = File(timestampedExportFileName())
                dialogTitle = "Export MarkDay logs"
            }
            val result = chooser.showSaveDialog(null)
            if (result != JFileChooser.APPROVE_OPTION) {
                return LogExportResult.Failure("Log export was cancelled.")
            }

            val file = chooser.selectedFile
            file.writeText(content)
            LogExportResult.Success(file.name, file.absolutePath)
        } catch (e: Exception) {
            LogExportResult.Failure(e.message ?: "Unable to export logs.")
        }
    }

    actual suspend fun clearPersistedLogs() {
        synchronized(lock) {
            logFile().delete()
            previousLogFile().delete()
        }
    }

    private fun persist(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        try {
            synchronized(lock) {
                val file = logFile()
                file.parentFile?.mkdirs()
                if (file.length() >= MAX_LOG_BYTES) {
                    previousLogFile().delete()
                    file.renameTo(previousLogFile())
                }
                file.appendText(formatLine(level, tag, message, throwable))
            }
        } catch (e: Exception) {
            // Logging must never fail the app or recursively log logger-internal errors.
        }
    }

    private fun readPersistedLogs(): String {
        val previous = previousLogFile().takeIf { it.exists() }?.readText().orEmpty()
        val current = logFile().takeIf { it.exists() }?.readText().orEmpty()
        return previous + current
    }

    private fun formatLine(level: LogLevel, tag: String, message: String, throwable: Throwable?): String {
        val fullMessage = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        return "${timestamp()} [$level] $tag: $fullMessage\n"
    }

    private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())

    private fun timestampedExportFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "markday-log-$timestamp.txt"
    }

    private fun logsDir(): File = File(System.getProperty("user.home"), ".markday/logs")

    private fun logFile(): File = File(logsDir(), LOG_FILE_NAME)

    private fun previousLogFile(): File = File(logsDir(), PREVIOUS_LOG_FILE_NAME)
}
