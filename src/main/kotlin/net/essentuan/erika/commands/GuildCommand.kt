@file:Command("guild")

package net.essentuan.erika.commands

import com.busted_moments.buster.api.Guild
import com.essentuan.acf.core.annotations.Argument
import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Default
import com.essentuan.acf.core.annotations.Subcommand
import com.essentuan.acf.core.command.arguments.builtin.primitaves.String.StringType
import com.mojang.brigadier.context.CommandContext
import net.essentuan.erika.framework.console.Str
import net.essentuan.erika.framework.console.SystemSource
import net.essentuan.erika.db.struct.guild.GuildModel.Table.external
import net.essentuan.erika.db.struct.guild.search.invoke
import net.essentuan.erika.inline
import net.essentuan.erika.observers.guilds.list.Guilds
import net.essentuan.esl.filterNotNull
import net.essentuan.esl.ifPresentOrElse
import net.essentuan.esl.map
import net.essentuan.esl.orNull
import net.essentuan.esl.rx.findFirst

@Default
fun CommandContext<SystemSource>.default(
    @Argument("guild") @StringType(Str.GREEDY_PHRASE) guild: String
) {
    inline {
        Guild(api = true, update = false) { +guild }
            .findFirst()
            .map { (_, result) -> result.orNull() }
            .filterNotNull()
            .ifPresentOrElse({ source.message("Found guild! ${it.name} [${it.tag}]: ${it.external().asString(true)}") }) {
                source.error("No guild '$guild' exists!")
            }
    }
}

@Subcommand("find")
fun CommandContext<SystemSource>.find(
    @Argument("guild") @StringType(Str.GREEDY_PHRASE) guild: String
) {
    val result = Guilds.find(
        guild,
        false
    )

    if (result != null)
        source.message("Found guild! ${result.name} [${result.tag}]")
    else
        source.error("No guild '$guild' exists!")
}