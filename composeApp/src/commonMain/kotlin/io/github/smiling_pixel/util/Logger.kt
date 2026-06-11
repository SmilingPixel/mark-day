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
    ERROR
}

expect object Logger {
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null)
    fun setLogLevel(level: LogLevel)
}

/**
 * Logs a debug-level message using this [Logger].
 *
 * Shorthand for calling [log] with [LogLevel.DEBUG].
 *
 * @param tag A short tag identifying the log source.
 * @param message The message to be logged.
 */
fun Logger.d(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.DEBUG, tag, message, throwable)

/**
 * Logs an info-level message using this [Logger].
 *
 * Shorthand for calling [log] with [LogLevel.INFO].
 *
 * @param tag A short tag identifying the log source.
 * @param message The message to be logged.
 */
fun Logger.i(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.INFO, tag, message, throwable)

/**
 * Logs a warning-level message using this [Logger].
 *
 * Shorthand for calling [log] with [LogLevel.WARN].
 *
 * @param tag A short tag identifying the log source.
 * @param message The message to be logged.
 */
fun Logger.w(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.WARN, tag, message, throwable)

/**
 * Logs an error-level message using this [Logger].
 *
 * Shorthand for calling [log] with [LogLevel.ERROR].
 *
 * @param tag A short tag identifying the log source.
 * @param message The message to be logged.
 */
fun Logger.e(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)
