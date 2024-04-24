package library.core

import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration

/**
 * A global factor affecting adjustable timeout durations.
 */
expect val adjustableTimeoutFactor: Double

/**
 * Runs [block] with a [timeout], using [description] to document the exception thrown if the timeout occurs.
 *
 * Unlike [withTimeout], an elapsed timeout is not considered a regular coroutine cancellation, but an error.
 */
suspend fun <T> withAdjustableTimeout(
    timeout: Duration,
    description: () -> String,
    block: suspend CoroutineScope.() -> T
): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val effectiveTimeout = timeout * adjustableTimeoutFactor
    val startTime = Clock.System.now()
    try {
        return withTimeout(effectiveTimeout, block)
    } catch (exception: TimeoutCancellationException) {
        val endTime = Clock.System.now()
        throw AdjustableTimeoutException(
            "${description()}... ${exception.message} (from $startTime to $endTime," +
                " timeout factor=$adjustableTimeoutFactor)"
        )
    }
}

/**
 * An exception indicating an elapsed adjustable timeout.
 */
class AdjustableTimeoutException(message: String?, cause: Throwable? = null) :
    AssertionError(message, cause),
    CopyableThrowable<AdjustableTimeoutException> {

    override fun createCopy() = null // do not copy during stack trace recovery
}
