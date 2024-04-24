package example.backend.service

import example.backend.database.Database
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async

fun runApplicationServer() {
    embeddedServer(
        Netty,
        environment = applicationEngineEnvironment {
            connector {
                host = "0.0.0.0"
                port = 8080
            }
            modules.add(Application::backendModule)
        }
    ).start(wait = true)
}

fun Application.backendModule(
    service: suspend (applicationScope: CoroutineScope) -> BackendService = ::demoBackendService,
    testConfiguration: ((BackendSession) -> BackendSession.TestConfiguration)? = null
) {
    val deferredService = async(start = CoroutineStart.LAZY) {
        service(this@backendModule)
    }

    install(WebSockets)
    install(ContentNegotiation)

    routing {
        webSocket("/websocket") {
            WebSocketBackendSession(deferredService.await(), this, testConfiguration).communicate()
        }

        staticResources("/", basePackage = null)
    }
}

@Suppress("RedundantSuspendModifier")
private suspend fun demoBackendService(applicationScope: CoroutineScope): BackendService {
    val database = Database(scope = applicationScope, name = "main/demo", elementCount = 10)

    return BackendService(scope = applicationScope, name = "main/demo", database = database)
}
