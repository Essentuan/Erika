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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import net.essentuan.erika.buster.events.BusterEvent
import net.essentuan.erika.db.struct.fuy.BusterAccount
import net.essentuan.erika.framework.db.`object`.BsonModel
import net.essentuan.erika.framework.events.api.Listener
import net.essentuan.erika.framework.events.events
import net.essentuan.erika.framework.events.listen
import net.essentuan.erika.inline
import net.essentuan.erika.observers.player.worlds.WorldList
import net.essentuan.esl.Result
import net.essentuan.esl.coroutines.delay
import net.essentuan.esl.fetch.NOTHING
import net.essentuan.esl.get
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Model.Companion.export
import net.essentuan.esl.other.lock
import net.essentuan.esl.scheduling.tasks
import net.essentuan.esl.time.duration.minutes
import net.essentuan.esl.time.duration.ms
import net.essentuan.esl.time.extensions.timeSince
import java.io.ByteArrayInputStream
import java.io.IOException
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

    val charset = call.request.headers.suitableCharset()

    lateinit var account: BusterAccount

    var isOpen: Boolean = false
        get() = ::account.isInitialized && field
        private set

    suspend fun start() {
        try {
            events.register()
            send(ClientboundLoginPacket(id))

            isOpen = true

            incoming.consumeEach {
                if (it is Frame.Text)
                    inline {
                        process(it)
                    }
            }
        } catch (_: ClosedReceiveChannelException) {
            //onClose
        } catch (_: IOException) {
            //Timeout
        } catch (ex: Throwable) {
            LOGGER.error("Uncaught exception in Socket(username=$username, uuid=$uuid, auth=$isOpen)!", ex)
        } finally {
            clean()
        }
    }

    private suspend fun process(frame: Frame.Text) {
        val result = read(frame)

        if (result is Result.Fail<*>) {
            if (result.cause is PacketFormatException) {
                close()

                LOGGER.error("Error in socket", result.cause)

                return
            }

            throw result.cause
        }

        val packet = result.get()

        BusterService.listeners[packet.javaClass]?.also {
            if (!it.privileged || ::account.isInitialized)
                it(this@Socket, packet)
        }
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

    fun send(packet: Packet) =
        send(packet.export().asString())

    fun send(payload: String) =
        send(payload.toByteArray(charset))

    @OptIn(DelicateCoroutinesApi::class)
    fun send(payload: ByteArray) {
        BusterService.launch {
            outgoing.send(Frame.Text(true, payload))
        }
    }

    fun Request<*>.fulfill(
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