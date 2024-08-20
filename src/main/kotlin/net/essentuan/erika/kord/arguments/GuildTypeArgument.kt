package net.essentuan.erika.kord.arguments

import com.busted_moments.buster.api.GuildType
import dev.kord.common.Locale
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.interaction.response.MessageInteractionResponse
import dev.kord.core.event.interaction.ComponentInteractionCreateEvent
import dev.kord.core.event.interaction.SelectMenuInteractionCreateEvent
import dev.kord.rest.builder.component.option
import dev.kord.rest.builder.interaction.BaseInputChatBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.actionRow
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.framework.events.events
import net.essentuan.erika.kord.create
import net.essentuan.erika.kord.framework.commands.CommandInteraction
import net.essentuan.erika.kord.framework.commands.KordArgument
import net.essentuan.erika.kord.framework.commands.annotations.Ephemeral
import net.essentuan.erika.kord.framework.message.embed
import net.essentuan.erika.observers.guilds.list.Guilds
import net.essentuan.esl.Result
import net.essentuan.esl.color.McColor
import net.essentuan.esl.iteration.extensions.mutable.iterate
import net.essentuan.esl.orNull
import net.essentuan.esl.other.lock
import net.essentuan.esl.reflections.extensions.annotatedWith
import net.essentuan.esl.result
import net.essentuan.esl.scheduling.annotations.Every
import net.essentuan.esl.scheduling.tasks
import net.essentuan.esl.string.extensions.isUUID
import net.essentuan.esl.string.extensions.toUUID
import net.essentuan.esl.time.duration.seconds
import net.essentuan.esl.time.extensions.timeSince
import net.essentuan.esl.unsafe
import java.util.Date
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KParameter

class GuildTypeArgument(
    param: KParameter
) : KordArgument<GuildType>(param) {
    private val ephemeral = param annotatedWith Ephemeral::class
    private val continuations: MutableMap<Snowflake, Pair<Date, Continuation<UUID?>>> = mutableMapOf()

    init {
        tasks.resume()
        events.register()
    }

    @Subscribe
    private fun ComponentInteractionCreateEvent.on() {
        if (!interaction.componentId.startsWith("guildType|"))
            return

        val parts = interaction.componentId.removePrefix("guildType|").split("#")
        if (parts.size != 2)
            return

        val id = Snowflake(
            unsafe {
                parts[0].toULong()
            }.orNull() ?: return
        )

        if (id !in continuations)
            return

        when {
            parts[1] == "cancel" ->
                continuations.lock { remove(id) }?.second?.resume(null)

            parts[1] == "select" && this is SelectMenuInteractionCreateEvent -> {
                continuations.lock { remove(id) }?.second?.resume(interaction.values[0].toUUID())
            }
        }
    }


    @Every(seconds = 10.0)
    private fun cleanse() {
        continuations.lock {
            continuations.values iterate { (created, cont) ->
                if (created.timeSince() > 45.seconds) {
                    remove()

                    cont.resume(null)
                }
            }
        }
    }

    override fun BaseInputChatBuilder.create() {
        string(id, description) {
            required = !param.isOptional

            minLength = 3
            maxLength = 30

            Locale.ALL.forEach { name(it, name) }
        }
    }

    private suspend fun CommandInteraction.select(options: MutableMap<UUID, GuildType>): GuildType? {
        val id = Snowflake.create()

        if (ephemeral)
            ephemeral()
        else
            public()

        message {
            actionRow {
                stringSelect("guildType|$id#select") {
                    this.placeholder = "Please select a guild..."

                    for ((uuid, guild) in options)
                        option("${guild.name} [${guild.tag}]", uuid.toString())
                }

                interactionButton(
                    ButtonStyle.Danger,
                    "guildType|$id#cancel"
                ) {
                    label = "Cancel"
                }
            }
        }

        return options[
            suspendCoroutine<UUID?> {
                continuations.lock {
                    continuations[id] = Date() to it
                }
            } ?: return null
        ]
    }

    override suspend fun CommandInteraction.value(): Result<GuildType?> {
        val string = command.strings[id] ?: return Result.of(null)

        if (string.isUUID()) {
            val guild = Guilds[string.toUUID()]

            if (guild == null) {
                ephemeral {
                    embed(McColor.DARK_RED) {
                        title = "'$string' is not a valid guild!"
                    }
                }

                return Result.empty()
            }

            return guild.result()
        }

        val options = mutableMapOf<UUID, GuildType>()

        for (guild in Guilds) {
            if (guild.name == string || guild.tag == string)
                return guild.result()
            else if (guild.name.lowercase() == string.lowercase() || guild.tag.lowercase() == string.lowercase())
                options[guild.uuid] = guild
        }

        return when {
            options.isEmpty() -> {
                ephemeral {
                    embed(McColor.DARK_RED) {
                        title = "'$string' is not a valid guild!"
                    }
                }

                Result.empty()
            }

            options.size == 1 -> options.values.first().result()

            else -> {
                val selected = select(options)
                removeComponents()

                if (selected == null) {
                    (public() as? MessageInteractionResponse)?.delete()

                    return Result.empty()
                }

                selected.result()
            }
        }
    }
}