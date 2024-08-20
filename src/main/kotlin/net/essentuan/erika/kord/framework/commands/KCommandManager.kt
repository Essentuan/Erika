package net.essentuan.erika.kord.framework.commands

import com.essentuan.acf.core.annotations.Command
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.message.embed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.kord.framework.commands.nodes.KCommand
import net.essentuan.erika.kord.kord
import net.essentuan.esl.color.McColor
import net.essentuan.esl.coroutines.await
import net.essentuan.esl.future.api.Future
import net.essentuan.esl.reflections.Reflections
import net.essentuan.esl.reflections.extensions.instance

private val LOGGER by Logging

object KCommandManager {
    private val commands: MutableMap<String, KCommand> = mutableMapOf()

    init {
        Reflections.types
            .annotatedWith(Command::class)
            .map { it.instance }
            .filterNotNull()
            .forEach {
                val command = KCommand(it)

                commands[command.id] = command
            }
    }

    @Subscribe
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun ReadyEvent.on() {
        LOGGER.info("Deleting pre-existing commands.")

        await {
            +kord.getGlobalApplicationCommands()
                .onEach { it.delete() }
                .launchIn(kord)

            +kord.guilds
                .flatMapMerge { kord.getGuildApplicationCommands(it.id) }
                .onEach { it.delete() }
                .launchIn(kord)
        }

        LOGGER.info("Finished deleting pre-existing commands!")

        val guilds = kord.guilds.toList()

        LOGGER.info("Creating commands.")

        await {
            for ((id, command) in commands) {
                when (command.type) {
                    CommandType.GLOBAL -> {
                        +Future {
                            kord.createGlobalChatInputCommand(id, command.description) {
                                command.apply { build(null) }
                            }
                        }
                    }

                    CommandType.GUILD -> {
                        for (guild in guilds)
                            if (
                                (command.whitelist.isEmpty() || guild.id in command.whitelist) &&
                                (command.blacklist.isEmpty() || guild.id !in command.blacklist)
                            )
                                +Future {
                                    kord.createGuildChatInputCommand(
                                        guild.id,
                                        id,
                                        command.description
                                    ) {
                                        command.apply { build(guild) }
                                    }
                                }
                    }
                }
            }
        }

        LOGGER.info("Finished creating commands!")
    }

    @Subscribe
    private suspend fun GuildChatInputCommandInteractionCreateEvent.on() {
        val interaction = CommandInteraction(this.interaction)
        val command = commands[interaction.command.rootName]

        if (command == null) {
            interaction.message {
                embed {
                    title = "You do not have the proper permission to use this command!"
                    color = McColor.DARK_RED.kord
                }
            }

            return
        }

        interaction.apply {
            command.run {
                execute(this@on.interaction.getGuild())
            }
        }
    }
}