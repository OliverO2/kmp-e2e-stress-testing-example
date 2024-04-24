package example.backend.service

import example.backend.database.Database
import example.transport.Interactor
import kotlinx.coroutines.CoroutineScope
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class BackendService(
    scope: CoroutineScope,
    name: String,
    val database: Database
) : Interactor(Type.BACKEND, ID(name), "backend service"), CoroutineScope by scope, Closeable {

    private var nextSessionID = AtomicInteger(0)

    /** [BackendSession]s by [Interactor.ID], only maintained for sessions with a test configuration. */
    private val sessions = ConcurrentHashMap<ID, BackendSession>()

    override fun close() {
        database.close()
    }

    @PackagePrivate
    internal fun nextSessionID() = nextSessionID.incrementAndGet()

    /**
     * Registers the session for testing purposes.
     */
    @PackagePrivate
    internal fun registerSession(backendSession: BackendSession) {
        sessions[backendSession.interactor.id] = backendSession
    }

    /**
     * Unregisters the session for testing purposes.
     */
    @PackagePrivate
    internal fun unregisterSession(backendSession: BackendSession) {
        sessions.remove(backendSession.interactor.id)
    }

    /**
     * Returns the [BackendSession] by [Interactor.ID], only available for sessions with a test configuration.
     */
    fun session(id: ID): BackendSession = sessions[id]!!
}
