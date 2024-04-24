package example.transport

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.Serializable
import library.core.debugString
import library.core.simpleClassName

typealias EventSequenceID = Int

/**
 * A sequenced event, identified by a monotonically increasing sequence ID.
 *
 * The sequenceID allows the receiver to detect event losses and out-of-order situations.
 */
@Serializable
data class Event internal constructor(
    val sequenceID: EventSequenceID,
    val payload: EventPayload
)

/**
 * A source of [Event]s.
 */
open class EventSource {
    private val lastEventSequenceID = atomic(INVALID_SEQUENCE_ID)

    /**
     * Returns a new event with a new sequence ID.
     *
     * NOTE: In order to have strictly monotonically increasing sequence IDs, event creation and sending must be atomic.
     * It is the caller's responsibility to guarantee this, typically either by enclosing these operations in a Mutex
     * block or by confining them to a single coroutine.
     */
    fun event(payload: EventPayload) = Event(lastEventSequenceID.incrementAndGet(), payload)

    companion object {
        private const val INVALID_SEQUENCE_ID: EventSequenceID = 0
    }
}

@Serializable
sealed interface EventPayload

/**
 * A backend request to the client for synchronization.
 *
 * The server will postpone sending events to the clients until a [SynchronizationResponse] has been received.
 * This allows a slow client to catch up.
 */
@Serializable
data object SynchronizationRequest : EventPayload

/**
 * A client-side response for synchronization.
 *
 * [requestSequenceID] refers to the corresponding backend request, which is answered by the response.
 */
@Serializable
data class SynchronizationResponse(val requestSequenceID: Int) : EventPayload

@Serializable
class FrontendInitialization(val interactor: Interactor, val slate: Slate) : EventPayload {
    override fun toString(): String = "$simpleClassName(interactor=$interactor)"
}

@Serializable
class TextLine(val id: Int, var value: String, var lastInteractor: Interactor? = null) : EventPayload {
    override fun toString(): String = "$simpleClassName(id=$id, value=\"$value\", lastInteractor=$lastInteractor)"
}

@Serializable
class Slate private constructor(private val lines: MutableList<TextLine>) : EventPayload, SynchronizedObject() {
    constructor(lineCount: Int) : this(MutableList(lineCount) { TextLine(it, "") })

    fun update(line: TextLine) = synchronized(this) {
        lines[line.id] = line
    }

    fun lines() = synchronized(this) { lines.toList() }

    fun line(id: Int) = synchronized(this) { lines[id] }

    fun copy() = synchronized(this) { Slate(lines.toMutableList()) }

    override fun toString(): String = "$simpleClassName(${lines.debugString()})"
}
