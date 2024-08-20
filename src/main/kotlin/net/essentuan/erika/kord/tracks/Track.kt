package net.essentuan.erika.kord.tracks

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.interaction.GlobalButtonInteractionCreateEvent
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.request.KtorRequestException
import kotlinx.coroutines.launch
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.framework.events.events
import net.essentuan.erika.inline
import net.essentuan.erika.kord.KordService
import net.essentuan.erika.kord.framework.message.description
import net.essentuan.erika.kord.framework.message.embed
import net.essentuan.erika.kord.kord
import net.essentuan.esl.color.McColor
import net.essentuan.esl.encoding.Unsafe
import net.essentuan.esl.future.api.Future
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.annotations.Ignored
import net.essentuan.esl.model.annotations.NoHash
import net.essentuan.esl.other.lock

@Suppress("UNCHECKED_CAST")
data class Track(
    private val id: Snowflake,
    private val creator: Snowflake,
    @Unsafe
    @property:Unsafe
    private val lanes: MutableMap<Int, Any> = mutableMapOf<Int, Any>(),
    private var counter: Int = 0
) : Json.Model, Map<Int, Track.Lane> by (lanes as Map<Int, Lane>) {
    @Ignored
    private var future: Future<TextChannel?>? = null

    init {
        for ((_, lane) in this)
            lane.apply { claim() }

        events.register()
    }

    @Subscribe
    private suspend fun GlobalButtonInteractionCreateEvent.on() {
        if (interaction.componentId != "resolve_$id")
            return

        lock {
            future = null
        }

        interaction.deferPublicMessageUpdate()
        interaction.message.delete()

        KordService.launch {
            test()
        }
    }

    private suspend fun handleNoPermission(kord: Kord, channel: TextChannel) {
        lock {
            future = Future(null)
        }

        val dm = kord.getUser(creator)?.getDmChannelOrNull()

        if (dm == null) {
            TrackManager.clear(id)

            return
        }

        dm.createMessage {
            embed(McColor.DARK_RED) {
                description {
                    +"The track in "
                    +channel
                    +" does not have permission to send messages."
                }
            }

            actionRow {
                interactionButton(
                    ButtonStyle.Primary,
                    "resolve_$id"
                ) {
                    label = "Resolve"
                }
            }
        }
    }

    private suspend fun channel(): TextChannel? {
        val channel = lock {
            if (future == null)
                future = inline<TextChannel?> {
                    val kord = kord()
                    val channel: TextChannel? = kord.getChannelOf(id)

                    if (channel == null) {
                        TrackManager.clear(id)

                        return@inline null
                    }

                    channel
                }

            future!!
        }.await()

        if (channel == null) {
            TrackManager.clear(id)

            return null
        }

        val kord = kord()

        try {
            val perms = channel.getEffectivePermissions(kord.selfId)
            if (Permission.ViewChannel in perms && Permission.SendMessages in perms)
                return channel
        } catch (_: Exception) {
        }

        handleNoPermission(kord, channel)

        return null
    }

    suspend fun test() {
        channel()
    }

    operator fun plusAssign(lane: Lane) {
        lane.apply { claim() }
    }

    fun delete(id: Int) {
        (lanes.remove(id) as? Lane)?.close()
    }

    fun close() {
        events.unregister()

        for ((_, lane) in this)
            lane.close()
    }

    abstract class Lane : Json.Model {
        @NoHash
        private var _id: Int = -1

        @Ignored
        private lateinit var owner: Track

        val id: Int
            get() = _id

        protected suspend fun channel(): TextChannel? {
            return if (::owner.isInitialized)
                owner.channel()
            else
                null
        }

        protected suspend fun handleNoPermission(channel: TextChannel) {
            owner.handleNoPermission(kord(), channel)
        }

        protected suspend inline fun send(block: MessageBuilder.() -> Unit): Message? {
            val channel = channel() ?: return null

            return try {
                channel.createMessage(block)
            } catch (ex: KtorRequestException) {
                if (ex.status.code == 403)
                    handleNoPermission(channel)
                else
                    throw ex

                null
            }
        }

        internal fun Track.claim() {
            if (_id == -1)
                _id = lock {
                    val id = counter
                    counter++

                    id
                }

            lanes[_id] = this@Lane
            owner = this
        }

        abstract fun close()
    }
}