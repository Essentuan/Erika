@file:Command("memory")

package net.essentuan.erika.commands

import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Subcommand
import com.mojang.brigadier.context.CommandContext
import com.sun.management.HotSpotDiagnosticMXBean
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.db.`object`.Memory
import net.essentuan.erika.framework.formatting.table
import net.essentuan.esl.reflections.extensions.simpleString
import java.lang.management.ManagementFactory

var MEMORY_DEBUG_ENABLED: Boolean = false

@Subcommand("debug")
private fun CommandContext<*>.debug() {
    MEMORY_DEBUG_ENABLED = !MEMORY_DEBUG_ENABLED
}

@Subcommand("usage")
private fun CommandContext<*>.usage() {
    val runtime = Runtime.getRuntime()

    val max = runtime.maxMemory()
    val used = runtime.totalMemory()
    val free = runtime.freeMemory()

    Logging.info("Heap: ${used - free}/${used}")
    Logging.info("Memory usage: $used/${max} (${(used / max.toDouble()) * 100})")
}

@Subcommand("counts")
private fun CommandContext<*>.counts() {
    Logging.info("\nTotal object counts: \n${
        table { 
            val counts = Memory.counts()
            
            column("Name")
            column("Count") {
                footer {
                    +"Total: "
                    +counts.values.sum().toString()
                }
            }
            
            for (entry in counts)
                row(entry.key.simpleString(), entry.value.toString())
        }
    }")
}

@Subcommand("gc")
private fun CommandContext<*>.gc() {
    Runtime.getRuntime().gc()
}

@Subcommand("dump")
private fun CommandContext<*>.dump() {
    Logging.info("Dumping heap")
    Logging.info("Acquiring MBeanServer")

    val server = ManagementFactory.getPlatformMBeanServer()

    Logging.info("Creating dump")

    try {
        val file = "./heap-${System.currentTimeMillis()}.hprof"

        ManagementFactory.newPlatformMXBeanProxy(
            server,
            "com.sun.management:type=HotSpotDiagnostic",
            HotSpotDiagnosticMXBean::class.java
        ).dumpHeap(
            file,
            true
        )

        Logging.info("Heap dump has been created at $file!")
    } catch (ex: Exception) {
        Logging.error("Error creating dump!", ex)
    }
}