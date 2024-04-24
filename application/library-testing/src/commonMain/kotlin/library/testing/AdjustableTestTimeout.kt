package library.testing

import io.kotest.assertions.failure
import kotlinx.coroutines.CoroutineScope
import library.core.AdjustableTimeoutException
import library.core.withAdjustableTimeout
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration

/**
 * Runs [block] with a [timeout], using [description] to document the exception thrown if the timeout occurs.
 *
 * Unlike [withAdjustableTimeout], this function throws an exception compatible with Kotest, incorporating any
 * clues, if present.
 */
suspend fun <T> withAdjustableTestTimeout(
    timeout: Duration,
    description: () -> String,
    block: suspend CoroutineScope.() -> T
): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    try {
        return withAdjustableTimeout(timeout, description, block)
    } catch (exception: AdjustableTimeoutException) {
        // Throw an exception which incorporates Kotest's clues, if present.
        throw failure(exception.message ?: "$exception", null)
    }
}
