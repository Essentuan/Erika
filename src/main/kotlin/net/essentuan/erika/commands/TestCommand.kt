@file:Command("table")

package net.essentuan.erika.commands

import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Default
import com.mojang.brigadier.context.CommandContext
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.formatting.JUSTIFY_RIGHT
import net.essentuan.erika.framework.formatting.table
import kotlin.random.Random

@Default
private fun CommandContext<*>.table() {
    Logging.info(
        '\n' + table {
            column("Col #1") {
                for (i in 0..5)
                    row(Random.nextInt().toString())
            }

            column("Col #2") {
                for (i in 0..5)
                    row(Random.nextLong().toString())
            }

            column("Col #3") {
                for (i in 0..5)
                    row(Random.nextFloat().toString())
            }

            column("Col #3") {
                var sum: Double = 0.0

                for (i in 0..5) {
                    val double = Random.nextDouble()
                    sum+= double

                    row(double.toString())
                }

                footer(sum.toString(), justify = JUSTIFY_RIGHT)
            }
        }
    )
}

