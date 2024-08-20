@file:Command("buster")

package net.essentuan.erika.commands

import com.essentuan.acf.core.annotations.Argument
import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Subcommand
import com.mojang.brigadier.context.CommandContext
import net.essentuan.erika.buster.BusterService
import net.essentuan.erika.buster.BusterService.Constants.trustedCutoff
import net.essentuan.erika.framework.console.Logging

@Subcommand("trusted")
fun CommandContext<*>.trusted(
    @Argument("cutoff") cutoff: Int
) {
    trustedCutoff = cutoff
}

@Subcommand("sessions")
fun CommandContext<*>.sessions() {
    Logging.info("There are currently ${BusterService.sockets.size} sockets connected to Buster!")
}

