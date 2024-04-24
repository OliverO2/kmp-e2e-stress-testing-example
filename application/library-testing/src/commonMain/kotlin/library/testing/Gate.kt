package library.testing

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import library.telemetry.Telemetry
import kotlin.reflect.KMutableProperty0
import kotlin.time.Duration.Companion.seconds

/**
 * A gate which suspends a party wanting to [pass] as long as another party is in a [keepClosed] block.
 */
class Gate(isOpen: Boolean = true) {
    private val mutex = Mutex(locked = !isOpen)
    val isOpen get() = !mutex.isLocked

    /**
     * Suspends the caller while the gate is closed.
     *
     * If [waitAction] is given, it is executed if the gate is not immediately open.
     */
    suspend fun pass(waitAction: (suspend () -> Unit)? = null) {
        if (!mutex.isLocked) return // fast path
        if (!mutex.tryLock()) {
            waitAction?.invoke()
            mutex.lock()
        }
        mutex.unlock()
    }

    fun open() = mutex.unlock()

    suspend fun close() = mutex.lock()

    /**
     * Keeps the gate closed while running [action].
     */
    suspend fun <Result> keepClosed(action: suspend () -> Result) = mutex.withLock { action() }
}

/**
 * Keeps the gate closed until [action] has completed and a subsequent [predicate] invocation returns true.
 *
 * [interceptor] is a reference to an interception function variable, which this function will use to assign its
 * internal interception function. [interceptor]'s target must be `null` initially and must be available for
 * exclusive use by [keepClosedUntil] while it runs. It is the caller's responsibility to ensure that the
 * [interceptor]'s target function will be invoked for each [Interception] to be captured.
 *
 * [predicate] is then invoked for each captured [Interception].
 *
 * Example usage:
 * ```
 * frontendSendGate.keepClosedUntil(
 *     interceptor = backendMonitor::postSendInterceptor,
 *     predicate = { it.payload is TextTransportModel }
 * ) {
 *     // action
 * }
 * ```
 */
suspend fun <Interception, Result> Gate.keepClosedUntil(
    interceptor: KMutableProperty0<(suspend (interception: Interception) -> Unit)?>,
    predicate: (interception: Interception) -> Boolean,
    action: suspend () -> Result
): Result {
    require(interceptor.get() == null) { "Interceptor must have a non-null value when invoking this function" }

    val capturedInterceptions = Channel<Interception>(Channel.UNLIMITED)

    interceptor.set {
        capturedInterceptions.send(it)
    }

    return keepClosed {
        val result = action()
        try {
            withAdjustableTestTimeout(5.seconds, { "Waiting for predicates allowing to re-open the gate" }) {
                for (interception in capturedInterceptions) {
                    if (predicate(interception)) break
                }
            }
        } catch (exception: Throwable) {
            logger.error { "exception in keepClosedUntil: $exception" }
            throw exception
        } finally {
            interceptor.set(null)
        }
        result
    }
}

private val logger = Telemetry.logger("library.testing.GateKt")
