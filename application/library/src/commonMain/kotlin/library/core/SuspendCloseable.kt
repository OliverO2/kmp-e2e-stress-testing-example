package library.core

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * a source or destination of data that can be closed in a suspendable way.
 * The close method is invoked to release resources that the object is holding (such as open files).
 */
interface SuspendCloseable {
    suspend fun close()
}

/**
 * Executes [block] on this resource and then closes it down correctly whether an exception is thrown or not.
 */
suspend inline fun <Type : SuspendCloseable, Result> Type.use(block: (Type) -> Result): Result {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    var blockException: Throwable? = null

    try {
        return block(this)
    } catch (exception: Throwable) {
        blockException = exception
        throw exception
    } finally {
        withContext(NonCancellable) {
            if (blockException == null) {
                close()
            } else {
                try {
                    close()
                } catch (closeException: Throwable) {
                    blockException.addSuppressed(closeException)
                }
            }
        }
    }
}

/**
 * Executes [block] on this resource and then closes it down correctly whether an exception is thrown or not.
 *
 * In contrast to [use], the resource acts as a receiver for [block] (like in [with]).
 */
suspend inline fun <Type : SuspendCloseable, Result> Type.useWith(block: Type.() -> Result): Result = use { it.block() }
