package library.testing

import io.kotest.assertions.ErrorCollectionMode
import io.kotest.assertions.errorCollector
import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import library.core.adjustableTimeoutFactor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

suspend fun <T> eventuallyTimestamped(
    maximumDuration: Duration,
    name: String = "Eventually block",
    displayMode: EventuallyDisplayMode = EventuallyDisplayMode.DEFAULT,
    block: suspend () -> T
): T {
    val effectiveMaximumDuration = maximumDuration * adjustableTimeoutFactor
    val timeSource = TimeSource.Monotonic
    val timeoutMark = timeSource.markNow() + effectiveMaximumDuration
    var firstError: AssertionError? = null
    val originalErrorCollectionMode = errorCollector.getCollectionMode()
    errorCollector.setCollectionMode(ErrorCollectionMode.Hard)

    while (true) {
        try {
            return block().also {
                errorCollector.setCollectionMode(originalErrorCollectionMode)
            }
        } catch (lastError: AssertionError) {
            if (firstError == null) firstError = lastError

            if (timeoutMark.hasPassedNow()) {
                errorCollector.setCollectionMode(originalErrorCollectionMode)

                val actualDuration = timeSource.markNow() - timeoutMark + effectiveMaximumDuration
                val endTime = Clock.System.now()
                val message = buildString {
                    appendLine("$name timed out after $actualDuration (from ${endTime - actualDuration} to $endTime)")

                    listOf(
                        Triple("first", displayMode.firstError, firstError),
                        Triple("last", displayMode.lastError, lastError)
                    ).forEach { (name, displayEnabled, error) ->
                        if (displayEnabled) {
                            appendLine("\t$name error:")
                            appendLine(error.stackTraceToString().prependIndent("\t\t"))
                            appendLine("\t\t^^^ $name error ^^^")
                        }
                    }

                    if (displayMode.coroutinesInfo) {
                        append(coroutinesInfo().prependIndent("\t"))
                    }
                }

                throw EventuallyTimeoutException(message)
            }

            delay(25.milliseconds)
        }
    }
}

class EventuallyDisplayMode(
    val firstError: Boolean = false,
    val lastError: Boolean = true,
    val coroutinesInfo: Boolean = false // true
) {
    companion object {
        val DEFAULT = EventuallyDisplayMode()
    }
}

/**
 * An exception indicating that an [eventuallyTimestamped] condition was not met in time.
 */
class EventuallyTimeoutException(message: String?, cause: Throwable? = null) :
    AssertionError(message, cause),
    CopyableThrowable<EventuallyTimeoutException> {

    override fun createCopy() = null // do not copy during stack trace recovery
}
