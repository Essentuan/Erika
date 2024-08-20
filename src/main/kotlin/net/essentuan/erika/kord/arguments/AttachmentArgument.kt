package net.essentuan.erika.kord.arguments

import dev.kord.common.Locale
import dev.kord.core.entity.Attachment
import dev.kord.rest.builder.interaction.BaseInputChatBuilder
import dev.kord.rest.builder.interaction.attachment
import net.essentuan.erika.kord.framework.commands.CommandInteraction
import net.essentuan.erika.kord.framework.commands.KordArgument
import net.essentuan.esl.Result
import net.essentuan.esl.result
import kotlin.reflect.KParameter

class AttachmentArgument(
    param: KParameter
) : KordArgument<Attachment>(param) {
    override fun BaseInputChatBuilder.create() {
        attachment(id, description) {
            required = !param.isOptional
            
            Locale.ALL.forEach {name(it, name) }
        }
    }

    override suspend fun CommandInteraction.value(): Result<Attachment?> =
        command.attachments[id].result()

}