package library.telemetry

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import library.core.simpleClassName
import kotlin.coroutines.coroutineContext

@Serializable
enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

/**
 * An instrument for logging, also a feature scope.
 *
 * If [minimumLevel] is not null, it overrides the logging category default, but not an external configuration.
 */
class Logger(
    val path: String,
    level: LogLevel
) {

    private val minimumLevel: LogLevel = level

    fun debug(message: String, exception: Throwable? = null) = emit(LogLevel.DEBUG, message, exception)

    fun debug(exception: Throwable? = null, message: () -> String) = emit(LogLevel.DEBUG, exception, message)

    fun info(message: String, exception: Throwable? = null) = emit(LogLevel.INFO, message, exception)

    fun info(exception: Throwable? = null, message: () -> String) = emit(LogLevel.INFO, exception, message)

    fun warning(message: String, exception: Throwable? = null) = emit(LogLevel.WARNING, message, exception)

    fun warning(exception: Throwable? = null, message: () -> String) = emit(LogLevel.WARNING, exception, message)

    fun error(message: String, exception: Throwable? = null) = emit(LogLevel.ERROR, message, exception)

    fun error(exception: Throwable? = null, message: () -> String) = emit(LogLevel.ERROR, exception, message)

    fun emit(level: LogLevel, message: String, exception: Throwable? = null) {
        emit(LogEvent(path, level, message, exception))
    }

    fun emit(level: LogLevel, exception: Throwable? = null, message: () -> String) {
        if (isEnabledFor(level)) {
            emitUnconditionally(LogEvent(path, level, message(), exception))
        }
    }

    fun emit(logEvent: LogEvent) {
        if (isEnabledFor(logEvent.level)) {
            emitUnconditionally(logEvent)
        }
    }

    private fun emitUnconditionally(logEvent: LogEvent) {
        loggingConfiguration.emit(logEvent)
    }

    fun isEnabledFor(level: LogLevel) = level >= minimumLevel
}

@Serializable
class LogEvent(
    val path: String,
    val level: LogLevel,
    val message: String,
    @Transient val exception: Throwable? = null
) {
    val exceptionDetails: String? = exception?.stackTraceToString()?.replaceIndent("\t")

    val abbreviatedPath
        get() = path.split('.').let { components ->
            components.mapIndexed { index, component ->
                if (index == components.lastIndex) component else component.firstOrNull() ?: ""
            }.joinToString(separator = ".")
        }

    fun completeText(multiLine: Boolean = true): String {
        val exceptionAddendum = if (exceptionDetails != null) {
            if (multiLine) {
                "\n$exceptionDetails"
            } else {
                " ${exceptionDetails.substringBefore("\n")}"
            }
        } else {
            ""
        }

        return "${level.name}: [$abbreviatedPath] $message$exceptionAddendum"
    }

    override fun toString(): String {
        val exceptionAddendum =
            if (exceptionDetails != null) ", exception = ${exceptionDetails.substringBefore("\n")}" else ""
        return "$simpleClassName($path, $level, '$message'$exceptionAddendum)"
    }
}

/**
 * Runs a suspending [action] surrounded by logging output.
 */
suspend fun <R> Logger.withLogging(level: LogLevel, prefix: String? = null, action: suspend () -> R): R {
    var throwable: Throwable? = null
    val prefixOrName = prefix ?: coroutineContext[CoroutineName]?.name ?: "Coroutine"
    try {
        emit(level) { blankSeparated(prefixOrName, "starting") }
        return action()
    } catch (caughtThrowable: Throwable) {
        throwable = caughtThrowable
        throw throwable
    } finally {
        when (throwable) {
            null -> emit(level) { blankSeparated(prefixOrName, "completed") }

            is CancellationException -> emit(level) {
                blankSeparated(
                    prefixOrName,
                    "got canceled, reason: ${throwable.message ?: "(not stated)"}"
                )
            }

            else -> error { blankSeparated(prefixOrName, "finished with $throwable") }
        }
    }
}

/**
 * Runs a suspending [action] surrounded by logging output with log level INFO.
 */
suspend fun <R> Logger.withInfoLogging(prefix: String? = null, action: suspend () -> R): R =
    withLogging(LogLevel.INFO, prefix, action)

/**
 * Runs a suspending [action] surrounded by logging output with log level INFO.
 */
suspend fun <R> Logger.withDebugLogging(prefix: String? = null, action: suspend () -> R): R =
    withLogging(LogLevel.DEBUG, prefix, action)

fun blankSeparated(vararg strings: String) = strings.joinToString(" ")
