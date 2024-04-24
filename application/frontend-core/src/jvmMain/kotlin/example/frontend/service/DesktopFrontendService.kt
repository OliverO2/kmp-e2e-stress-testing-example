package example.frontend.service

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope

/**
 * The desktop application's service frontend.
 */
class DesktopFrontendService : FrontendService() {

    private val httpClient = HttpClient(CIO) {
        install(WebSockets)
    }

    override suspend fun runWebSocketSession(
        block: suspend CoroutineScope.(webSocketSession: WebSocketSession) -> Unit
    ) {
        httpClient.ws(
            method = HttpMethod.Get,
            host = "localhost",
            port = 8080,
            path = "/websocket"
        ) {
            block(this)
        }
    }
}
