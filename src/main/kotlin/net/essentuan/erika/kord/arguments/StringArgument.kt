package net.essentuan.erika.kord.arguments

import dev.kord.common.Locale
import dev.kord.rest.builder.interaction.BaseInputChatBuilder
import dev.kord.rest.builder.interaction.string
import net.essentuan.erika.kord.framework.commands.CommandInteraction
import net.essentuan.erika.kord.framework.commands.KordArgument
import net.essentuan.esl.Result
import net.essentuan.esl.reflections.extensions.get
import net.essentuan.esl.result
import kotlin.reflect.KParameter

annotation class MinLength(val value: Int)
annotation class MaxLength(val value: Int)

class StringArgument(
    param: KParameter
) : KordArgument<String>(param) {
    private val min = param[MinLength::class]?.value
    private val max = param[MaxLength::class]?.value
    
    override fun BaseInputChatBuilder.create() {
        string(id, description) {
            required = !param.isOptional
            
            minLength = min
            maxLength = max
            
            Locale.ALL.forEach {name(it, name) }
        }
    }

    override suspend fun CommandInteraction.value(): Result<String?> =
        command.strings[id].result()

}