@file:Command("task")

package net.essentuan.erika.commands

import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Subcommand
import com.mojang.brigadier.context.CommandContext
import io.ktor.utils.io.*
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.formatting.tree
import net.essentuan.esl.Result
import net.essentuan.esl.other.lock
import net.essentuan.esl.result
import net.essentuan.esl.scheduling.Scheduler
import net.essentuan.esl.scheduling.api.Task
import net.essentuan.esl.time.duration.FormatFlag

@Subcommand("list")
private fun CommandContext<*>.on() {
    Logging.info('\n' + tree<Any>({
        when (this) {
            is Scheduler -> this.lock { map { it.result() } }
            is Task.Group -> this.lock { map { it.result() } }
            is Task -> this.lock { toList() }

            else -> emptyList()
        }
    }, Scheduler.result()) {
        when (this) {
            is Scheduler -> it.append("Scheduler")
            is Task.Group -> it.append(name)
            is Task -> {
                if (suspended)
                    it.append('*')

                it.append(id)

                it.append(" | ")

                it.append("Every ")
                it.append(rate.print(FormatFlag.COMPACT))

                it.append(" | ")

                it.append("TTL-")
                it.append(lifetime.print(FormatFlag.MINIFIED))

                it.append(" | ")

                it.append(size)
                it.append("/")
                it.append(capacity)

                it.append(" Worker")

                if (size != 1)
                    it.append("s")
            }


            is Task.Worker -> {
                it.append("Worker #")
                it.append(this.id)
                it.append(" - ")
                it.append(this.age.print(FormatFlag.COMPACT))
            }
        }
    })
}