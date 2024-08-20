package net.essentuan.erika.kord.arguments

import dev.kord.common.Locale
import dev.kord.rest.builder.interaction.BaseInputChatBuilder
import dev.kord.rest.builder.interaction.boolean
import net.essentuan.erika.kord.framework.commands.CommandInteraction
import net.essentuan.erika.kord.framework.commands.KordArgument
import net.essentuan.esl.Result
import net.essentuan.esl.result
import kotlin.reflect.KParameter

class BooleanArgument(
    param: KParameter
) : KordArgument<Boolean>(param) {
    override fun BaseInputChatBuilder.create() {
        boolean(id, description) {
            required = !param.isOptional
            
            Locale.ALL.forEach {name(it, name) }
        }
    }

    override suspend fun CommandInteraction.value(): Result<Boolean?> =
        command.booleans[id].result()

}