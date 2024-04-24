@file:OptIn(PackagePrivate::class)

package example.backend.service

import example.backend.database.Subscription
import example.transport.Event
import example.transport.EventPayload
import example.transport.EventSource
import example.transport.FrontendInitialization
import example.transport.Interactor
import example.transport.SynchronizationRequest
import example.transport.SynchronizationResponse
import example.transport.TextLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import library.core.DebugTraceTarget
import library.core.debugTrace
import library.core.simpleClassName
import library.core.withAdjustableTimeout
import library.core.withLockIfNonNull
import library.telemetry.Telemetry
import library.telemetry.withDebugLogging
import kotlin.time.Duration.Companion.seconds

private val logger = Telemetry.logger("example.backend.service.BackendSessionKt")

/**
 * A backend session which is connected to a client and interacts with the backend service.
 */
abstract class BackendSession(private val service: BackendService) : EventSource(), CoroutineScope, DebugTraceTarget {

    /**
     * The client interactor, a notion shared between the [BackendSession] and the corresponding frontend service.
     */
    @OptIn(PackagePrivate::class) // TODO: the file-wide annotation should suffice, but the compiler sometimes complains
    internal val interactor = service.nextSessionID().let {
        Interactor(Interactor.Type.CLIENT, Interactor.ID("$it"), "$service session #$it")
    }

    /**
     * Mutex for atomic event creation and sending, required to guarantee strictly monotonically increasing event IDs.
     */
    private val eventCreationAndSendingMutex = Mutex()

    private val synchronizationResponses = Channel<SynchronizationResponse>(Channel.RENDEZVOUS)

    /**
     * Returns a flow of events incoming from the client.
     */
    protected abstract fun incomingEvents(): Flow<Event>

    /**
     * Sends an event to the client.
     */
    protected abstract suspend fun sendEvent(event: Event)

    abstract val testConfiguration: TestConfiguration?

    /**
     * An extensible test configuration.
     */
    open class TestConfiguration(val backendSession: BackendSession) {
        var enableSubscriptionDebugTrace: Boolean = false
        var subscription: Subscription? = null

        var receiveInterceptor: (suspend (event: Event) -> Unit)? = null
        var incomingEventProcessedInterceptor: (suspend (event: Event) -> Unit)? = null

        var preSendInterceptor: (suspend (payload: Any) -> Unit)? = null
        var postSendInterceptor: (suspend (event: Event) -> Unit)? = null

        var modelUpdateSendInterceptor: (suspend (textLine: TextLine) -> Unit)? = null

        @Volatile
        internal var synchronizationEnabled = true

        /**
         * Guard protecting a code block against concurrent changes of [synchronizationEnabled].
         * This avoids a race between parallel invocations of [withSynchronizationDisabled] and [synchronizeClient],
         * before [synchronizationEnabled] gets set to `false`.
         */
        internal val synchronizationEnabledChangeGuard = Mutex()

        suspend fun <Result> withSynchronizationDisabled(action: suspend () -> Result): Result {
            synchronizationEnabledChangeGuard.withLock {
                require(synchronizationEnabled) { "Cannot disable synchronization which is already disabled" }

                // Ensure that the client is synchronized before disabling synchronization.
                // Reason: Disabled synchronization is generally used with blocking send/receive gates in [action].
                // Synchronizing at this point ensures that a gate will not block the completion of requests which
                // were present before [action] is invoked.
                backendSession.synchronizeClientUnconditionally()
                synchronizationEnabled = false
            }

            try {
                return action()
            } finally {
                synchronizationEnabled = true
            }
        }
    }

    /**
     * Makes a session communicate until either the sending or receiving channel is closed.
     */
    suspend fun communicate() {
        testConfiguration?.apply {
            service.registerSession(backendSession)
        }

        try {
            Subscription(
                service.database,
                interactor,
                enableDebugTrace = testConfiguration?.enableSubscriptionDebugTrace == true
            ).use { subscription ->
                testConfiguration?.apply {
                    this.subscription = subscription
                }

                sendEventPayload(FrontendInitialization(interactor, service.database.slate))

                coroutineScope {
                    debugTrace?.log { "starting senders and receiver" }
                    val communicationJobs = listOf(
                        launchedReceiver(),
                        launchedModelUpdatesSender(subscription)
                    )

                    // If a job finishes, cancel the others.
                    select {
                        for (candidateJob in communicationJobs) {
                            candidateJob.onJoin {
                                for (job in communicationJobs) {
                                    job.cancel(CancellationException("$candidateJob finished"))
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            testConfiguration?.apply {
                service.unregisterSession(backendSession)
            }
        }
    }

    /**
     * Returns a newly launched job, sending model updates to the client until canceled.
     *
     * Concurrently with this sender's updates, third parties may send other events at any time.
     */
    private fun launchedModelUpdatesSender(subscription: Subscription) =
        launch(CoroutineName("$interactor-ModelUpdatesSender")) {
            debugTrace?.log { "starting $this" }

            // Ensure an initial synchronization before starting to send out updates. This is mainly useful for
            // testing as it avoids missing initial updates.
            synchronizeClient()

            logger.withDebugLogging {
                subscription.changedTextLineBatches().collect { changedLines ->
                    var synchronizationRequired = false

                    // 1. Send all model updates of a batch.
                    for (textLine in changedLines) {
                        testConfiguration?.modelUpdateSendInterceptor?.invoke(textLine)
                        if (textLine.lastInteractor != interactor) {
                            // Never feed back updates to the originating client. Rationale:
                            // There may be multiple consecutive updates of the same model (e.g. text
                            // editing). An update may have arrived at the backend, while another one
                            // may be still in flight. The backend must not feed back the earlier update
                            // as doing so would overwrite the in-flight update.

                            sendEventPayload(textLine)
                            synchronizationRequired = true
                        }
                    }

                    if (synchronizationRequired) {
                        // 2. Synchronize before continuing with the next batch. This ensures that a slow client
                        // will not be flooded with updates beyond its processing capacity.
                        synchronizeClient()
                    }
                }
            }
        }

    /**
     * Returns a newly launched receiver job, which will receive, decode and process client events.
     */
    private fun launchedReceiver() = launch(CoroutineName("$interactor-Receiver")) {
        logger.withDebugLogging {
            debugTrace?.log { "starting $this" }
            incomingEvents().collect { event ->
                debugTrace?.log { "received $event" }
                logger.debug { "${this@BackendSession} received $event" }
                testConfiguration?.receiveInterceptor?.invoke(event)
                processIncomingEvent(event)
                debugTrace?.log { "processed incoming $event" }
                logger.debug { "${this@BackendSession} processed incoming $event" }
                testConfiguration?.incomingEventProcessedInterceptor?.invoke(event)
            }
        }
    }

    /**
     * Processes an incoming event.
     */
    private suspend fun processIncomingEvent(event: Event) {
        when (val payload = event.payload) {
            is TextLine -> {
                service.database.update(payload)
            }

            is SynchronizationResponse -> {
                synchronizationResponses.send(payload)
            }

            else -> {
                logger.error { "Received unexpected event $event" }
            }
        }
    }

    /**
     * Sends an event payload to the client.
     *
     * Most events are sent sequentially by [launchedModelUpdatesSender]. Exceptional events
     * may be sent asynchronously by [launchedReceiver] at any time. Therefore, this method must be thread-safe.
     */
    private suspend fun sendEventPayload(payload: EventPayload): Event {
        testConfiguration?.preSendInterceptor?.invoke(payload)
        if (eventCreationAndSendingMutex.isLocked) debugTrace?.log { "waiting on mutex to send $payload" }
        eventCreationAndSendingMutex.withLock {
            val event = event(payload)
            sendEvent(event)
            debugTrace?.log { "sent $event" }
            logger.debug { "${this@BackendSession} sent $event" }
            testConfiguration?.postSendInterceptor?.invoke(event)
            return event
        }
    }

    /**
     * Synchronizes the client, making sure it has processed all server-side events so far.
     *
     * This function protects itself against races, as it and its unconditional variant can potentially be called
     * concurrently
     * - by the [BackendSession]'s sender during initialization,
     * - by tests aiming to bring the client into a known state.
     */
    private suspend fun synchronizeClient() = (testConfiguration?.synchronizationEnabledChangeGuard).withLockIfNonNull {
        if (testConfiguration?.synchronizationEnabled == false) return
        synchronizeClientUnconditionally()
    }

    private suspend fun synchronizeClientUnconditionally() {
        debugTrace?.log("synchronizing")
        logger.debug { "${this@BackendSession} synchronizing" }

        val requestEvent = withAdjustableTimeout(3.seconds, { "Sending a synchronization request to the client" }) {
            sendEventPayload(SynchronizationRequest)
        }

        val response = withAdjustableTimeout(3.seconds, { "Waiting for a synchronization response from the client" }) {
            synchronizationResponses.receive()
        }

        debugTrace?.log("synchronized")
        logger.debug { "${this@BackendSession} synchronized" }

        if (response.requestSequenceID != requestEvent.sequenceID) {
            logger.error {
                "Requested synchronization with sequence ID #${requestEvent.sequenceID} but" +
                    " received acknowledgment for sequence ID #${response.requestSequenceID}\n" +
                    "$debugTrace"
            }
        }
    }

    override fun toString(): String = "$simpleClassName($interactor)"
}
