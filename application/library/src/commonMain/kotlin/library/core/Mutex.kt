package library.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Executes [action] like [Mutex.withLock], or unconditionally if [this] is `null`.
 */
suspend inline fun <Result> Mutex?.withLockIfNonNull(action: () -> Result): Result {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }

    return if (this == null) {
        action()
    } else {
        withLock {
            action()
        }
    }
}
