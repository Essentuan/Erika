package net.essentuan.erika.features.content.kord

import com.busted_moments.buster.api.GuildType
import com.busted_moments.buster.protocol.serverbound.ContentStage
import dev.kord.core.entity.channel.TextChannel
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.embed
import net.essentuan.erika.features.content.ContentRecord
import net.essentuan.erika.features.content.duration
import net.essentuan.erika.kord.framework.message.ContentBuilder
import net.essentuan.erika.kord.framework.message.colored
import net.essentuan.erika.observers.guilds.list.color
import net.essentuan.esl.color.Color
import net.essentuan.esl.iteration.extensions.iterate
import net.essentuan.esl.rx.iterate
import net.essentuan.esl.time.TimeUnit
import java.lang.StringBuilder

@PublishedApi
internal const val WYNNCRAFT_ICON = "https://cdn.wynncraft.com/nextgen/themes/classic/assets/wynncraft_icon.svg"

@PublishedApi
internal val DEFAULT_MESSAGE_COLOR = Color(255, 238, 143)

inline fun MessageBuilder.ContentMessage(
    guild: GuildType? = null,
    content: EmbedBuilder.() -> Unit
) {
    embed {
        colored(guild?.color ?: DEFAULT_MESSAGE_COLOR)

        author { icon = WYNNCRAFT_ICON }

        content()
    }
}

private fun EmbedBuilder.Spacer(inline: Boolean = true) {
    field { this.inline = inline }
}

fun EmbedBuilder.ContentStages(record: ContentRecord) {
    record.chunked(2) iterate { chunk ->
        if (chunk.size == 2) {
            ContentStage(chunk[0])
            Spacer()
            ContentStage(chunk[1])
        } else {
            Spacer()
            ContentStage(chunk[0])
            Spacer()
        }
    }

    Spacer(inline = false)
}

fun EmbedBuilder.ContentStage(stage: ContentStage) {
    field {
        name = stage.name
        value = stage.duration.print(TimeUnit.SECONDS)

        inline = true
    }
}

suspend fun ContentBuilder.ContentParty(record: ContentRecord) {
    record.party iterate  {
        if (!isEmpty) {
            +", "

            if (!hasNext())
                +"and "
        }

        +it.name
    }
}