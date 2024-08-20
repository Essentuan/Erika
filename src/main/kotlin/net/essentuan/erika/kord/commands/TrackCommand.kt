package net.essentuan.erika.kord.commands

import com.busted_moments.buster.api.GuildType
import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Subcommand
import dev.kord.core.entity.channel.Channel
import dev.kord.core.entity.channel.TextChannel
import net.essentuan.erika.framework.annotation.Description
import net.essentuan.erika.kord.framework.commands.CommandInteraction
import net.essentuan.erika.kord.framework.commands.annotations.Ephemeral
import net.essentuan.erika.kord.framework.commands.annotations.Named
import net.essentuan.erika.kord.framework.commands.annotations.Requires
import net.essentuan.erika.kord.framework.message.description
import net.essentuan.erika.kord.framework.message.embed
import net.essentuan.erika.kord.framework.message.title
import net.essentuan.erika.kord.tracks.TrackManager
import net.essentuan.erika.kord.tracks.lanes.TerritoryLane
import net.essentuan.esl.color.McColor

@Command("track")
@Requires("command.track")
object TrackCommand {
    @Subcommand("clear")
    @Description("Clears any track from the given channel.")
    private suspend fun CommandInteraction.clear(
        @Named("channel") resolved: Channel = this.channel
    ) {
        val channel = resolved.fetchChannelOrNull()

        if (channel == null || channel !is TextChannel) {
            ephemeral {
                embed(McColor.DARK_RED) {
                    title {
                        +"You must specify a channel!"
                    }
                }
            }

            return
        }

        TrackManager.clear(channel.id)

        ephemeral {
            embed(McColor.GREEN) {
                title {
                    +"All tracks have been cleared!"
                }
            }
        }
    }

    @Subcommand("territories")
    @Description("Adds a lane that tracks territories.")
    private suspend fun CommandInteraction.territories(
        @Named("channel") resolved: Channel = this.channel,
        @Ephemeral guild: GuildType? = null
    ) {
        val channel = resolved.fetchChannelOrNull()

        if (channel == null || channel !is TextChannel) {
            ephemeral {
                embed(McColor.DARK_RED) {
                    title {
                        +"You must specify a channel!"
                    }
                }
            }

            return
        }

        TrackManager.getOrCreate(channel, source) += TerritoryLane(guild?.uuid)

        ephemeral {
            embed(McColor.GREEN) {
                description {
                    if (guild == null) {
                        +"Successfully starting tracking all territories!"
                    } else {
                        +"Successfully started tracking territories for "
                        +guild.name
                        +" ["
                        +guild.tag
                        +"]!"
                    }
                }
            }
        }
    }
}