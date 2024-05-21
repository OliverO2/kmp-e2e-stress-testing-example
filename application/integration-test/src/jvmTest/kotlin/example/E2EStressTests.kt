package example

import example.backend.service.BackendService
import example.backend.service.BackendSession
import example.backend.testing.BackendTestExtension
import example.frontend.GlobalSnapshotManagerTestExtension
import example.frontend.HeadlessFrontend
import example.frontend.service.FrontendService
import example.frontend.service.TestFrontendService
import example.frontend.views.FrontendView
import example.frontend.views.ViewNode
import example.transport.Event
import example.transport.TextLine
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.withClue
import io.kotest.core.annotation.DoNotParallelize
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.ktor.server.testing.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import library.core.GuardedBy
import library.core.SuspendCloseable
import library.core.adjustableTimeoutFactor
import library.core.adjustableTimeoutFactorContextElement
import library.core.createDebugTrace
import library.core.debugTrace
import library.core.removeDebugTrace
import library.core.useWith
import library.telemetry.Telemetry
import library.testing.CoroutineContextTestSpecExtension
import library.testing.Gate
import library.testing.TestVariant
import library.testing.eventuallyTimestamped
import library.testing.keepClosedUntil
import library.testing.testVariant
import library.testing.withAdjustableTestTimeout
import library.testing.withClues
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val logger = Telemetry.logger("example.HeadlessFrontendIntegrationTestsKt")

private fun BackendService.textLine(frontendIndex: Int) = database.slate.line(frontendIndex)

private suspend fun BackendService.updateTextComponent(frontendIndex: Int, value: String) {
    database.update(TextLine(id = frontendIndex, value = value, lastInteractor = this))
}

private class FrontendContext(val frontendIndex: Int) {
    var service by atomic<FrontendService?>(null)

    var updateCount by atomic(0)
    var updatesSentCount by atomic(0)
    var updatesReceivedCount by atomic(0)

    var isDone by atomic(false)
}

private val HeadlessFrontend<FrontendContext>.prefix get() = "fid=$id[i=${context.frontendIndex}]"

private fun HeadlessFrontend<FrontendContext>.textViewNode(frontendIndex: Int): ViewNode.Text {
    return (rootViewNode.child(frontendIndex) as? ViewNode.Text)!!
}

private fun HeadlessFrontend<FrontendContext>.updateTextView(frontendIndex: Int, value: String) {
    textViewNode(frontendIndex).update(value)
}

/**
 * Returns a launched frontend, which must be closed after use.
 */
private suspend fun launchedFrontend(
    scope: CoroutineScope,
    testApplication: TestApplication,
    frontendIndex: Int,
    testConfiguration: FrontendService.TestConfiguration? = null,
    context: FrontendContext = FrontendContext(frontendIndex),
    effectCoroutine: (suspend HeadlessFrontend<FrontendContext>.() -> Unit)? = null
) = HeadlessFrontend(
    mainView = {
        FrontendView(
            service = {
                TestFrontendService(id, testApplication, testConfiguration).also { context.service = it }
            },
            effectCoroutine = effectCoroutine?.let { innerEffectCoroutine ->
                { this@HeadlessFrontend.innerEffectCoroutine() }
            }
        )
    },
    context = context
).apply {
    val frontend = this
    launchIn(scope)

    withClues(
        { frontend },
        { frontend.debugTrace }
    ) {
        eventuallyTimestamped(5.seconds, "Waiting for the frontend's service startup") {
            compositionIsReady() shouldBe true
            frontend.context.service shouldNotBe null
        }

        withClues({ frontend.context.service!!.debugTrace }) {
            eventuallyTimestamped(5.seconds, "Waiting for the frontend's UI to update") {
                shouldNotThrow<NullPointerException> {
                    // Ensure that Compose has created at least the view node for this frontend.
                    textViewNode(frontendIndex)
                }
            }
        }
    }
}

private class FrontendBackendSessionPair(
    private val testApplication: TestApplication,
    private val cleanup: (suspend FrontendBackendSessionPair.() -> Unit)? = null
) : SuspendCloseable {
    @Volatile
    lateinit var frontend: HeadlessFrontend<FrontendContext>

    val frontendReceiveGate = Gate()
    val frontendSendGate = Gate()

    @GuardedBy("this")
    private val frontendReceivedPayloads = mutableListOf<Any>()

    @GuardedBy("this")
    private val frontendSentPayloads = mutableListOf<Any>()

    @Volatile
    lateinit var backendService: BackendService

    @Volatile
    lateinit var backendSession: BackendSession

    @Volatile
    var backendIncomingEventProcessedInterceptor: (suspend (event: Event) -> Unit)? = null

    @Volatile
    var backendPostSendInterceptor: (suspend (event: Event) -> Unit)? = null

    val backendModelUpdateSendGate = Gate()

    fun frontendReceivedPayloads() = synchronized(this) { frontendReceivedPayloads.toList() }

    suspend fun <Result> withSynchronizationDisabled(action: suspend () -> Result): Result =
        backendSession.testConfiguration!!.withSynchronizationDisabled(action)

    suspend fun runIn(
        scope: CoroutineScope,
        frontendIndex: Int,
        action: suspend FrontendBackendSessionPair.() -> Unit
    ) {
        frontend = launchedFrontend(
            scope,
            testApplication,
            frontendIndex,
            FrontendService.TestConfiguration {
                receiveInterceptor = { event ->
                    frontendReceiveGate.pass {
                        frontendService.debugTrace?.log("waiting at the receive gate")
                    }
                    synchronized(this) {
                        frontendReceivedPayloads.add(event.payload)
                    }
                }

                preSendInterceptor = { event ->
                    frontendSendGate.pass {
                        frontendService.debugTrace?.log("waiting at the send gate")
                    }
                    synchronized(this) {
                        frontendSentPayloads.add(event.payload)
                    }
                }
            }
        )

        backendService = BackendTestExtension.service()
        backendSession = backendService.session(frontend.context.service!!.interactor!!.id)
        backendSession.debugTrace?.log { "$backendSession <-> $frontend" }

        with(backendSession.testConfiguration!!) {
            incomingEventProcessedInterceptor = {
                backendIncomingEventProcessedInterceptor?.invoke(it)
            }

            postSendInterceptor = {
                backendPostSendInterceptor?.invoke(it)
            }

            modelUpdateSendInterceptor = { modelUpdate ->
                backendModelUpdateSendGate.pass {
                    backendSession.debugTrace?.log("waiting at the model update send gate ($modelUpdate)")
                }
            }
        }

        useWith {
            withClues(
                { backendSession.debugTrace },
                { frontend.debugTrace },
                { frontend.context.service!!.debugTrace }
            ) {
                withAdjustableTestTimeout(10.seconds, { "$this: Executing test action" }) {
                    withSynchronizationDisabled {
                        action()
                    }
                }
            }
        }
    }

    override suspend fun close() {
        if (cleanup != null) {
            withContext(NonCancellable) {
                cleanup?.invoke(this@FrontendBackendSessionPair)
            }
        }

        frontend.close()
        backendSession.removeDebugTrace() // was created via BackendExtension parameter `testConfiguration`
    }
}

// Run tests sensitive to timing issues independently
@DoNotParallelize
class E2EUpdateRejectionTests : FunSpec({
    val frontendCountVariants =
        fixedFrontendCount?.let {
            listOf(it)
        } ?: testVariant.dependent(listOf(1, 4, 50), TestVariant.EXTENDED to listOf(1, 4, 100))
    val commonContext =
        adjustableTimeoutFactorContextElement(testVariant.dependent(1.0, TestVariant.EXTENDED to 30.0))
    extensions(GlobalSnapshotManagerTestExtension(), CoroutineContextTestSpecExtension(commonContext))

    val testApplication =
        extension(
            BackendTestExtension(
                "hfi-ct",
                applicationCoroutineContext = commonContext,
                elementCount = frontendCountVariants.last(),
                testConfiguration = { backendSession ->
                    backendSession.createDebugTrace() // will be removed by FrontendSessionPair.runIn
                    BackendSession.TestConfiguration(backendSession)
                }
            )
        ).testApplication

    fun cleanup(frontendIndex: Int): (suspend FrontendBackendSessionPair.() -> Unit) = {
        backendService.updateTextComponent(frontendIndex, "")
    }

    for (frontendCount in frontendCountVariants) {
        test("$frontendCount frontends") {
            for (frontendIndex in 0..<frontendCount) {
                launch(Dispatchers.IO) {
                    FrontendBackendSessionPair(testApplication, cleanup(frontendIndex)).runIn(this, frontendIndex) {
                        val frontendPrefix = frontend.prefix

                        backendModelUpdateSendGate.keepClosedUntil(
                            interceptor = this::backendIncomingEventProcessedInterceptor,
                            predicate = { event ->
                                // Wait for the backend to process this frontend's change, ignoring changes from
                                // other frontends.
                                val changeText = (event.payload as? TextLine)?.value
                                changeText?.startsWith(frontendPrefix) ?: false
                            }
                        ) {
                            frontendReceiveGate.keepClosed {
                                backendService.updateTextComponent(frontendIndex, "$frontendPrefix-b")
                                frontend.updateTextView(frontendIndex, "$frontendPrefix-reject")
                            }
                        }

                        eventuallyTimestamped(5.seconds, "Waiting for the frontend to receive a rejection") {
                            val correspondingTextLines = frontendReceivedPayloads().mapNotNull {
                                if ((it as? TextLine)?.value?.startsWith(frontendPrefix) == true) it else null
                            }
                            correspondingTextLines.shouldNotBeEmpty()
                            correspondingTextLines.last().value shouldEndWith "-done"
                        }

                        withClue("Backend database should reflect the rejection") {
                            backendService.textLine(frontendIndex).value shouldBe "$frontendPrefix-done"
                        }

                        eventuallyTimestamped(5.seconds, "Waiting for the frontend to revert its change") {
                            frontend.textViewNode(frontendIndex).content shouldBe "$frontendPrefix-done"
                        }
                    }
                }
            }
        }
    }
})

// Run tests sensitive to timing issues independently
@DoNotParallelize
class E2EMassUpdateTests : FunSpec({
    val frontendCount = fixedFrontendCount ?: testVariant.dependent(50, TestVariant.EXTENDED to 100)
    val maximumUpdateDuration = testVariant.dependent(10.seconds, TestVariant.EXTENDED to 40.seconds)
    val commonContext = adjustableTimeoutFactorContextElement(testVariant.dependent(1.0, TestVariant.EXTENDED to 4.0))

    extensions(GlobalSnapshotManagerTestExtension(), CoroutineContextTestSpecExtension(commonContext))
    val testApplication = extension(
        BackendTestExtension(
            "hfi-st",
            applicationCoroutineContext = commonContext,
            elementCount = frontendCount
        )
    ).testApplication

    class FrontendServiceTestConfiguration(val context: FrontendContext) : FrontendService.TestConfiguration() {
        init {
            initialize = {
                postSendInterceptor = { event ->
                    if (event.payload is TextLine) context.updatesSentCount++
                }
                receiveInterceptor = { event ->
                    if (event.payload is TextLine) context.updatesReceivedCount++
                }
            }
        }
    }

    test("mass updates") {
        val testScope = this

        val startUpdatesGate = Gate(isOpen = false)

        val frontends = mutableListOf<HeadlessFrontend<FrontendContext>>()

        logger.info("Starting $frontendCount frontend(s)...")

        try {
            val targetUpdateCount = 20

            // Launch all frontends, letting them wait at the start gate.
            repeat(frontendCount) { frontendIndex ->
                val context = FrontendContext(frontendIndex)
                frontends.add(
                    launchedFrontend(
                        testScope,
                        testApplication,
                        frontendIndex = frontendIndex,
                        FrontendServiceTestConfiguration(context),
                        context,
                        effectCoroutine = {
                            // Wait until the start gate opens.
                            startUpdatesGate.pass()

                            // Perform the action of interest: Repeatedly update "this frontend's" text line.
                            for (updateIndex in 1..targetUpdateCount) {
                                textViewNode(frontendIndex).update("$prefix-${++context.updateCount}")
                                delay(150.milliseconds)
                            }

                            // Wait for Compose to settle.
                            delay(3.seconds * adjustableTimeoutFactor)
                            context.isDone = true
                        }
                    )
                )
            }

            // Open the start gate, making the frontends concurrently perform updates.
            startUpdatesGate.open()

            // Wait until they are done and settled.
            logger.info("Performing updates...")
            eventuallyTimestamped(maximumUpdateDuration, "waiting for frontends to complete updates") {
                frontends.shouldForAll { it.context.isDone shouldBe true }
            }

            // Check settled frontend state.
            withClue("All views of all frontends should display the settled final state") {
                frontends.shouldForAll { frontend ->
                    with(frontend) {
                        context.updateCount shouldBe targetUpdateCount
                        context.updatesSentCount shouldBe targetUpdateCount
                        // Each frontend should display the settled state for all views, not just for the
                        // frontend's "own" text line view.
                        frontends.forEachIndexed { viewNodeFrontendIndex, viewNodeFrontend ->
                            (
                                textViewNode(viewNodeFrontendIndex).content shouldBe
                                    "${viewNodeFrontend.prefix}-$targetUpdateCount"
                                )
                        }
                    }
                }
            }

            // Stop all frontends.
            logger.info("Stopping $frontendCount frontend(s)...")
            frontends.onEach { it.stop() }.map { it.job }.joinAll()

            // Check database state.
            withClue("The database should contain the settled final state") {
                BackendTestExtension.service().database.slate.lines().shouldForAll {
                    it.value shouldEndWith "[i=${it.id}]-$targetUpdateCount"
                }
            }

            // Evaluate frontend contexts.
            val frontendContexts = frontends.map { it.context }

            fun Iterable<Int>.statistics(): String {
                val sum: Int = sum()
                val max: Int = max()
                val min: Int = min()
                val average: Double = average()
                val seconds = maximumUpdateDuration.inWholeSeconds

                return "sum=$sum (${sum / seconds}/s), max=$max (${max / seconds}/s)," +
                    " min=$min (${min / seconds}/s), avg=$average (${average / seconds}/s)"
            }

            logger.info("Results with $frontendCount frontends:")
            logger.info("  updates:          " + frontendContexts.map { it.updateCount }.statistics())
            logger.info("  updates sent:     " + frontendContexts.map { it.updatesSentCount }.statistics())
            logger.info("  updates received: " + frontendContexts.map { it.updatesReceivedCount }.statistics())

            frontendContexts.sumOf { it.updatesSentCount } shouldBeGreaterThan 0
            frontendContexts.sumOf { it.updatesReceivedCount } shouldBeGreaterThan 0
        } finally {
            frontends.forEach {
                it.close()
            }
        }
    }
})

private var fixedFrontendCount = System.getProperty("application.test.fixedFrontendCount")?.toIntOrNull()
