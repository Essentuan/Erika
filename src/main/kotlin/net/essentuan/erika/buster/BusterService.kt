package net.essentuan.erika.buster

import com.busted_moments.buster.Buster
import com.busted_moments.buster.api.Guild
import com.busted_moments.buster.api.PlayerType
import com.busted_moments.buster.protocol.Packet
import com.busted_moments.buster.protocol.clientbound.ClientboundGuildListPacket
import com.busted_moments.buster.protocol.clientbound.ClientboundMapPacket
import com.busted_moments.buster.protocol.clientbound.ClientboundWorldListPacket
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.close
import io.ktor.websocket.extensionOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.isActive
import net.essentuan.erika.db.struct.guild.search.invoke
import net.essentuan.erika.fetch.wynncraft.guild.BasicGuild
import net.essentuan.erika.fetch.wynncraft.guild.guild
import net.essentuan.erika.framework.Service
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.db.`object`.types.Singleton
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.inline
import net.essentuan.erika.ktor.Route
import net.essentuan.erika.observers.guilds.list.Guilds
import net.essentuan.erika.observers.guilds.list.isDeleted
import net.essentuan.erika.observers.player.worlds.WorldList
import net.essentuan.erika.observers.territories.TerritoryList
import net.essentuan.erika.observers.territories.TerritoryList.external
import net.essentuan.erika.observers.territories.events.MapUpdateEvent
import net.essentuan.erika.scope
import net.essentuan.esl.Rating
import net.essentuan.esl.collections.maps.expireAfter
import net.essentuan.esl.collections.synchronized
import net.essentuan.esl.fetch.fetch
import net.essentuan.esl.model.Model.Companion.export
import net.essentuan.esl.orNull
import net.essentuan.esl.other.lock
import net.essentuan.esl.reflections.Reflections
import net.essentuan.esl.rx.filterNotNull
import net.essentuan.esl.rx.iterate
import net.essentuan.esl.rx.map
import net.essentuan.esl.scheduling.annotations.Every
import net.essentuan.esl.scheduling.annotations.Lifetime
import net.essentuan.esl.time.duration.minutes
import net.essentuan.esl.time.extensions.timeUntil
import java.math.BigInteger
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.UUID

val LOGGER by Logging

object BusterService : Service(), Route, Iterable<Socket>, CoroutineScope by scope(10) {
    @Subscribe
    private fun WorldList.UpdateEvent.on() {
        broadcast(ClientboundWorldListPacket(WorldList.lock { external() }))
    }

    @Subscribe
    private fun MapUpdateEvent.on() {
        broadcast(ClientboundMapPacket(TerritoryList.external()))
    }

    private val guilds =
        mutableMapOf<UUID, BasicGuild>().synchronized()
            .expireAfter { (_, it) -> it.metadata.expires!!.timeUntil().min(3.minutes) }

    @Every(seconds = 10.0)
    @Lifetime(seconds = 90.0)
    private suspend fun updateGuilds() {
        broadcast(ClientboundGuildListPacket(Guilds.lock { external() }))
        guilds.cleanse()

        Guild(priority = Rating.LOW, expiry = 5.minutes) {
            +this@BusterService.asSequence()
                .map { it to it.account.member.guild?.uuid }
                .filterNot { (_, it) -> it == null }
                .groupBy({ (_, it) -> it!! }) { (it, _) -> it }
                .asSequence()
                .filter { (guild, members) -> guild !in guilds && members.size >= Constants.guildCutoff }
                .map { (it, _) -> it }
                .map { Guilds[it] }
                .filterNotNull()
                .filterNot { it.isDeleted }
        }.map { (_, result) -> result.orNull() }.filterNotNull() iterate {
            guilds[it.uuid] = fetch { guild(uuid = it.uuid, priority = Rating.LOWEST) } ?: return@iterate
        }
    }

    val sockets = mutableMapOf<UUID, Socket>()
    val listeners = Reflections.functions
        .annotatedWith(Listener::class)
        .map { MethodPacketListener(it) }
        .filterNotNull()
        .distinctBy { it.packet }
        .associateByTo(mutableMapOf()) { it.packet }

    fun register(listener: PacketListener) {
        listeners[listener.packet] = listener
    }

    override fun Application.start() {
        install(WebSockets) {
            extensions {
                install(Buster)
            }
        }

        routing {
            webSocket("/buster") {
                if (!isEnabled)
                    return@webSocket

                val buster = extensionOrNull(Buster)
                if (buster?.ready != true) {
                    close()
                    return@webSocket
                }

                sockets.lock {
                    val current = get(buster.uuid)

                    when {
                        current?.isActive == false -> remove(buster.uuid)?.let {
                            inline {
                                it.close()
                            }
                        }

                        current != null -> {
                            inline {
                                current.close()
                            }
                        }
                    }
                }

                Socket(buster.username, buster.uuid, this).start()
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    inline fun broadcast(packet: Packet, crossinline predicate: Socket.() -> Boolean = { true }) {
        val payload = packet.export().asString()
        val buffers = mutableMapOf<Charset, ByteArray>()

        for (socket in this@BusterService)
            if (socket.predicate())
                socket.send(buffers.computeIfAbsent(socket.charset) {
                    payload.toByteArray(it)
                })
    }

    override fun iterator(): Iterator<Socket> =
        sockets.lock { values.toList() }.iterator()

    object Constants : Singleton() {
        var trustedCutoff = 2
        var guildCutoff = 2
    }

    val PlayerType.socket: Socket?
        get() = sockets[uuid]
}

fun hash(str: String): String {
    val digest = digest(str)
    return BigInteger(digest).toString(16)
}

private fun digest(str: String): ByteArray {
    val md = MessageDigest.getInstance("SHA-1")
    val strBytes: ByteArray = str.toByteArray()
    return md.digest(strBytes)
}


@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Listener(val value: Boolean = true)