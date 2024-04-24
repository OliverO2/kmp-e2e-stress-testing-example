package library.telemetry

import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.slf4j.spi.LocationAwareLogger

/**
 * The JVM's thread-local or coroutine-specific logging configuration.
 *
 * Use [LoggingConfiguration.asContextElement] to specify a coroutine-specific configuration.
 */
actual val loggingConfiguration: LoggingConfiguration
    get() = threadLocalConfiguration.get()

fun LoggingConfiguration.asContextElement() = threadLocalConfiguration.asContextElement(this)

private val threadLocalConfiguration = ThreadLocal.withInitial { initialConfiguration }
private val initialConfiguration = LoggingConfiguration("JVM-default", ::emitViaSlf4J)

/**
 * Emits [logEvent] via SLF4J.
 */
fun emitViaSlf4J(logEvent: LogEvent) {
    val loggerName = logEvent.abbreviatedPath

    val logMessage = if (logEvent.exceptionDetails != null && logEvent.exception == null) {
        "${logEvent.message}\n${logEvent.exceptionDetails}"
    } else {
        logEvent.message
    }

    when (val slf4JLogger = LoggerFactory.getLogger(loggerName)) {
        is LocationAwareLogger -> {
            val slf4JLogLevel = when (logEvent.level) {
                LogLevel.DEBUG -> Level.DEBUG
                LogLevel.INFO -> Level.INFO
                LogLevel.WARNING -> Level.WARN
                LogLevel.ERROR -> Level.ERROR
            }

            slf4JLogger.log(
                null, // marker
                loggerName,
                slf4JLogLevel.toInt(),
                logMessage,
                null, // argArray
                logEvent.exception
            )
        }

        else -> {
            when (logEvent.level) {
                LogLevel.DEBUG -> slf4JLogger.debug(logMessage, logEvent.exception)
                LogLevel.INFO -> slf4JLogger.info(logMessage, logEvent.exception)
                LogLevel.WARNING -> slf4JLogger.warn(logMessage, logEvent.exception)
                LogLevel.ERROR -> slf4JLogger.error(logMessage, logEvent.exception)
            }
        }
    }
}

actual suspend fun <Result> LoggingConfiguration.use(block: suspend () -> Result): Result =
    withContext(asContextElement()) {
        block()
    }
