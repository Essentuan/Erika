package net.essentuan.erika.kord.arguments

import dev.kord.common.Locale
import dev.kord.rest.builder.interaction.BaseInputChatBuilder
import dev.kord.rest.builder.interaction.string
import net.essentuan.erika.kord.framework.commands.CommandInteraction
import net.essentuan.erika.kord.framework.commands.KordArgument
import net.essentuan.erika.kord.framework.message.embed
import net.essentuan.esl.Result
import net.essentuan.esl.color.McColor
import net.essentuan.esl.result
import net.essentuan.esl.time.duration.Duration
import kotlin.reflect.KParameter

class DurationArgument(
    param: KParameter
) : KordArgument<Duration>(param) {
    override fun BaseInputChatBuilder.create() {
        string(id, description) {
            required = !param.isOptional

            minLength = 2
            maxLength = 100

            Locale.ALL.forEach { name(it, name) }
        }
    }

    override suspend fun CommandInteraction.value(): Result<Duration?> {
        val string = command.strings[id] ?: return Result.of(null)

        return try {
            Duration(string).result()
        } catch (ex: Exception) {
            ephemeral {
                embed(McColor.DARK_RED) {
                    title = "'$string' is not a valid duration!"
                }
            }

            Result.empty<Duration?>()
        }
    }
}