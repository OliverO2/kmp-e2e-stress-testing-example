package library.core

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.datetime.Clock
import library.telemetry.Telemetry
import library.telemetry.blankSeparated
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

private val logger = Telemetry.logger("library.core.DebugTraceKt")

/**
 * A container for object-specific, time-stamped log messages.
 *
 * [debugTrace] property.
 * A [DebugTrace] can be created for a [DebugTraceTarget] via [createDebugTrace] and used as if it were the target's
 *
 * Debug tracing is disabled by default, in which case it incurs only a minimal performance penalty and
 * no memory overhead. To enable it, set [DebugTrace.enabled] to `true`.
 */
class DebugTrace private constructor(private val target: DebugTraceTarget) : SynchronizedObject() {
    @GuardedBy("this")
    private val messageLines = mutableListOf<String>()

    /**
     * Logs an inexpensively computed message.
     */
    fun log(coroutineContext: CoroutineContext?, message: String) = synchronized(this) {
        val coroutineInfo = coroutineContext?.get(CoroutineName)?.name?.let { " [$it]" } ?: ""
        messageLines.add("${Clock.System.now()}$coroutineInfo: $message")
    }

    /**
     * Logs an inexpensively computed message.
     *
     * This function does not suspend. It uses the `coroutineContext` to access coroutine information for logging.
     * Use the [log] version with an explicit `coroutineContext` parameter inside a critical section to avoid a
     * compiler error "suspension point is inside a critical section".
     */
    suspend fun log(message: String) = log(coroutineContext, message)

    /**
     * Logs an expensively computed message.
     */
    fun log(coroutineContext: CoroutineContext?, message: () -> String) = log(coroutineContext, message())

    /**
     * Logs an expensively computed message.
     */
    suspend fun log(message: () -> String) = log(coroutineContext, message())

    /**
     * Removes all messages from this trace.
     */
    fun clear() = synchronized(this) {
        messageLines.clear()
    }

    override fun toString() = synchronized(this) {
        if (messageLines.isEmpty()) {
            "(DebugTrace for $target is empty)"
        } else {
            "DebugTrace for $target:\n\t${messageLines.joinToString(separator = "\n\t")}"
        }
    }

    companion object : SynchronizedObject() {
        /**
         * Specifies whether debug traces are enabled globally.
         */
        @Volatile
        var enabled = false

        // Must be public for inlining
        @GuardedBy("this")
        val targetTraces = HashMap<DebugTraceTarget, DebugTrace>()

        /**
         * Creates a debug trace for [target] if traces are enabled.
         */
        internal fun createFor(target: DebugTraceTarget) {
            if (enabled) {
                synchronized(this) {
                    targetTraces[target] = DebugTrace(target)
                    logger.debug { "Created debugTrace for $target" }
                }
            }
        }

        /**
         * Removes a debug trace for [target] if traces are enabled.
         */
        internal fun removeFor(target: DebugTraceTarget) {
            if (enabled) {
                synchronized(this) {
                    logger.debug { "Removing debugTrace for $target" }
                    targetTraces.remove(target)
                }
            }
        }

        /**
         * Clears all debug traces.
         */
        fun clearAll() {
            synchronized(this) {
                targetTraces.clear()
            }
        }
    }
}

/**
 * The target of a debug trace.
 *
 * This marker interface ensures that [debugTrace] can only be applied to traceable receivers. Insisting on
 * [DebugTraceTarget] instead of [Any] guards against using the wrong receiver in multi-receiver scenarios
 * (e.g. intended receiver plus coroutine scope).
 */
interface DebugTraceTarget

/**
 * Creates a debug trace for [this] target if traces are enabled.
 */
fun DebugTraceTarget.createDebugTrace() {
    if (DebugTrace.enabled) {
        DebugTrace.createFor(this)
    }
}

/**
 * Removes a debug trace for [this] target if traces are enabled.
 */
fun DebugTraceTarget.removeDebugTrace() {
    if (DebugTrace.enabled) {
        DebugTrace.removeFor(this)
    }
}

/**
 * Returns the receiver's debug trace, if enabled, otherwise null.
 */
inline val <T : DebugTraceTarget> T.debugTrace: DebugTrace?
    get() =
        if (DebugTrace.enabled) {
            synchronized(DebugTrace.Companion) { DebugTrace.targetTraces[this] }
        } else {
            null
        }

/**
 * Runs a suspending [action] surrounded by debug trace logging.
 */
suspend fun <R> DebugTrace?.withLogging(prefix: String? = null, action: suspend () -> R): R {
    if (this == null) return action()

    var throwable: Throwable? = null
    val prefixOrName = prefix ?: coroutineContext[CoroutineName]?.name ?: "Coroutine"
    try {
        log { blankSeparated(prefixOrName, "starting") }
        return action()
    } catch (caughtThrowable: Throwable) {
        throwable = caughtThrowable
        throw throwable
    } finally {
        when (throwable) {
            null -> log(coroutineContext) { blankSeparated(prefixOrName, "completed") }

            is CancellationException -> log(coroutineContext) {
                blankSeparated(
                    prefixOrName,
                    "got canceled, reason: ${throwable.message ?: "(not stated)"}"
                )
            }

            else -> log { blankSeparated(prefixOrName, "finished with $throwable") }
        }
    }
}

/**
 * Returns extra debug trace information if available, else an empty string.
 */
fun DebugTrace?.extraInfo(separator: String = " – "): String = if (this != null) "$separator$this" else ""

/**
 *  Returns a string representation of an [Iterable]'s elements, suitably shortened for debug logging.
 */
fun <T> Iterable<T>.debugString(
    separator: CharSequence = ", ",
    prefix: CharSequence = "[",
    postfix: CharSequence = "]",
    limit: Int = 3,
    truncated: CharSequence = "...",
    transform: ((T) -> CharSequence)? = null
) = joinToString(
    separator = separator,
    prefix = prefix,
    postfix = postfix,
    limit = limit,
    truncated = truncated,
    transform = transform
)
