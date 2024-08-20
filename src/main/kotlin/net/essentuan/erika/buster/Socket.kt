package net.essentuan.erika.buster

import com.busted_moments.buster.exceptions.PacketFormatException
import com.busted_moments.buster.protocol.Packet
import com.busted_moments.buster.protocol.Request
import com.busted_moments.buster.protocol.Response
import com.busted_moments.buster.protocol.clientbound.ClientboundLoginPacket
import com.google.gson.stream.JsonReader
import io.ktor.serialization.suitableCharset
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import net.essentuan.erika.buster.events.BusterEvent
import net.essentuan.erika.db.struct.fuy.BusterAccount
import net.essentuan.erika.framework.db.`object`.BsonModel
import net.essentuan.erika.framework.events.api.Listener
import net.essentuan.erika.framework.events.events
import net.essentuan.erika.framework.events.listen
import net.essentuan.erika.inline
import net.essentuan.erika.observers.player.worlds.WorldList
import net.essentuan.esl.Result
import net.essentuan.esl.fetch.NOTHING
import net.essentuan.esl.get
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Model.Companion.export
import net.essentuan.esl.other.lock
import net.essentuan.esl.scheduling.tasks
import net.essentuan.esl.time.duration.minutes
import net.essentuan.esl.time.extensions.timeSince
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.Date
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

class Socket(
    val username: String,
    val uuid: UUID,
    private val session: DefaultWebSocketServerSession
) : DefaultWebSocketServerSession by session {
    val id: String = hash(
        "$username|$uuid|${System.currentTimeMillis()}|${Random.nextLong()}|${hashCode()}|${BusterService.sockets.size}"
    )

    private val charset = call.request.headers.suitableCharset()

    lateinit var account: BusterAccount

    var isOpen: Boolean = false
        get() = ::account.isInitialized && field
        private set

    suspend fun start() {
        try {
            events.register()
            send(ClientboundLoginPacket(id))

            isOpen = true

            for (frame in incoming)
                if (frame is Frame.Text)
                    process(frame)
        } catch (_: ClosedReceiveChannelException) {
            //onClose
        } catch (ex: Throwable) {
            LOGGER.error("Uncaught exception in Socket(username=$username, uuid=$uuid, auth=$isOpen)!", ex)
            close()
        } finally {
            clean()
        }
    }

    private suspend fun process(frame: Frame.Text) {
        val result = read(frame)

        if (result is Result.Fail<*>) {
            if (result.cause is PacketFormatException) {
                close()

                return
            }

            throw result.cause
        }

        val packet = result.get()

        inline { BusterService.listeners[packet.javaClass]?.also {
            if (!it.privileged || ::account.isInitialized)
                it(this@Socket, packet)
        } }
    }

    private fun read(frame: Frame.Text) = Packet(
        Json(
            JsonReader(
                InputStreamReader(
                    ByteArrayInputStream(frame.data), charset
                )
            )
        )
    )

    suspend fun send(packet: Packet) {
        outgoing.send(
            Frame.Text(
                true,
                packet.export().asString().toByteArray(charset)
            )
        )
    }

    suspend fun Request<*>.fulfill(
        payload: Any = Unit,
        error: String = NOTHING
    ) {
        when {
            payload !== Unit -> {
                send(
                    Response(
                        ray,
                        when (payload) {
                            is Json.Model -> payload.export(BsonModel.EXTERNAL)
                            else -> payload
                        },
                        Response.OK
                    )
                )
            }

            error !== NOTHING -> {
                send(
                    Response(
                        ray,
                        error,
                        Response.ERROR
                    )
                )
            }

            else -> throw IllegalArgumentException()
        }
    }

    private fun clean() {
        isOpen = false
        tasks.close()
        events.unregister()

        if (::account.isInitialized) {
            BusterService.sockets.lock { remove(uuid) }
            LOGGER.info("$username ($uuid) has logged out.")

            BusterEvent.Disconnect(this).post()
        }
    }

    suspend fun close() {
        clean()

        this.close(reason = CloseReason(CloseReason.Codes.NORMAL, ""))
    }
}

private suspend fun Socket.wait(): Boolean {
    lateinit var listener: Listener<WorldList.UpdateEvent>

    return suspendCoroutine { cont ->
        val start = Date()

        listener = listen<WorldList.UpdateEvent> {
            if (start.timeSince() > 1.5.minutes)
                cont.resume(false)
            else if (uuid in WorldList)
                cont.resume(true)
        }
    }.also {
        listener.close()
    }
}