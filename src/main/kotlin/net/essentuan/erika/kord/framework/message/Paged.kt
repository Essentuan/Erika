package net.essentuan.erika.kord.framework.message

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.builder.message.modify.MessageModifyBuilder
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.framework.events.events
import net.essentuan.erika.kord.create
import net.essentuan.erika.kord.framework.commands.CommandInteraction
import net.essentuan.esl.collections.maps.IntMap
import net.essentuan.esl.collections.setOf
import net.essentuan.esl.other.lock
import net.essentuan.esl.scheduling.annotations.Every
import net.essentuan.esl.scheduling.tasks
import net.essentuan.esl.time.duration.Duration
import net.essentuan.esl.time.duration.minutes
import net.essentuan.esl.time.extensions.timeSince
import java.util.Date
import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.min

private const val PUBLIC = false
private const val EPHEMERAL = true

//Prevents active page responders from being garbage collected
private val active = IdentityHashMap<Paged, Boolean>().setOf()

abstract class Paged(
    private val ephemeral: Boolean,
    private val interaction: CommandInteraction,
    private val buttons: Set<String>,
    private val expiry: Duration
) {
    private var open: Boolean = true
    private var lastInteraction: Date = Date()

    init {
        events.register()
        tasks.resume()

        active.lock { add(this@Paged) }
    }

    private val id = Snowflake.create()

    private var body: suspend MessageModifyBuilder.(suspend ContentBuilder.() -> Unit) -> Unit = {
        content { it() }
    }
    private var pages: MutableMap<Int, suspend ContentBuilder.() -> Unit> = IntMap()

    var page: Int = 0
        private set

    var maxPages: Int = 0
        private set

    @Subscribe
    private suspend fun ButtonInteractionCreateEvent.on() {
        if (!interaction.componentId.endsWith(id.toString()))
            return

        lastInteraction = Date()

        when (interaction.componentId.removeSuffix(id.toString())) {
            PREVIOUS_PAGE -> {
                page--

                if (page < 0)
                    page = maxPages

                respond()
            }

            NEXT_PAGE -> {
                page++

                if (page > maxPages)
                    page = 0

                respond()
            }

            REFRESH -> {
                refresh()
            }

            CANCEL -> {
                close()
            }
        }

        interaction.deferPublicMessageUpdate()
    }

    @Every(seconds = 1.0)
    private suspend fun cleanse() {
        if (lastInteraction.timeSince() > expiry)
            close()
    }

    fun body(body: suspend MessageModifyBuilder.(page: suspend ContentBuilder.() -> Unit) -> Unit) {
        this.body = body
    }

    operator fun Int.invoke(page: suspend ContentBuilder.() -> Unit) {
        require(this >= 0) {
            "$this must be a positive integer!"
        }

        require(pages.put(this, page) == null) {
            "Duplicate page $this!"
        }

        maxPages = max(maxPages, this)
    }

    operator fun IntRange.invoke(page: suspend ContentBuilder.(Int) -> Unit) {
        for (i in this)
            i {
                page(i)
            }
    }

    protected abstract suspend fun build()

    suspend fun refresh() {
        maxPages = 0
        pages = IntMap()

        build()

        page = min(page, maxPages)

        respond()
    }

    private suspend fun MessageModifyBuilder.edit() {
        body(pages[page] ?: ::nothing)

        if (!open)
            components = mutableListOf()
        else {
            actionRow {
                for (button in buttons) {
                    val id = "$button$id"

                    when (button) {
                        PREVIOUS_PAGE ->
                            interactionButton(ButtonStyle.Primary, id) { label = emoji("arrow_backward") }

                        NEXT_PAGE ->
                            interactionButton(ButtonStyle.Primary, id) { label = emoji("arrow_forward") }

                        REFRESH ->
                            interactionButton(ButtonStyle.Secondary, id) { label = emoji("repeat") }

                        CANCEL ->
                            interactionButton(ButtonStyle.Danger, id) { label = "Cancel" }
                    }
                }
            }
        }
    }

    private suspend fun respond(closed: Boolean = !open) {
        if (!open)
            return

        open = !closed

        if (ephemeral)
            interaction.ephemeral {
                edit()
            }
        else
            interaction.message {
                edit()
            }
    }

    suspend fun close() {
        events.unregister()
        tasks.close()

        active.lock { remove(this@Paged) }

        respond(true)
    }

    companion object {
        const val PREVIOUS_PAGE = "previous"
        const val NEXT_PAGE = "next"
        const val REFRESH = "refresh"
        const val CANCEL = "cancel"

        private suspend fun nothing(content: ContentBuilder): Unit =
            Unit
    }
}

suspend inline fun CommandInteraction.paged(
    vararg buttons: String = arrayOf(
        Paged.PREVIOUS_PAGE,
        Paged.NEXT_PAGE,
        Paged.REFRESH,
        Paged.CANCEL
    ),
    expiry: Duration = 2.minutes,
    ephemeral: Boolean = false,
    crossinline block: suspend Paged.() -> Unit
) {
    object : Paged(ephemeral, this@paged, buttons.toSet(), expiry) {
        override suspend fun build() =
            block()
    }.refresh()
}