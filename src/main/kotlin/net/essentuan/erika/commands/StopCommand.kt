@file:Command("stop")

package net.essentuan.erika.commands

import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Default
import com.mojang.brigadier.context.CommandContext
import net.essentuan.erika.framework.console.Logging
import kotlin.system.exitProcess

@Default
fun CommandContext<*>.default() {
   Logging.warn("Stopping!")
   exitProcess(0)
}