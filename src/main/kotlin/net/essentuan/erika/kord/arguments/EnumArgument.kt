package net.essentuan.erika.kord.arguments

import dev.kord.common.Locale
import dev.kord.rest.builder.interaction.BaseInputChatBuilder
import dev.kord.rest.builder.interaction.string
import net.essentuan.erika.kord.framework.commands.CommandInteraction
import net.essentuan.erika.kord.framework.commands.KordArgument
import net.essentuan.erika.kord.framework.message.embed
import net.essentuan.esl.Result
import net.essentuan.esl.color.McColor
import net.essentuan.esl.encoding.builtin.EnumEncoder
import net.essentuan.esl.reflections.Annotations
import net.essentuan.esl.reflections.extensions.javaClass
import net.essentuan.esl.result
import net.essentuan.esl.string.extensions.camelCase
import kotlin.reflect.KParameter

class EnumArgument(
    param: KParameter
) : KordArgument<Enum<*>>(param) {
    @Suppress("UNCHECKED_CAST")
    private val enum = param.type.javaClass as Class<Enum<*>>

    override fun BaseInputChatBuilder.create() {
        string(id, description) {
            required = !param.isOptional

            for (option in enum.enumConstants) {
                choice(option.name.camelCase(separator = " "), option.name)
            }

            Locale.ALL.forEach {name(it, name) }
        }
    }

    override suspend fun CommandInteraction.value(): Result<Enum<*>?> {
        try {
            return EnumEncoder.decode(
                command.strings[id] ?: return Result.of(null),
                emptySet(),
                enum,
                Annotations.empty()
            ).result()
        } catch(ex: Exception) {
            ephemeral {
                embed(McColor.DARK_RED) {
                    title = "'${command.strings[id]}' is not an option for $name!"
                }
            }

            return Result.empty()
        }
    }
}