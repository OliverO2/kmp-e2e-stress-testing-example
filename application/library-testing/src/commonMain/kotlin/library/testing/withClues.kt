package library.testing

import io.kotest.assertions.failure
import io.kotest.assertions.withClue
import kotlinx.coroutines.CancellationException

/**
 * Runs [action], documenting failures with a number of [clues].
 *
 * Also ensures that exceptions produced without invoking Kotest's `failure` method are provided with clues
 * from this `withClues` invocation.
 */
suspend fun <R> withClues(vararg clues: () -> Any?, action: suspend () -> R): R {
    val cluePrefix = "### Clues:"
    val prefixedClues = if (clues.isEmpty()) clues else arrayOf(cluePrefix, *clues)
    val innerAction: suspend () -> R = {
        try {
            action()
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (throwable: Throwable) {
            if (throwable.message?.startsWith(cluePrefix) == true) {
                throw throwable
            } else {
                // This invocation provides an AssertionError with a message containing clues.
                throw failure(throwable.message ?: "(no exception message)", throwable)
            }
        }
    }
    val clueWrappedAction = prefixedClues.reversed().fold(innerAction) { wrappedAction, clue ->
        {
            withClue(clue) {
                wrappedAction()
            }
        }
    }

    return clueWrappedAction()
}
