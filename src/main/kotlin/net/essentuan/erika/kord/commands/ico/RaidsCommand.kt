package net.essentuan.erika.kord.commands.ico

import com.busted_moments.buster.api.Profile
import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Subcommand
import dev.kord.rest.builder.message.embed
import kotlinx.datetime.toKotlinInstant
import net.essentuan.erika.buster.listeners.IcoEvent
import net.essentuan.erika.buster.listeners.Raid
import net.essentuan.erika.buster.listeners.room
import net.essentuan.erika.db.struct.player.search.invoke
import net.essentuan.erika.framework.annotation.Description
import net.essentuan.erika.framework.formatting.JUSTIFY_LEFT
import net.essentuan.erika.framework.formatting.JUSTIFY_RIGHT
import net.essentuan.erika.framework.formatting.table
import net.essentuan.erika.kord.framework.commands.CommandInteraction
import net.essentuan.erika.kord.framework.commands.annotations.Named
import net.essentuan.erika.kord.framework.commands.annotations.Requires
import net.essentuan.erika.kord.framework.commands.annotations.Whitelist
import net.essentuan.erika.kord.framework.message.*
import net.essentuan.esl.collections.builders.list
import net.essentuan.esl.collections.enumMapOf
import net.essentuan.esl.color.Color
import net.essentuan.esl.color.McColor
import net.essentuan.esl.iteration.extensions.iterate
import net.essentuan.esl.orNull
import net.essentuan.esl.rx.*
import net.essentuan.esl.time.TimeUnit
import java.text.DecimalFormat
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.enums.enumEntries
import kotlin.math.min

private const val PLAYERS_PER_PAGE = 10
private const val RUNS_PER_PAGE = 5

@Command("raids")
@Whitelist(810258030201143328)
@Requires("ico.commands.raids")
object RaidsCommand {
    @Subcommand("rewards")
    @Description("Displays a leaderboard of who has earned the most from the event.")
    private suspend fun CommandInteraction.rewards() = pagination {
        val players = Profile {
            for ((uuid, _) in IcoEvent)
                +uuid
        }.map { (uuid, result) ->
            (IcoEvent.get(uuid as UUID) ?: return@map null) to result.orNull()
        }.filterNotNull()
            .filter { (store, _) -> store.total > 0 }
            .sortedByDescending { (store, _) -> store.total }
            .toList()

        var totalRuns = 0
        var totalRewards = 0.0

        val completions = enumMapOf<Raid.Type, Int>().apply {
            for ((store, _) in players) {
                totalRuns += store.total
                totalRewards += store.rewards

                for (raid in Raid.Type.entries)
                    compute(raid) { _, count -> (count ?: 0) + store.completions(raid) }
            }
        }

        body {
            content {
                +"```ml"
                newLine()

                it()

                newLine()
                +"```"
            }
        }

        (0..<players.split(PLAYERS_PER_PAGE)) {
            +table {
                header {
                    append("---- Rewards Leaderboard | Page ")
                    append(page + 1)
                    append(" of ")
                    append(maxPages + 1)
                    append(" ----")
                }

                column("")
                column("Player")

                for (raid in enumEntries<Raid.Type>()) {
                    column(raid.short) {
                        footer((completions[raid] ?: 0).toString())
                    }
                }

                column("Total") { footer(totalRuns.toString()) }
                column("Rewards", JUSTIFY_RIGHT) { footer("%.2f LE".format(totalRewards)) }

                val offset = page * PLAYERS_PER_PAGE

                for (i in offset..<min(players.size, offset + PLAYERS_PER_PAGE)) {
                    val (store, profile) = players[i]

                    row(
                        *list {
                            +"#${i + 1}"
                            +(profile?.name ?: "???")

                            for (raid in enumEntries<Raid.Type>())
                                +store.completions(raid).toString()

                            +store.total.toString()
                            +"%.2f LE".format(store.rewards)
                        }.toTypedArray()
                    )
                }
            }
        }
    }

    @Subcommand("overview")
    private suspend fun CommandInteraction.overview(
        @Named("Player") player: Profile
    ) {
        val user = IcoEvent.get(player.uuid)

        if (user == null) {
            message {
                embed(McColor.RED) {
                    title = "${player.name} is not in Idiot Co!"
                }
            }

            return
        }

        message {
            embed {
                colored(IcoEvent.COLOR)

                author {
                    icon =
                        "https://cdn.discordapp.com/icons/810258030201143328/04210659f67b2bbcd890c4744eed1863.webp"

                    name {
                        +"Overview of "
                        +player.name
                    }

                    url = "https://wynncraft.com/stats/player/${player.uuid}"
                }

                thumbnail {
                    url = "https://visage.surgeplay.com/bust/${player.uuid}?wynncraft-autofixed&no=ears"
                }

                for (raid in enumEntries<Raid.Type>()) {
                    val completions = user.completions(raid)

                    field {
                        name = raid.display

                        value = buildString {
                            append(completions.toString())
                            append(" completion")

                            if (completions != 1)
                                append('s')
                        }
                    }
                }

                footer {
                    icon = "https://wynncraft.wiki.gg/images/8/8c/Experience_bottle.png"

                    text {
                        +player.name
                        +" has earned "
                        +"%.2f".format(user.rewards)
                        +" LE in "
                        +user.total.toString()

                        +" raid"

                        if (user.total != 1)
                            +'s'

                        +"!"
                    }
                }
            }
        }
    }

    @Subcommand("pbs")
    private suspend fun CommandInteraction.overview(
        @Named("Player") player: Profile,
        @Named("Raid") type: Raid.Type
    ) {
        val user = IcoEvent.get(player.uuid)

        if (user == null) {
            message {
                embed(McColor.RED) {
                    title = "${player.name} is not in Idiot Co!"
                }
            }

            return
        }

        val raid = user.pb(type)

        if (raid == null) {
            message {
                embed(McColor.RED) {
                    title = "${player.name} has not completed ${type.display}!"
                }
            }

            return
        }

        message {
            embed {
                colored(IcoEvent.COLOR)

                author {
                    icon =
                        "https://cdn.discordapp.com/icons/810258030201143328/04210659f67b2bbcd890c4744eed1863.webp"

                    name {
                        +player.name
                        +"'s personal best for "
                        +raid.type.display
                    }
                }

                title {
                    +"Done in "
                    +raid.duration.print(TimeUnit.SECONDS)
                    +"!"
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

    @Subcommand("leaderboard")
    private suspend fun CommandInteraction.leaderboard(
        @Named("Raid") type: Raid.Type
    ) = pagination {
        val seen = mutableSetOf<UUID>()

        val runs = IcoEvent.asSequence()
            .map { (_, store) -> store.pb(type) }
            .filterNotNull()
            .sortedBy { it.duration }
            .filter {
                var any = false

                for (member in it.partyMembers)
                    any = any or seen.add(member)

                any
            }
            .toList()

        val players = Profile {
            for (run in runs)
                +run.partyMembers
        }.map { it.second.orNull() }
            .filterNotNull()
            .associateBy { it.uuid }

        var indices: IntRange = 0..0

        (0..<runs.split(RUNS_PER_PAGE)) {
            val offset = page * RUNS_PER_PAGE

            indices = offset..<min(runs.size, offset + RUNS_PER_PAGE)
        }

        body {
            embed {
                colored(IcoEvent.COLOR)

                author {
                    icon =
                        "https://cdn.discordapp.com/icons/810258030201143328/04210659f67b2bbcd890c4744eed1863.webp"

                    name {
                        +"Leaderboard for "
                        +type.display

                        it()
                    }
                }

                thumbnail {
                    url = type.icon
                }

                for (i in indices) {
                    val run = runs[i]

                    field {
                        name = buildString {
                            append('#')
                            append(i + 1)

                            append(" - ")

                            run.partyMembers iterate {
                                append(players[it]?.name ?: "???")

                                if (hasNext())
                                    append(", ")
                            }
                        }

                        value = buildString {
                            append(" - ")
                            append(run.duration.print(TimeUnit.SECONDS))
                        }
                    }
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
    }
}