package net.essentuan.erika.kord.framework.message

import com.vdurmont.emoji.EmojiParser
import dev.kord.core.entity.Entity
import dev.kord.core.entity.Role
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.Channel
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.embed
import net.essentuan.erika.kord.kord
import net.essentuan.esl.color.Color

@JvmInline
value class ContentBuilder(
    private val builder: StringBuilder = StringBuilder()
) {
    val isEmpty: Boolean
        get() = builder.isEmpty()

    fun append(obj: Any?) {
        when (obj) {
            is String -> builder.append(obj)
            is User -> builder.append("<@$${obj.id}>")
            is Role -> builder.append("<@&${obj.id}>")
            is Channel -> builder.append("<#${obj.id}>")
            is Entity -> builder.append("<unknown-entity>")

            else -> builder.append(obj)
        }
    }

    operator fun Any?.unaryPlus() =
        append(this)

    fun newLine() {
        builder.append('\n')
    }

    override fun toString(): String =
        builder.toString()
}

inline fun MessageBuilder.content(block: ContentBuilder.() -> Unit) {
    content = ContentBuilder().also(block).toString()
}

inline fun MessageBuilder.embed(color: Color, crossinline block: EmbedBuilder.() -> Unit) {
    embed {
        this.color = color.kord

        block()
    }
}

fun EmbedBuilder.colored(color: Color) {
    this.color = color.kord
}

inline fun EmbedBuilder.title(block: ContentBuilder.() -> Unit) {
    title = ContentBuilder().also(block).toString()
}

inline fun EmbedBuilder.description(block: ContentBuilder.() -> Unit) {
    description = ContentBuilder().also(block).toString()
}

inline fun EmbedBuilder.Author.name(block: ContentBuilder.() -> Unit) {
    name = ContentBuilder().also(block).toString()
}

inline fun EmbedBuilder.Footer.text(block: ContentBuilder.() -> Unit) {
    text = ContentBuilder().also(block).toString()
}

val Entity.mention: String
    get() =
        when (this) {
            is User -> "<@$id>"
            is Role -> "<@&$id>"
            is Channel -> "<#>"
            else -> "<unknown-entity>"
        }

//https://github.com/vdurmont/emoji-java/blob/master/EMOJIS.md
fun emoji(emoji: String): String =
    EmojiParser.parseToUnicode(":$emoji:")