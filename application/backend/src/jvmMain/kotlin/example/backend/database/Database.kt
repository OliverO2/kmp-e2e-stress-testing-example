@file:OptIn(PackagePrivate::class)

package example.backend.database

import example.transport.Slate
import example.transport.TextLine
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import library.core.GuardedBy
import library.core.simpleClassName
import library.telemetry.Telemetry
import library.telemetry.withDebugLogging
import java.io.Closeable

private val logger = Telemetry.logger("example.backend.database.DatabaseKt")

/**
 * In-memory database storing a single slate of text lines.
 *
 * At construction time, each instance launches a coroutine in `scope`. It will run until [close]d.
 */
class Database(
    scope: CoroutineScope,
    private val name: String,
    elementCount: Int
) : Closeable {
    /** The entire 'data model' of this database. */
    val slate = Slate(elementCount)

    @GuardedBy("itself")
    private val subscriptions = HashSet<Subscription>()

    @GuardedBy("commitMutex") // for channel send only
    private val textLineUpdates = Channel<TextLine>(1_000)

    private val commitMutex = Mutex() // guards properties and channel sends during an atomic commit

    /** Coroutine that distributes updated [TextLine]s to all registered subscriptions. */
    private val subscriptionUpdateJob: Job =
        scope.launch(CoroutineName("Subscription-Updater-$this")) {
            logger.withDebugLogging(prefix = "subscription update processing") {
                for (textLine in textLineUpdates) {
                    synchronized(subscriptions) {
                        for (subscription in subscriptions) {
                            subscription.update(textLine)
                        }
                    }
                }
            }
        }

    @PackagePrivate
    internal fun registerSubscription(subscription: Subscription) = synchronized(subscriptions) {
        subscriptions.add(subscription)
    }

    @PackagePrivate
    internal fun unregisterSubscription(subscription: Subscription) = synchronized(subscriptions) {
        subscriptions.remove(subscription)
    }

    suspend fun update(candidateTextLine: TextLine) = commitMutex.withLock {
        val committedTextLine: TextLine
        if (candidateTextLine.value.endsWith("-do")) {
            if (intentionalDefectCountdown?.decrementAndGet() == 0) {
                logger.debug { "failing to reject $candidateTextLine" }
                committedTextLine = TextLine(
                    candidateTextLine.id,
                    candidateTextLine.value.replace("-do", "-oh-no"),
                    null
                )
            } else {
                logger.debug { "rejecting update $candidateTextLine" }
                committedTextLine = TextLine(
                    candidateTextLine.id,
                    candidateTextLine.value.replace("-do", "-done"),
                    null
                )
            }
        } else {
            logger.debug { "updating $candidateTextLine" }
            committedTextLine = candidateTextLine
        }
        slate.update(committedTextLine)
        textLineUpdates.send(committedTextLine)
    }

    override fun close() {
        subscriptionUpdateJob.cancel(CancellationException("$this closing"))
    }

    override fun toString(): String = "$simpleClassName(\"$name\")"
}

var intentionalDefectCountdown = System.getProperty("application.test.defectCountdown")?.toIntOrNull()?.let {
    atomic(it)
}
