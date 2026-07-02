package io.github.smiling_pixel.util

/**
 * Represents the severity of a log message.
 *
 * Loggers can use this level to filter or format output depending on
 * how important or noisy a given message is.
 */
enum class LogLevel {
    /**
     * Fine-grained diagnostic information that is useful during development
     * and troubleshooting, but typically too verbose for production logs.
     */
    DEBUG,

    /**
     * General informational messages that describe the normal flow of the
     * application, such as lifecycle events or high-level state changes.
     */
    INFO,

    /**
     * Potential problems or unusual situations that are not necessarily
     * errors but might require attention or investigation.
     */
    WARN,

    /**
     * Error conditions indicating that an operation has failed or that the
     * application is in an unexpected state and may not be able to continue
     * normally.
     */
    ERROR,
}

/**
 * Represents the result of exporting persisted log output.
 */
sealed interface LogExportResult {
    /**
     * Indicates that logs were exported successfully.
     *
     * @property fileName The generated or selected export file name.
     * @property destinationDescription A user-readable destination description.
     */
    data class Success(
        val fileName: String,
        val destinationDescription: String,
    ) : LogExportResult

    /**
     * Indicates that there are no persisted logs available to export.
     */
    data object NoLogs : LogExportResult

    /**
     * Indicates that log export is not available on the current platform.
     */
    data object Unavailable : LogExportResult

    /**
     * Indicates that export failed.
     *
     * @property message A user-readable failure message.
     */
    data class Failure(
        val message: String,
    ) : LogExportResult
}

/**
 * Cross-platform application logger.
 */
expect object Logger {
    /**
     * Logs a message at the provided [level].
     *
     * @param level The severity of the message.
     * @param tag A short tag identifying the log source.
     * @param message The message to log.
     * @param throwable Optional throwable details to include with the message.
     */
    fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )

    /**
     * Sets the minimum severity that will be emitted by this logger.
     *
     * @param level The minimum log level to emit.
     */
    fun setLogLevel(level: LogLevel)

    /**
     * Returns the current minimum severity emitted by this logger.
     *
     * @return The active minimum log level.
     */
    fun getLogLevel(): LogLevel

    /**
     * Enables or disables persisted log storage.
     *
     * @param enabled Whether filtered log entries should be persisted.
     */
    fun setPersistenceEnabled(enabled: Boolean)

    /**
     * Returns whether persisted log storage is enabled.
     *
     * @return `true` when filtered log entries are persisted.
     */
    fun isPersistenceEnabled(): Boolean

    /**
     * Exports persisted logs using the current platform's export mechanism.
     *
     * @return The export result.
     */
    suspend fun exportPersistedLogs(): LogExportResult

    /**
     * Clears persisted logs on platforms that support persistence.
     */
    suspend fun clearPersistedLogs()
}

/**
 * Logs a debug-level message using this [Logger].
 *
 * Shorthand for calling [log] with [LogLevel.DEBUG].
 *
 * @param tag A short tag identifying the log source.
 * @param message The message to be logged.
 */
fun Logger.d(
    tag: String,
    message: String,
    throwable: Throwable? = null,
) = log(LogLevel.DEBUG, tag, message, throwable)

/**
 * Logs an info-level message using this [Logger].
 *
 * Shorthand for calling [log] with [LogLevel.INFO].
 *
 * @param tag A short tag identifying the log source.
 * @param message The message to be logged.
 */
fun Logger.i(
    tag: String,
    message: String,
    throwable: Throwable? = null,
) = log(LogLevel.INFO, tag, message, throwable)

/**
 * Logs a warning-level message using this [Logger].
 *
 * Shorthand for calling [log] with [LogLevel.WARN].
 *
 * @param tag A short tag identifying the log source.
 * @param message The message to be logged.
 */
fun Logger.w(
    tag: String,
    message: String,
    throwable: Throwable? = null,
) = log(LogLevel.WARN, tag, message, throwable)

/**
 * Logs an error-level message using this [Logger].
 *
 * Shorthand for calling [log] with [LogLevel.ERROR].
 *
 * @param tag A short tag identifying the log source.
 * @param message The message to be logged.
 */
fun Logger.e(
    tag: String,
    message: String,
    throwable: Throwable? = null,
) = log(LogLevel.ERROR, tag, message, throwable)
