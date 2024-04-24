package library.telemetry

import library.core.simpleClassId

typealias LogEventEmitter = ((logEvent: LogEvent) -> Unit)

/**
 * A configuration of the logging mechanism.
 *
 * [name] is used for debugging and should be unique within a system under test.
 */
class LoggingConfiguration(val name: String, vararg emitters: LogEventEmitter) {
    private val emitters: List<LogEventEmitter> = emitters.toList()

    /**
     * Emits [logEvent] according to the configuration.
     */
    fun emit(logEvent: LogEvent) {
        for (emitter in emitters) {
            emitter(logEvent)
        }
    }

    override fun toString(): String = "$simpleClassId(\"$name\")"
}

/**
 * Execute [block] with [this] logging configuration.
 *
 * On single-threaded systems, the logging configuration will temporarily change globally. If [block] suspends,
 * other coroutines will see the changed configuration.
 *
 * TODO: Change this when ThreadContextElement is implemented on non-JVM platforms,
 *     see https://github.com/Kotlin/kotlinx.coroutines/issues/3326.
 */
expect suspend fun <Result> LoggingConfiguration.use(block: suspend () -> Result): Result

/**
 * Emits a log event via println.
 */
fun emitViaPrintln(logEvent: LogEvent) {
    println(logEvent.completeText())
}

/**
 * The currently active logging configuration.
 */
expect val loggingConfiguration: LoggingConfiguration
