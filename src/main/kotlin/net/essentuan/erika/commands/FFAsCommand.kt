@file:Command("ffas")

package net.essentuan.erika.commands

import com.essentuan.acf.core.annotations.Argument
import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Subcommand
import com.essentuan.acf.core.command.arguments.builtin.primitaves.String.StringType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.essentuan.erika.buster.FFAList
import net.essentuan.erika.framework.console.Logging
import net.essentuan.esl.other.lock

@Subcommand("add")
fun CommandContext<*>.add(
    @StringType(StringArgumentType.StringType.GREEDY_PHRASE) @Argument("territory") territory: String
) {
    FFAList.lock { add(territory) }
}

@Subcommand("remove")
fun CommandContext<*>.remove(
    @StringType(StringArgumentType.StringType.GREEDY_PHRASE) @Argument("territory") territory: String
) {
    FFAList.lock { remove(territory) }
}

@Subcommand("list")
fun CommandContext<*>.list() {
    Logging.info(FFAList.lock { joinToString() })
}

@Subcommand("clear")
fun CommandContext<*>.clear() {
    FFAList.lock { clear() }
}

@Subcommand("clear")
fun CommandContext<*>.update() {
    FFAList.update()
}