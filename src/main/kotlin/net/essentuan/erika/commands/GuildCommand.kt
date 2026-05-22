@file:Command("guild")

package net.essentuan.erika.commands

import com.busted_moments.buster.api.Guild
import com.busted_moments.buster.api.Profile
import com.essentuan.acf.core.annotations.Argument
import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Default
import com.essentuan.acf.core.annotations.Subcommand
import com.essentuan.acf.core.command.arguments.builtin.primitaves.String.StringType
import com.mojang.brigadier.context.CommandContext
import net.essentuan.erika.framework.console.Str
import net.essentuan.erika.framework.console.SystemSource
import net.essentuan.erika.db.struct.guild.GuildModel.Table.external
import net.essentuan.erika.db.struct.guild.member.search.invoke
import net.essentuan.erika.db.struct.guild.search.invoke
import net.essentuan.erika.db.struct.player.search.invoke
import net.essentuan.erika.inline
import net.essentuan.erika.observers.guilds.list.Guilds
import net.essentuan.esl.*
import net.essentuan.esl.rx.findFirst
import net.essentuan.esl.rx.first

@Default
fun CommandContext<SystemSource>.default(
    @Argument("guild") @StringType(Str.GREEDY_PHRASE) guild: String
) {
    inline {
        Guild(api = true, update = false) { +guild }
            .findFirst()
            .map { (_, result) -> result.orNull() }
            .filterNotNull()
            .ifPresentOrElse({
                source.message(
                    "Found guild! ${it.name} [${it.tag}]: ${
                        it.external().asString(true)
                    }"
                )
            }) {
                source.error("No guild '$guild' exists!")
            }
    }
}

@Subcommand("find")
fun CommandContext<SystemSource>.find(
    @Argument("guild") @StringType(Str.GREEDY_PHRASE) guild: String
) {
    val result = Guilds.find(guild)

    if (result != null)
        source.message("Found guild! ${result.name} [${result.tag}]")
    else
        source.error("No guild '$guild' exists!")
}

@Subcommand("player")
fun CommandContext<SystemSource>.player(
    @Argument("player") @StringType(Str.SINGLE_WORD) playerName: String,
    @Argument("guild") @StringType(Str.GREEDY_PHRASE) guildName: String
) {
    inline {
        val result = Guilds.find(guildName)

        if (result != null)
            source.message("Found guild! ${result.name} [${result.tag}]")
        else {
            source.error("No guild '$guildName' exists!")
            return@inline
        }

        val guild = Guild(result.uuid)
            .first()
            .second
            .orNull()

        if (guild == null) {
            source.error("Guild for $guildName was null")
            return@inline
        }

        val player = Guild.Member(playerName)
            .first()
            .second
            .orNull()

        if (player == null) {
            source.error("No player '$playerName' exists!")
            return@inline
        }

        if (player.guild?.uuid != guild.uuid)
            source.error("the guild of '$playerName' is ${player.guild?.name ?: "<unknown>"} not $guildName")

        if (player !in guild) {
            source.error("'$playerName' is not in '$guildName'")
            return@inline
        }

        val member = guild[player]
        if (member == null) {
            source.error("$playerName was not found in $guildName")
            return@inline
        }

        if (member.guild?.uuid != guild.uuid)
            source.error("'$playerName' member object of guild has ${member.guild?.name ?: "<unknown>"} as its guild")
    }
}