@file:Command("java")

package net.essentuan.erika.commands

import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Default
import com.mojang.brigadier.context.CommandContext
import net.essentuan.erika.framework.console.Logging

@Default
private fun CommandContext<*>.java() {
    Logging.info("${Runtime.version()} - ${System.getProperty("java.vm.vendor")}")
}