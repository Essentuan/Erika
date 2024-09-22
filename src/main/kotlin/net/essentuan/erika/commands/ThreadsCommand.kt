@file:Command("threads")

package net.essentuan.erika.commands

import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Default
import com.essentuan.acf.core.annotations.Subcommand
import com.mojang.brigadier.context.CommandContext
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.formatting.tree
import net.essentuan.esl.other.thread

private val Thread.root: ThreadGroup
    get() {
        var root = threadGroup

        while (root.parent != null)
            root = root.parent

        return root
    }

private fun dump(stacktrace: Boolean): String {
    return tree<Any>({
        when (this) {
            is ThreadGroup -> {
                val threads = arrayOfNulls<Thread>(this.activeCount())
                enumerate(threads, false)

                val groups = arrayOfNulls<ThreadGroup>(activeGroupCount())
                enumerate(groups, false)

                val out = mutableListOf<Any>()

                for (thread in threads) {
                    if (thread != null)
                        out += thread
                }

                for (group in groups) {
                    if (group != null)
                        out += group
                }

                out
            }

            is Thread -> if (stacktrace) stackTrace.asList() else emptyList()

            else -> emptyList()
        }
    }, thread().root) {
        when (this) {
            is ThreadGroup -> it.append(name)
            is Thread -> {
                it.append(name)

                if (isDaemon)
                    it.append(" (Daemon)")

                it.append(" | ")
                it.append(state)
            }

            is StackTraceElement -> it.append(toString())
        }
    }
}

@Default
private fun CommandContext<*>.threads() {
    Logging.info('\n' + dump(false))
}

@Subcommand("stacktrace")
private fun CommandContext<*>.stacktrace() {
    Logging.info('\n' + dump(true))
}
