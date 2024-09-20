package net.essentuan.erika.buster.listeners

import com.busted_moments.buster.api.Guild
import com.busted_moments.buster.api.Profile
import com.busted_moments.buster.protocol.Packet
import com.busted_moments.buster.protocol.Request
import com.busted_moments.buster.protocol.requests.GuildRequest
import com.busted_moments.buster.protocol.requests.MemberRequest
import com.busted_moments.buster.protocol.requests.PingRequest
import net.essentuan.erika.buster.BusterService
import net.essentuan.erika.buster.PacketListener
import net.essentuan.erika.buster.Socket
import net.essentuan.erika.db.struct.guild.GuildModel.Table.external
import net.essentuan.erika.db.struct.guild.member.MemberModel.Table.external
import net.essentuan.erika.db.struct.guild.member.search.invoke
import net.essentuan.erika.db.struct.guild.search.invoke
import net.essentuan.esl.Result
import net.essentuan.esl.collections.maps.expireAfter
import net.essentuan.esl.collections.synchronized
import net.essentuan.esl.filterNotNull
import net.essentuan.esl.ifPresentOrElse
import net.essentuan.esl.map
import net.essentuan.esl.orNull
import net.essentuan.esl.other.lock
import net.essentuan.esl.rx.first
import net.essentuan.esl.rx.map
import net.essentuan.esl.string.extensions.isUUID
import net.essentuan.esl.time.duration.Duration
import net.essentuan.esl.time.duration.minutes
import net.essentuan.esl.unsafe
import kotlin.reflect.KClass

object Requests {
    init {
        GuildRequest::class {
            by { it.guild }

            fetch {
                if (!Guild.Tags.isValid(it.guild) && !Guild.Names.isValid(it.guild) && !it.guild.isUUID())
                    null
                else
                    Guild(it.guild).map { (_, result) -> result }.first().map { guild -> guild.lock { external() } }
                        .orNull()
            }
        }

        MemberRequest::class {
            by { it.member }

            fetch {
                if (!Profile.isValid(it.member))
                    null
                else
                    Guild.Member(it.member, create = true).map { (_, result) -> result }.first()
                        .map { member -> member.lock { external() } }.orNull()
            }
        }

        PingRequest::class {
            by { it.ray }

            fetch {
                return@fetch 0
            }
        }
    }

    inline operator fun <T : Request<*>> KClass<T>.invoke(
        block: Listener<T>.() -> Unit
    ) {
        Listener(java).apply(block)
    }

    class Listener<T : Request<*>>(
        override val packet: Class<T>
    ) : PacketListener {
        override val privileged: Boolean
            get() = true

        init {
            BusterService.register(this)
        }

        private val cache = mutableMapOf<Any?, Data>()
            .synchronized()
            .expireAfter { (_, data) -> data.expiry }

        private lateinit var id: Socket.(T) -> Any?
        private lateinit var execute: suspend Socket.(T) -> Any?
        private var expiry: Socket.(T, Any?) -> Duration = { _, _ -> 5.minutes }

        fun by(block: Socket.(T) -> Any?) {
            id = block
        }

        fun fetch(block: suspend Socket.(T) -> Any?) {
            execute = block
        }

        fun expire(block: Socket.(T, Any?) -> Duration) {
            expiry = block
        }

        inner class Data(
            val result: Result<Any>,
            val expiry: Duration
        )

        override suspend fun invoke(socket: Socket, packet: Packet) {
            val request = packet as T

            val id = socket.id(request)
            val data = cache[id] ?: unsafe {
                socket.execute(request)
            }.filterNotNull().run {
                Data(
                    this,
                    socket.expiry(request, orNull())
                )
            }

            socket.apply {
                data.result.ifPresentOrElse({ request.fulfill(it) }) { request.fulfill(error = "Bad request!") }
            }
        }
    }
}

