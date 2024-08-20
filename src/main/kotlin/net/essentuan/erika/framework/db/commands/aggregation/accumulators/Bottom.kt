package net.essentuan.erika.framework.db.commands.aggregation.accumulators

import net.essentuan.erika.framework.db.builders.Sort
import net.essentuan.erika.framework.db.builders.bson
import net.essentuan.erika.framework.db.commands.aggregation.Accumulator
import org.bson.Document
import org.bson.conversions.Bson

inline fun Accumulator.bottom(n: Int = 1, block: Sort.() -> Any?): Bson = bson {
    val sort = Document()

    "\$bottomN" {
        "output" to Sort(sort).block()
        "sortBy" to sort
        "n" to n
    }
}