package net.essentuan.erika.kord.arguments

import dev.kord.common.Locale
import dev.kord.core.entity.Entity
import dev.kord.rest.builder.interaction.BaseInputChatBuilder
import dev.kord.rest.builder.interaction.mentionable
import net.essentuan.erika.kord.framework.commands.CommandInteraction
import net.essentuan.erika.kord.framework.commands.KordArgument
import net.essentuan.esl.Result
import net.essentuan.esl.result
import kotlin.reflect.KParameter

class MentionableArgument(
    param: KParameter
) : KordArgument<Entity>(param) {
    override fun BaseInputChatBuilder.create() {
        mentionable(id, description) {
            required = !param.isOptional
            
            Locale.ALL.forEach {name(it, name) }
        }
    }

    override suspend fun CommandInteraction.value(): Result<Entity?> =
        command.mentionables[id].result()
}