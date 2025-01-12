package net.essentuan.erika.kord.tracks.lanes

import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.embed
import kotlinx.datetime.toKotlinInstant
import net.essentuan.erika.buster.listeners.IcoEvent
import net.essentuan.erika.buster.listeners.Raid
import net.essentuan.erika.buster.listeners.room
import net.essentuan.erika.framework.events.Event
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.framework.events.events
import net.essentuan.erika.inline
import net.essentuan.erika.kord.framework.message.colored
import net.essentuan.erika.kord.framework.message.name
import net.essentuan.erika.kord.framework.message.text
import net.essentuan.erika.kord.framework.message.title
import net.essentuan.erika.kord.tracks.Track
import net.essentuan.esl.color.Color
import net.essentuan.esl.iteration.extensions.iterate
import net.essentuan.esl.rx.iterate
import net.essentuan.esl.scheduling.tasks
import net.essentuan.esl.time.TimeUnit

class IcoEventLane : Track.Lane() {
    init {
        events.register()
        tasks.resume()
    }

    @Subscribe
    private fun Completion.on() {
        inline {
            send {
                embed {
                    colored(IcoEvent.COLOR)

                    author {
                        icon =
                            "https://cdn.discordapp.com/icons/810258030201143328/04210659f67b2bbcd890c4744eed1863.webp"

                        name {
                            +raid.type.display
                            +" has been beaten!"
                        }
                    }

                    title {
                        +"Done in "
                        +raid.duration.print(TimeUnit.SECONDS)
                    }

                    thumbnail {
                        url = raid.type.icon
                    }

                    raid.rooms.chunked(2) iterate { chunk ->
                        if (chunk.size == 2) {
                            room(chunk[0])
                            field { inline = true }
                            room(chunk[1])
                        } else {
                            field { inline = true }
                            room(chunk[0])
                            field { inline = true }
                        }
                    }

                    field { inline = false }

                    footer {
                        text {
                            raid.party iterate {
                                if (!isEmpty) {
                                    +", "

                                    if (!hasNext())
                                        +"and "
                                }

                                +it.name
                            }
                        }
                    }

                    timestamp = raid.end.toInstant().toKotlinInstant()
                }
            }
        }
    }

    override fun close() {
        tasks.close()
        events.unregister()
    }

    class Completion(val raid: Raid) : Event()
}