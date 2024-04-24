package example.frontend

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import example.frontend.views.ViewNode
import io.kotest.assertions.withClue
import io.kotest.core.extensions.SpecExtension
import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.core.test.TestType
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.getOrElse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import library.core.DebugTraceTarget
import library.core.createDebugTrace
import library.core.debugTrace
import library.core.removeDebugTrace
import library.core.simpleClassName
import library.core.withLogging
import library.telemetry.Telemetry
import library.telemetry.withDebugLogging
import library.telemetry.withInfoLogging
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

private val logger = Telemetry.logger("example.frontend.HeadlessFrontendKt")

/**
 * A frontend with a headless Compose UI.
 */
class HeadlessFrontend<Context>(
    private val mainView: @Composable HeadlessFrontend<Context>.() -> Unit,
    val context: Context,
    private val resultsCheck: (HeadlessFrontend<Context>.() -> Unit)? = null,
    val fps: Int = 60
) : DebugTraceTarget, Closeable {
    val id: Int = lastId.incrementAndGet()
    lateinit var job: Job
        private set
    val rootViewNode = ViewNode.Root()

    @Volatile
    var compositionCoroutineContext: CoroutineContext? = null

    fun launchIn(testScope: CoroutineScope) {
        createDebugTrace()
        debugTrace?.log(testScope.coroutineContext) { "starting" }

        val frontendCoroutineName = "$this-Main"

        @OptIn(DelicateCoroutinesApi::class)
        val mainThreadDispatcher = newSingleThreadContext(frontendCoroutineName)

        job = testScope.launch(mainThreadDispatcher + headlessMonotonicFrameClock(fps)) {
            withGlobalSnapshotManager {
                val recomposer = Recomposer(coroutineContext)
                val composition = Composition(ViewNode.Applier(rootViewNode), recomposer)
                compositionCoroutineContext = coroutineContext

                try {
                    debugTrace.withLogging {
                        logger.withDebugLogging(frontendCoroutineName) {
                            composition.setContent {
                                this@HeadlessFrontend.mainView()
                            }

                            recomposer.runRecomposeAndApplyChanges()
                        }
                    }
                } finally {
                    resultsCheck?.invoke(this@HeadlessFrontend)
                    composition.dispose()
                    mainThreadDispatcher.close() // shut down the frontend-exclusive thread
                }
            }
        }
    }

    fun compositionIsReady() = compositionCoroutineContext != null

    fun stop() {
        job.cancel(CancellationException("$this stopping"))
    }

    override fun close() {
        stop()
        removeDebugTrace()
    }

    override fun toString(): String = "$simpleClassName(id=$id)"

    companion object {
        private val lastId = atomic(0)
        private val headlessMonotonicFrameClocks = ConcurrentHashMap<Int, HeadlessMonotonicFrameClock>()

        private fun headlessMonotonicFrameClock(fps: Int) =
            headlessMonotonicFrameClocks.computeIfAbsent(fps) { HeadlessMonotonicFrameClock(fps) }
    }
}

class GlobalSnapshotManagerTestExtension(private val enableDebugTrace: Boolean = false) :
    SpecExtension, TestCaseExtension {

    override suspend fun intercept(spec: Spec, execute: suspend (Spec) -> Unit) {
        withGlobalSnapshotManager {
            execute(spec)
        }
    }

    override suspend fun intercept(testCase: TestCase, execute: suspend (TestCase) -> TestResult): TestResult =
        if (testCase.type == TestType.Container) {
            execute(testCase)
        } else {
            if (enableDebugTrace) {
                withClue({ GlobalSnapshotManager.debugTrace }) {
                    // Each test starts a new debug trace for the global snapshot manager. This ensures that the
                    // trace does not become too large. The downside is that we might miss debug traces if an exception
                    // is processed on another thread, and such exception needs the snapshot manager's trace.
                    GlobalSnapshotManager.createDebugTrace()
                    execute(testCase)
                }
            } else {
                execute(testCase)
            }
        }
}

private suspend fun <Result> withGlobalSnapshotManager(block: suspend () -> Result): Result {
    try {
        GlobalSnapshotManager.attachClient()
        return block()
    } finally {
        GlobalSnapshotManager.detachClient()
    }
}

private object GlobalSnapshotManager : DebugTraceTarget {
    private var clientCount = 0
    private var job: Job? = null
    private var dispatcher: ExecutorCoroutineDispatcher? = null

    @Synchronized
    fun attachClient() {
        if (++clientCount == 1) {
            val snapshotWriteObservation = Channel<Unit>(Channel.CONFLATED)

            @OptIn(DelicateCoroutinesApi::class)
            dispatcher = newSingleThreadContext("GlobalSnapshotManager")

            job = CoroutineScope(dispatcher!!).launch(CoroutineName("GlobalSnapshotManager")) {
                debugTrace.withLogging {
                    logger.withInfoLogging("GlobalSnapshotManager") {
                        snapshotWriteObservation.consumeEach {
                            Snapshot.sendApplyNotifications()
                        }
                    }
                }
            }
            Snapshot.registerGlobalWriteObserver {
                debugTrace?.log(job) { "write observed: $it" }
                snapshotWriteObservation.trySend(Unit).getOrElse {
                    debugTrace?.log(job) { "sendApplyNotifications() stalled: $it" }
                }
            }
        }
    }

    @Synchronized
    fun detachClient() {
        if (--clientCount == 0) {
            job!!.cancel(CancellationException("last client detached"))
            job = null
            dispatcher!!.close()
            dispatcher = null
            removeDebugTrace() // if any debug trace was created, remove it here
        }
    }
}

private class HeadlessMonotonicFrameClock(fps: Int) : MonotonicFrameClock {
    private val frameInterval = 1000L / fps

    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
        delay(frameInterval)
        return onFrame(System.nanoTime())
    }
}
