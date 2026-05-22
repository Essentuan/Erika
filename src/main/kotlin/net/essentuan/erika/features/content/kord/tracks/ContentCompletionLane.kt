package net.essentuan.erika.features.content.kord.tracks

import kotlinx.datetime.toKotlinInstant
import net.essentuan.erika.features.content.events.ContentEvent
import net.essentuan.erika.features.content.kord.ContentMessage
import net.essentuan.erika.features.content.kord.ContentParty
import net.essentuan.erika.features.content.kord.ContentStages
import net.essentuan.erika.features.content.modifiers.GuildRaidModifier
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.framework.events.events
import net.essentuan.erika.inline
import net.essentuan.erika.kord.framework.message.name
import net.essentuan.erika.kord.framework.message.text
import net.essentuan.erika.kord.framework.message.title
import net.essentuan.erika.kord.tracks.Track
import net.essentuan.esl.rx.iterate
import net.essentuan.esl.scheduling.tasks
import net.essentuan.esl.time.TimeUnit
import java.util.UUID

class ContentCompletionLane(private val guildId: UUID) : Track.Lane() {
    init {
        events.register()
        tasks.resume()
    }

    @Subscribe
    private fun ContentEvent.Completion.on() {
        val guild = record.modifiers[GuildRaidModifier] ?: return
        if (guild.uuid != guildId) return

        buildMessage {
            ContentMessage(guild = guild) {
                author {
                    name {
                        +record.type.displayName
                        +" has been completed!"
                    }
                }

                title {
                    +"Done in "
                    +record.duration.print(TimeUnit.SECONDS)
                }

                thumbnail { url = record.type.icon.toString() }

                ContentStages(record)

                footer {
                    text { ContentParty(record) }
                }

                timestamp = record.end.toInstant().toKotlinInstant()
            }
        }
    }

    override fun close() {
        tasks.close()
        events.unregister()
    }
}