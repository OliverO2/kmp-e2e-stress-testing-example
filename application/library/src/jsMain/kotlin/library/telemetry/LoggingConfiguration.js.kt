package library.telemetry

/**
 * The JS platform's active logging configuration.
 */
actual val loggingConfiguration: LoggingConfiguration
    get() = globalLoggingConfiguration

private var globalLoggingConfiguration: LoggingConfiguration =
    LoggingConfiguration("JS-default", ::emitToBrowserConsole)

fun LoggingConfiguration.activateGlobally() {
    globalLoggingConfiguration = this
}

actual suspend fun <Result> LoggingConfiguration.use(block: suspend () -> Result): Result {
    val originalConfiguration = loggingConfiguration
    activateGlobally()
    try {
        return block()
    } finally {
        originalConfiguration.activateGlobally()
    }
}

/**
 * Emits [logEvent] to the browser's console.
 */
fun emitToBrowserConsole(logEvent: LogEvent) {
    val eventText = logEvent.completeText()
    when (logEvent.level) {
        LogLevel.DEBUG -> console.log(eventText)
        LogLevel.INFO -> console.info(eventText)
        LogLevel.WARNING -> console.warn(eventText)
        LogLevel.ERROR -> console.error(eventText)
    }
}
