package net.essentuan.erika.kord

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.gateway.Intent
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import net.essentuan.erika.arg
import net.essentuan.erika.framework.Service
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.events.Event
import net.essentuan.erika.framework.events.KordEventType
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.framework.events.api.Listener
import net.essentuan.esl.Rating
import net.essentuan.esl.color.Color
import net.essentuan.esl.coroutines.blocking
import net.essentuan.esl.other.lock
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

typealias KColor = dev.kord.common.Color

private val LOGGER by Logging

object KordService : Service(), CoroutineScope {
    private var queue = mutableListOf<Continuation<Kord>>()

    @OptIn(PrivilegedIntent::class)
    private val kord: Kord by this {
        blocking {
            Kord(arg("token").value) {

            }.apply {
                events.buffer(Int.MAX_VALUE)
                    .onEach(this@KordService::execute)
                    .launchIn(this)
                launch {
                    login {
                        intents = Intents(
                            Intent.MessageContent,
                            Intent.DirectMessagesReactions,
                            Intent.DirectMessageTyping,
                            Intent.DirectMessages,
                            Intent.Guilds,
                            Intent.GuildMembers,
                            Intent.GuildMessageReactions,
                            Intent.GuildWebhooks
                        )
                    }
                }
            }
        }
    } finally {
        KordService.lock {
            active = null
        }

        blocking {
            shutdown()
        }
    }

    private var active: Kord? = null

    @Subscribe(Rating.HIGHEST)
    private suspend fun ReadyEvent.on() {
        val (conts, kord) = KordService.lock {
            active = this@on.kord

            queue.also {
                queue = mutableListOf()
            } to this@on.kord
        }

        for (cont in conts)
            cont.resume(kord)

        val self = kord.getSelf()

        LOGGER.info("Successfully logged into ${self.username}#${self.discriminator}!")
    }

    internal suspend fun kord(): Kord {
        val kord = active

        if (kord != null)
            return kord

        return suspendCoroutine<Kord> {
            KordService.lock {
                if (active != null)
                    it.resume(active!!)
                else
                    queue.add(it)
            }
        }
    }

    private suspend fun execute(event: KordEventType) {
        try {
            (Event.Bus[event::class] as Listener.List<KordEventType>).call(event)
        } catch (ex: Exception) {
            LOGGER.error("Error while processing event $event! Caused by: ", ex)
        }
    }

    fun KordEventType.post() {
        kord.launch {
            execute(this@post)
        }
    }

    override val coroutineContext: CoroutineContext
        get() = kord.coroutineContext
}

suspend fun kord(): Kord =
    KordService.kord()

val Color.kord: KColor
    get() = KColor(red, green, blue)

fun Snowflake.Companion.create() =
    Snowflake(Clock.System.now())
