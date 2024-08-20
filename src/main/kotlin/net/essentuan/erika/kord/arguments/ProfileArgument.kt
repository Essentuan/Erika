package net.essentuan.erika.kord.arguments

import com.busted_moments.buster.api.Profile
import dev.kord.common.Locale
import dev.kord.rest.builder.interaction.BaseInputChatBuilder
import dev.kord.rest.builder.interaction.string
import net.essentuan.erika.db.struct.player.search.invoke
import net.essentuan.erika.kord.framework.commands.CommandInteraction
import net.essentuan.erika.kord.framework.commands.KordArgument
import net.essentuan.erika.kord.framework.commands.annotations.Ephemeral
import net.essentuan.erika.kord.framework.message.embed
import net.essentuan.esl.Result
import net.essentuan.esl.cast
import net.essentuan.esl.color.McColor
import net.essentuan.esl.isPresent
import net.essentuan.esl.reflections.extensions.annotatedWith
import net.essentuan.esl.rx.first
import net.essentuan.esl.rx.map
import kotlin.reflect.KParameter

class ProfileArgument(
    param: KParameter
) : KordArgument<Profile>(param) {
    private val ephemeral = param annotatedWith Ephemeral::class

    override fun BaseInputChatBuilder.create() {
        string(id, description) {
            required = !param.isOptional

            minLength = 1
            maxLength = 16

            Locale.ALL.forEach { name(it, name) }
        }
    }

    override suspend fun CommandInteraction.value(): Result<Profile?> {
        val string = command.strings[id] ?: return Result.of(null)

        if (ephemeral)
            ephemeral()
        else
            public()

        val result = Profile {
            +string
        }.map { it.second }.first()

        if (!result.isPresent()) {
            ephemeral {
                embed(McColor.DARK_RED) {
                    title = "'$string' is not a player!"
                }
            }

            return Result.empty()
        }

        return result.cast()
    }
}