package net.essentuan.erika.kord.framework.commands

import dev.kord.core.behavior.interaction.response.DeferredMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.InteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.InteractionCommand
import dev.kord.core.entity.interaction.response.MessageInteractionResponse
import dev.kord.rest.builder.message.modify.MessageModifyBuilder

class CommandInteraction(
    private val interaction: ChatInputCommandInteraction
) {
    private lateinit var response: InteractionResponseBehavior
    private var removeComponents: Boolean = false

    val command: InteractionCommand
        get() = interaction.command
    
    val source: User
        get() = interaction.user

    val channel: MessageChannel
        get() =
            interaction.channel as MessageChannel

    fun removeComponents() {
        removeComponents = true
    }

    suspend fun public(): InteractionResponseBehavior  {
        if (!::response.isInitialized) {
            response = interaction.deferPublicResponse()
        }
        
        return response
    }

    fun edit(response: InteractionResponseBehavior) {
        this.response = response
    }

    suspend fun MessageModifyBuilder.prepare() {
        if (removeComponents)
            components = mutableListOf()

        removeComponents = false
    }

    suspend inline fun MessageModifyBuilder.buildMessage(
        crossinline builder: suspend MessageModifyBuilder.() -> Unit
    ) {
        prepare()

        builder()
    }

    suspend inline fun message(crossinline builder: suspend MessageModifyBuilder.() -> Unit) {
        when(val message = public()) {
            is MessageInteractionResponse -> {
                edit(message.edit { buildMessage(builder) })
            }
            
            is DeferredMessageInteractionResponseBehavior -> {
                edit(message.respond { buildMessage(builder) })
            }

            else -> error("")
        }
    }
    
    suspend fun ephemeral(): InteractionResponseBehavior  {
        if (!::response.isInitialized) {
            response = interaction.deferEphemeralResponse()
        }
        
        return response
    }
    
    suspend inline fun ephemeral(crossinline builder: suspend MessageModifyBuilder.() -> Unit) {
        when(val message = ephemeral()) {
            is MessageInteractionResponse -> {
                edit(message.edit { buildMessage(builder) })
            }
            
            is DeferredMessageInteractionResponseBehavior -> {
                edit(message.respond { buildMessage(builder) })
            }

            else -> error("")
        }
    }
}