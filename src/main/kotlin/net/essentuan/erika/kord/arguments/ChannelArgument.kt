package net.essentuan.erika.kord.arguments

import dev.kord.common.Locale
import dev.kord.common.entity.ChannelType
import dev.kord.core.entity.channel.Channel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.rest.builder.interaction.BaseInputChatBuilder
import dev.kord.rest.builder.interaction.channel
import net.essentuan.erika.kord.framework.commands.CommandInteraction
import net.essentuan.erika.kord.framework.commands.KordArgument
import net.essentuan.esl.Result
import net.essentuan.esl.reflections.extensions.extends
import net.essentuan.esl.reflections.extensions.javaClass
import net.essentuan.esl.result
import kotlin.reflect.KParameter

class ChannelArgument(
    param: KParameter
) : KordArgument<Channel>(param) {
    private val channelType = param.type.javaClass

    override fun BaseInputChatBuilder.create() {
        channel(id, description) {
            required = !param.isOptional

            when {
                channelType extends TextChannel::class -> {
                    channelTypes = listOf(ChannelType.GuildText)
                }
            }

            Locale.ALL.forEach { name(it, name) }
        }
    }

    override suspend fun CommandInteraction.value(): Result<Channel?> =
        command.channels[id].result()
}