package net.essentuan.erika.kord.arguments

import dev.kord.common.Locale
import dev.kord.core.entity.Role
import dev.kord.rest.builder.interaction.BaseInputChatBuilder
import dev.kord.rest.builder.interaction.role
import net.essentuan.erika.kord.framework.commands.CommandInteraction
import net.essentuan.erika.kord.framework.commands.KordArgument
import net.essentuan.esl.Result
import net.essentuan.esl.result
import kotlin.reflect.KParameter

class RoleArgument(
    param: KParameter
) : KordArgument<Role>(param){
    override fun BaseInputChatBuilder.create() {
        role(id, description) {
            required = !param.isOptional
            
            Locale.ALL.forEach {name(it, name) }
        }
    }

    override suspend fun CommandInteraction.value(): Result<Role?> =
        command.roles[id].result()

}