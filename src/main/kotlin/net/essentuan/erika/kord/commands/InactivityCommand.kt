package net.essentuan.erika.kord.commands

import com.busted_moments.buster.api.Guild
import com.busted_moments.buster.api.GuildType
import com.busted_moments.buster.api.Playtime
import com.busted_moments.buster.api.Profile
import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Subcommand
import dev.kord.rest.builder.message.embed
import net.essentuan.erika.db.struct.guild.search.invoke
import net.essentuan.erika.framework.annotation.Description
import net.essentuan.erika.framework.db.`object`.types.Singleton
import net.essentuan.erika.framework.formatting.JUSTIFY_LEFT
import net.essentuan.erika.framework.formatting.JUSTIFY_RIGHT
import net.essentuan.erika.framework.formatting.table
import net.essentuan.erika.kord.framework.commands.CommandInteraction
import net.essentuan.erika.kord.framework.commands.annotations.Ephemeral
import net.essentuan.erika.kord.framework.commands.annotations.Named
import net.essentuan.erika.kord.framework.commands.annotations.Requires
import net.essentuan.erika.kord.framework.commands.annotations.Whitelist
import net.essentuan.erika.kord.framework.message.description
import net.essentuan.erika.kord.framework.message.embed
import net.essentuan.erika.kord.framework.message.paged
import net.essentuan.erika.kord.framework.message.text
import net.essentuan.erika.kord.framework.message.title
import net.essentuan.erika.observers.guilds.list.Guilds
import net.essentuan.esl.color.McColor
import net.essentuan.esl.orThrow
import net.essentuan.esl.other.lock
import net.essentuan.esl.rx.first
import net.essentuan.esl.rx.map
import net.essentuan.esl.time.TimeUnit
import net.essentuan.esl.time.duration.Duration
import net.essentuan.esl.time.duration.FormatFlag
import net.essentuan.esl.time.duration.days
import net.essentuan.esl.time.duration.hours
import net.essentuan.esl.time.duration.minutes
import net.essentuan.esl.time.extensions.timeSince
import java.util.UUID
import kotlin.math.ceil

private val THE_SIMPLE_ONES_UUID = UUID.fromString("1199115c-780f-459f-8ffd-6acef91ba8a1")
private const val MEMBERS_PER_PAGE = 15

@Command("inactivity")
@Whitelist(573815520898449419)
object InactivityCommand : Singleton() {
    private var inactive: Duration = 90.minutes
    private var warning: Duration = 3.hours
    private var whitelist: MutableSet<UUID> = mutableSetOf()

    @Subcommand("fetch")
    @Requires("command.inactivity.fetch")
    @Description("Fetches the inactivity information for a guild.")
    private suspend fun CommandInteraction.fetch(
        @Named("guild") type: GuildType = Guilds[THE_SIMPLE_ONES_UUID]!!
    ) {
        public()

        val guild = Guild(type.uuid).map { (_, it) -> it.orThrow() }.first()

        paged(expiry = 10.minutes) {
            val members = guild.lock { toList() }
                .asSequence()
                .map { Computed(it) }
                .sorted()
                .toList()

            body {
                embed {
                    title {
                        +guild.name
                        +" ["
                        +guild.tag
                        +"] - Inactivity"
                    }

                    description {
                        +"```ml"
                        newLine()

                        it()

                        newLine()
                        +"```"
                    }

                    footer {
                        text {
                            +"Showing page "
                            append(page + 1)
                            +" of "
                            append(maxPages + 1)
                        }
                    }
                }
            }

            (0..<(ceil(members.size/MEMBERS_PER_PAGE.toFloat()).toInt())) {
                +table {
                    column("Username", JUSTIFY_LEFT)
                    column("State", JUSTIFY_RIGHT)
                    column("Playtime", JUSTIFY_LEFT)

                    members.drop(page * MEMBERS_PER_PAGE)
                        .take(MEMBERS_PER_PAGE)
                        .forEach {
                            row(
                                it.profile.name,
                                it.state,
                                it.playtime.print(FormatFlag.COMPACT, TimeUnit.MINUTES).trim()
                            )
                        }
                }
            }
        }
    }

    @Subcommand("requirements inactive")
    @Requires("command.inactivity.config")
    private suspend fun CommandInteraction.inactive(
        requirement: Duration
    ) {
        inactive = requirement

        ephemeral {
            embed(McColor.GREEN) {
                title = "Inactive requirement has been set to ${requirement.print(FormatFlag.COMPACT)}!"
            }
        }
    }

    @Subcommand("requirements warning")
    @Requires("command.inactivity.config")
    private suspend fun CommandInteraction.warning(
        requirement: Duration
    ) {
        warning = requirement

        ephemeral {
            embed(McColor.GREEN) {
                title = "Warn requirement has been set to ${requirement.print(FormatFlag.COMPACT)}!"
            }
        }
    }

    @Subcommand("whitelist add")
    @Requires("command.inactivity.config")
    private suspend fun CommandInteraction.whitelistAdd(
        @Ephemeral player: Profile
    ) {
        if (whitelist.add(player.uuid)) {
            ephemeral {
                embed(McColor.GREEN) {
                    title = "${player.name} has been added to the whitelist!"
                }
            }
        } else {
            ephemeral {
                embed(McColor.GOLD) {
                    title = "${player.name} was already on the whitelist."
                }
            }
        }
    }

    @Subcommand("whitelist remove")
    @Requires("command.inactivity.config")
    private suspend fun CommandInteraction.whitelistRemove(
        @Ephemeral player: Profile
    ) {
        if (whitelist.remove(player.uuid)) {
            ephemeral {
                embed(McColor.GREEN) {
                    title = "${player.name} has been removed to the whitelist!"
                }
            }
        } else {
            ephemeral {
                embed(McColor.GOLD) {
                    title = "${player.name} was not on the whitelist."
                }
            }
        }
    }


    private data class Computed(
        private val member: Guild.Member,
        val playtime: Playtime = member.profile.playtime.past(7.days)
    ) : Guild.Member by member, Comparable<Computed> {
        val state: String
            get() = when {
                profile.uuid in whitelist -> "Whitelisted"
                (joinedAt?.timeSince() ?: 7.days) < 7.days -> "Immune"
                playtime > warning -> "Active"
                playtime > inactive -> "Warn"
                else -> "Inactive"
            }

        override fun compareTo(other: Computed): Int {
            val whitelisted = profile.uuid in whitelist
            val otherWhitelisted = other.profile.uuid in whitelist

            return when {
                whitelisted && !otherWhitelisted -> 1
                !whitelisted && otherWhitelisted -> -1
                else -> {
                    val pt = playtime.compareTo(other.playtime)
                    if (pt == 0)
                        profile.name.compareTo(other.profile.name)
                    else
                        pt
                }
            }
        }

    }
}