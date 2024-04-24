@file:OptIn(PackagePrivate::class)

package example.backend.database

import example.transport.Interactor
import example.transport.TextLine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import library.core.DebugTraceTarget
import library.core.GuardedBy
import library.core.createDebugTrace
import library.core.debugString
import library.core.debugTrace
import library.core.removeDebugTrace
import library.core.simpleClassName
import library.telemetry.Telemetry
import java.io.Closeable

private val logger = Telemetry.logger("example.backend.database.SubscriptionKt")

/**
 * A subscription is a strategy observing database changes and forwarding those changes on request.
 *
 * The collector of [changedTextLineBatches] can be slow without losing changes or stalling upstream. A slow
 * collector will just see the latest state per collected batch.
 */
class Subscription(
    private val database: Database,
    private val interactor: Interactor,
    private val enableDebugTrace: Boolean = false
) : Closeable, DebugTraceTarget {
    init {
        database.registerSubscription(this)
        if (enableDebugTrace) createDebugTrace()
    }

    /** A batch of changed [TextLine]s, updated continually by the observed [Database]. */
    @GuardedBy("this")
    private var changedTextLineBatch = database.slate.lines().associateBy { it.id }.toMutableMap()

    /** Trigger signalling that [changedTextLineBatch] contains at least one change. */
    @GuardedBy("this") // for channel send only
    private val changesPendingTrigger = Channel<Unit>(Channel.CONFLATED)

    /** Returns a flow of changed line batches. */
    fun changedTextLineBatches(): Flow<Collection<TextLine>> = flow {
        debugTrace?.log("changedTextLineBatches() flow starting")

        changesPendingTrigger.send(Unit) // force an initial update

        for (trigger in changesPendingTrigger) {
            debugTrace?.log("changedTextLineBatches() received changesPendingTrigger")

            val changedLines: Map<Int, TextLine>

            with(this@Subscription) {
                synchronized(this) {
                    changedLines = this.changedTextLineBatch
                    this.changedTextLineBatch = mutableMapOf() // start a fresh batch
                }
            }

            logger.debug {
                "${this@Subscription} changedTextLineBatches() emitting ${changedLines.size} lines: " +
                    changedLines.values.debugString()
            }
            debugTrace?.log {
                "changedTextLineBatches() emitting ${changedLines.size} lines: " +
                    changedLines.values.debugString()
            }
            emit(changedLines.values)
        }
    }

    /** Updates the state of [textLine] for this subscription. */
    @PackagePrivate
    internal fun update(textLine: TextLine) {
        synchronized(this) {
            debugTrace?.log(null) { "update() change: $textLine" }
            changedTextLineBatch[textLine.id] = textLine

            if (changesPendingTrigger.trySend(Unit).isSuccess) {
                debugTrace?.log(null, "update() offered changesPendingTrigger")
            }
        }
    }

    override fun close() {
        database.unregisterSubscription(this)
        if (enableDebugTrace) removeDebugTrace()
    }

    override fun toString(): String = "$simpleClassName(interactor=$interactor)"
}
