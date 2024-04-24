package example.backend.service

import example.transport.Event
import io.ktor.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import library.core.toHexString
import library.telemetry.Telemetry
import java.nio.ByteBuffer

private val logger = Telemetry.logger("example.backend.service.WebSocketBackendSessionKt")

/**
 * A WebSocket-based backend session.
 */
class WebSocketBackendSession(
    service: BackendService,
    webSocketSession: WebSocketSession,
    testConfiguration: ((BackendSession) -> TestConfiguration)?
) : BackendSession(service), WebSocketSession by webSocketSession {
    override val testConfiguration: TestConfiguration? = testConfiguration?.invoke(this)

    private val serializationFormat = ProtoBuf

    override fun incomingEvents(): Flow<Event> = flow {
        for (frame in incoming) {
            if (frame is Frame.Binary) {
                val event: Event
                try {
                    event = serializationFormat.decodeFromByteArray(frame.data)
                } catch (exception: Throwable) {
                    logger.error {
                        "Could not decode Event from binary frame: $exception\n" +
                            "\tframe data: ${frame.data.toHexString()}"
                    }
                    continue
                }

                emit(event)
            } else {
                logger.error { "Received unexpected frame type '${frame.frameType}'" }
            }
        }
    }

    override suspend fun sendEvent(event: Event) {
        val buffer = ByteBuffer.wrap(serializationFormat.encodeToByteArray(event))
        outgoing.send(Frame.Binary(fin = true, buffer))
    }
}
