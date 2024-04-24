package example.frontend.service

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope

/**
 * The browser application's service frontend.
 */
class BrowserFrontendService : FrontendService() {

    private val httpClient = HttpClient(Js) {
        install(WebSockets)
    }

    override suspend fun runWebSocketSession(
        block: suspend CoroutineScope.(webSocketSession: WebSocketSession) -> Unit
    ) {
        httpClient.ws(
            method = HttpMethod.Get,
            host = window.location.hostname,
            port = window.location.port.toInt(),
            path = "/websocket"
        ) {
            block(this)
        }
    }
}
