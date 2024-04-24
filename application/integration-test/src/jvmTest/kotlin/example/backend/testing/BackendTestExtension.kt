package example.backend.testing

import example.backend.database.Database
import example.backend.service.BackendService
import example.backend.service.BackendSession
import example.backend.service.backendModule
import io.kotest.core.extensions.SpecExtension
import io.kotest.core.spec.Spec
import io.ktor.server.testing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import library.telemetry.Telemetry
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

private val logger = Telemetry.logger("example.backend.testing.BackendTestExtensionKt")

class BackendTestExtension(
    private val name: String,
    private val applicationCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    private val elementCount: Int = 1,
    testConfiguration: (
        backendSession: BackendSession
    ) -> BackendSession.TestConfiguration = { backendSession ->
        BackendSession.TestConfiguration(backendSession)
    }
) : SpecExtension {

    private lateinit var service: BackendService

    val testApplication: TestApplication = TestApplication {
        environment { parentCoroutineContext += applicationCoroutineContext }
        application {
            backendModule({ service }, testConfiguration)
        }
    }

    override suspend fun intercept(spec: Spec, execute: suspend (Spec) -> Unit) {
        logger.info("Starting the backend for $spec.")

        val scope = CoroutineScope(applicationCoroutineContext)
        val database = Database(scope = scope, name = name, elementCount = elementCount)
        service = BackendService(scope = scope, name = name, database = database)

        testApplication.start()

        try {
            withContext(BackendServiceContextElement(service)) {
                execute(spec)
            }
        } finally {
            logger.info("Stopping the backend for $spec.")
            testApplication.stop()
            service.close()
        }
    }

    private class BackendServiceContextElement(val service: BackendService) :
        AbstractCoroutineContextElement(BackendServiceContextElement) {
        companion object Key : CoroutineContext.Key<BackendServiceContextElement>
    }

    companion object {
        /**
         * Returns the [BackendService] responsible for the current coroutine.
         */
        suspend fun service(): BackendService = coroutineContext[BackendServiceContextElement.Key]!!.service
    }
}
