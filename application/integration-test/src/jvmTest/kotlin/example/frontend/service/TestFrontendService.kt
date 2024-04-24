package example.frontend.service

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import library.core.SuspendCloseable
import library.core.createDebugTrace
import library.core.debugTrace
import library.core.removeDebugTrace
import library.core.simpleClassName
import library.telemetry.LogEvent
import library.telemetry.LogLevel
import library.telemetry.LoggingConfiguration
import library.telemetry.asContextElement
import library.telemetry.emitViaSlf4J

class TestFrontendService(
    private val frontendId: Int,
    testApplication: TestApplication,
    testConfiguration: TestConfiguration? = null
) : FrontendService(testConfiguration), SuspendCloseable {

    init {
        createDebugTrace()
    }

    private val httpClient: HttpClient = testApplication.createClient {
        install(WebSockets)
    }

    override suspend fun runWebSocketSession(
        block: suspend CoroutineScope.(webSocketSession: WebSocketSession) -> Unit
    ) {
        httpClient.webSocket(path = "/websocket") {
            val webSocketSession = this
            withContext(
                LoggingConfiguration("${this@TestFrontendService}", ::emitLogEvent).asContextElement() +
                    CoroutineName("${this@TestFrontendService}")
            ) {
                block(webSocketSession)
            }
        }
    }

    private fun emitLogEvent(logEvent: LogEvent) {
        if (logEvent.level > LogLevel.DEBUG) {
            emitViaSlf4J(logEvent)
        } else {
            testConfiguration?.let { testConfiguration ->
                debugTrace?.log(testConfiguration.frontendCoroutineScope.coroutineContext) { "$logEvent" }
            } ?: emitViaSlf4J(logEvent)
        }
    }

    override suspend fun close() {
        stop()
        removeDebugTrace()
    }

    override fun toString(): String = "$simpleClassName(interactor=$interactor, frontendId=$frontendId)"
}
