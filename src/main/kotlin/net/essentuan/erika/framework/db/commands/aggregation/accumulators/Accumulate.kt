package net.essentuan.erika.framework.db.commands.aggregation.accumulators

import net.essentuan.erika.framework.db.builders.bson
import net.essentuan.erika.framework.db.commands.aggregation.Accumulator
import org.bson.conversions.Bson
import kotlin.apply
import kotlin.collections.isNotEmpty
import kotlin.to

class Accumulate {
    lateinit var init: String
    lateinit var initArgs: Array<out Any?>

    lateinit var accumulate: String
    lateinit var accumulateArgs: Array<out Any?>

    lateinit var merge: String
    var finialize: String? = null

    inline fun init(vararg args: Any?, block: () -> String) {
        this.init = block()
        this.initArgs = args
    }

    inline fun accumulate(vararg args: Any?, block: () -> String) {
        this.accumulate = block()
        this.accumulateArgs = args
    }

    inline fun merge(block: () -> String) {
        this.merge = block()
    }

    inline fun finialize(block: () -> String) {
        this.finialize = block()
    }

    fun build(): Bson = bson {
        "init" to init
        if (initArgs.isNotEmpty())
            "initArgs" to initArgs

        "accumulate" to accumulate
        if (accumulateArgs.isNotEmpty())
            "accumulateArgs" to accumulateArgs

        "merge" to merge

        if (finialize != null)
            "finalize" to finialize
    }
}

inline fun Accumulator.accumulate(block: Accumulate.() -> Unit): Bson =
    Accumulate().apply(block).build()