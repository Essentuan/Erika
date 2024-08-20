package net.essentuan.erika.kord.arguments

import dev.kord.common.Locale
import dev.kord.core.entity.User
import dev.kord.rest.builder.interaction.BaseInputChatBuilder
import dev.kord.rest.builder.interaction.user
import net.essentuan.erika.kord.framework.commands.CommandInteraction
import net.essentuan.erika.kord.framework.commands.KordArgument
import net.essentuan.esl.Result
import net.essentuan.esl.result
import kotlin.reflect.KParameter

class UserArgument(
    param: KParameter
) : KordArgument<User>(param){
    override fun BaseInputChatBuilder.create() {
        user(id, description) {
            required = !param.isOptional
            
            Locale.ALL.forEach {name(it, name) }
        }
    }

    override suspend fun CommandInteraction.value(): Result<User?> =
        command.users[id].result()

}