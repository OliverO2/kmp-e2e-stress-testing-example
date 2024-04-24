package example.frontend.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import example.frontend.views.TextLineViewModel
import example.transport.Event
import example.transport.EventPayload
import example.transport.EventSource
import example.transport.FrontendInitialization
import example.transport.Interactor
import example.transport.SynchronizationRequest
import example.transport.SynchronizationResponse
import example.transport.TextLine
import io.ktor.websocket.*
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import library.core.DebugTraceTarget
import library.core.GuardedBy
import library.core.debugTrace
import library.core.extraInfo
import library.core.simpleClassName
import library.telemetry.Telemetry
import library.telemetry.withDebugLogging
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds

private val logger = Telemetry.logger("example.frontend.service.FrontendServiceKt")

/**
 * The frontend service connecting to a backend session.
 */
abstract class FrontendService(val testConfiguration: TestConfiguration? = null) :
    SynchronizedObject(), DebugTraceTarget {

    /**
     * The client interactor, a notion shared between the [FrontendService] and the corresponding backend session.
     * The frontend's [interactor] will be available when it has been [initialize]d by the backend.
     */
    @Volatile
    var interactor: Interactor? = null
        private set(value) {
            field = value
            clientId = value?.id?.value
        }

    private val outgoingBufferCapacity: Int = testConfiguration?.outgoingBufferCapacity ?: 1_000
    private val outgoingPayloads = Channel<EventPayload>(outgoingBufferCapacity)

    private lateinit var parentScope: CoroutineScope // available when launched
    private var communicationJob: Job? = null

    var clientId: String? by mutableStateOf(null)

    @GuardedBy("this")
    var viewModels by mutableStateOf(listOf<TextLineViewModel>())

    /**
     * An extensible test configuration.
     *
     * [initialize] will be called when [frontendService] launches.
     */
    open class TestConfiguration(
        val outgoingBufferCapacity: Int? = null,
        var initialize: TestConfiguration.() -> Unit = {}
    ) {
        lateinit var frontendService: FrontendService // available when initialize is invoked
        lateinit var frontendCoroutineScope: CoroutineScope // available when initialize is invoked

        var initializedInterceptor: (suspend (initialization: FrontendInitialization) -> Unit)? = null

        var receiveInterceptor: (suspend (event: Event) -> Unit)? = null
        var incomingEventProcessedInterceptor: (suspend (event: Event) -> Unit)? = null

        var preSendInterceptor: (suspend (event: Event) -> Unit)? = null
        var postSendInterceptor: (suspend (event: Event) -> Unit)? = null
    }

    /**
     * Launches the service frontend's communication in [parentScope].
     */
    fun launchIn(parentScope: CoroutineScope) {
        this.parentScope = parentScope

        communicationJob = parentScope.launch(CoroutineName("$this")) {
            logger.withDebugLogging("WebSocket connection") {
                runWebSocketSession { webSocketSession ->
                    if (testConfiguration != null) {
                        testConfiguration.frontendService = this@FrontendService
                        testConfiguration.frontendCoroutineScope = this
                        testConfiguration.initialize(testConfiguration)
                    }

                    launchedSender(webSocketSession)

                    webSocketSession.receiveFrames()
                }
            }
        }
    }

    @Suppress("RedundantSuspendModifier") // pretend we do serious stuff like flushing outgoing payloads
    suspend fun stop() {
        communicationJob?.cancel(CancellationException("$this stopping"))
        communicationJob = null
    }

    protected abstract suspend fun runWebSocketSession(
        block: suspend CoroutineScope.(webSocketSession: WebSocketSession) -> Unit
    )

    private fun CoroutineScope.launchedSender(webSocketSession: WebSocketSession): Job =
        launch(CoroutineName("${coroutineContext[CoroutineName]?.name ?: "FrontendService"}-Sender")) {
            val eventSource = EventSource()

            for (payload in outgoingPayloads) {
                // NOTE: Event creation and sending must be confined to this single coroutine in order to
                // guarantee strictly monotonically increasing event IDs.
                val event = eventSource.event(payload)
                testConfiguration?.preSendInterceptor?.invoke(event)
                try {
                    val frameData = serializationFormat.encodeToByteArray(event)
                    webSocketSession.send(frameData)
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                } catch (exception: Throwable) {
                    logger.error(
                        "Could not send an event to the backend.\n\tEvent: $event" + debugTrace.extraInfo("\n\t"),
                        exception
                    )
                    throw exception
                }
                debugTrace?.log { "sent $event" }
                testConfiguration?.postSendInterceptor?.invoke(event)
            }
        }

    private suspend fun WebSocketSession.receiveFrames() {
        for (frame in incoming) {
            when (frame) {
                is Frame.Binary -> {
                    val event: Event = serializationFormat.decodeFromByteArray(frame.data)
                    processIncomingEvent(event)
                }

                is Frame.Close -> logger.debug { "Received a close request from backend." }

                else -> logger.error { "Received an unexpected frame type '${frame.frameType}'." }
            }
        }
    }

    private suspend fun processIncomingEvent(event: Event) {
        testConfiguration?.receiveInterceptor?.invoke(event)
        debugTrace?.log { "received $event" }
        if (event.payload is SynchronizationRequest) {
            delay(1.seconds) // pretend we are slow
            queueOutgoingPayload(SynchronizationResponse(requestSequenceID = event.sequenceID))
        } else {
            processIncomingPayload(event.payload)
        }
        debugTrace?.log("processed incoming $event")
        testConfiguration?.incomingEventProcessedInterceptor?.invoke(event)
    }

    private suspend fun processIncomingPayload(payload: Any) {
        // NOTE: The following workaround is based on the observation that Compose sometimes missed recomposing
        // updates to MutableState under extreme load like in `E2EUpdateRejectionTests`.
        // Compose Snapshot sources suggest that such updates should be thread-safe, but there seem to be race
        // conditions in the code. Avoiding direct updates to the global snapshot, and applying explicit snapshots
        // instead seem to resolve the problem of missed updates.
        // Another option would be to schedule mutations by background updates via the UI thread. However, this would
        // require extra considerations to avoid deadlocks.
        // If that issue was resolved in Compose, the invocation of `Snapshot.withMutableSnapshot` should be removed.
        Snapshot.withMutableSnapshot { // WORKAROUND: make MutableState updates from non-UI threads safer
            when (payload) {
                is TextLine -> {
                    updateViewModel(payload)
                }

                is FrontendInitialization -> {
                    initialize(payload)
                }

                else -> {
                    logger.error { "Received unexpected event data '$payload'" }
                }
            }
        }
    }

    private suspend fun queueOutgoingPayload(payload: EventPayload) {
        outgoingPayloads.send(payload)
    }

    /**
     * Processes a [viewModel] changed by a view's interaction.
     */
    fun processViewChange(viewModel: TextLineViewModel, newValue: String) {
        viewModel.applyChange(newValue)
        parentScope.launch(CoroutineName("FrontendService-Debouncer"), start = CoroutineStart.UNDISPATCHED) {
            queueOutgoingPayload(viewModel.textLine.apply { lastInteractor = interactor })
        }
    }

    fun viewModel(textLine: TextLine): TextLineViewModel = viewModels[textLine.id]

    /**
     * Updates the corresponding view model from [transportModel].
     */
    private fun updateViewModel(transportModel: TextLine) = synchronized(this) {
        viewModels[transportModel.id].receiveUpdate(transportModel)
    }

    private suspend fun initialize(initialization: FrontendInitialization) {
        interactor = initialization.interactor
        viewModels = List(initialization.slate.lines().size) { TextLineViewModel(initialization.slate.line(it)) }
        testConfiguration?.initializedInterceptor?.invoke(initialization)
    }

    override fun toString(): String = "$simpleClassName(interactor=$interactor)"
}

private val serializationFormat = ProtoBuf
