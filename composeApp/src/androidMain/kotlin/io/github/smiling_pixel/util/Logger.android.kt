package io.github.smiling_pixel.util

import android.content.Intent
import androidx.core.content.FileProvider
import android.util.Log
import io.github.smiling_pixel.preference.AndroidContextProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual object Logger {
    private const val MAX_LOG_BYTES = 1_048_576L
    private const val LOG_FILE_NAME = "markday.log"
    private const val PREVIOUS_LOG_FILE_NAME = "markday.previous.log"

    private var minLogLevel: LogLevel = LogLevel.ERROR
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
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
        if (isPersistenceEnabled) {
            persist(level, tag, message, throwable)
        }
    }

    actual suspend fun exportPersistedLogs(): LogExportResult {
        return try {
            val context = AndroidContextProvider.context
            val content = synchronized(lock) { readPersistedLogs() }
            if (content.isBlank()) {
                return LogExportResult.NoLogs
            }

            val exportDir = File(context.cacheDir, "log_exports").also { it.mkdirs() }
            val exportFile = File(exportDir, timestampedExportFileName())
            exportFile.writeText(content)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportFile
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "MarkDay logs")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Export MarkDay logs").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)

            LogExportResult.Success(exportFile.name, "Android share sheet")
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

    private fun logsDir(): File = File(AndroidContextProvider.context.filesDir, "logs")

    private fun logFile(): File = File(logsDir(), LOG_FILE_NAME)

    private fun previousLogFile(): File = File(logsDir(), PREVIOUS_LOG_FILE_NAME)
}
