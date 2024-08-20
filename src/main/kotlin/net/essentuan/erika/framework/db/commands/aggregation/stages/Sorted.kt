package net.essentuan.erika.framework.db.commands.aggregation.stages

import com.mongodb.client.model.Aggregates
import net.essentuan.erika.framework.db.builders.Sort
import net.essentuan.erika.framework.db.builders.expression.Expr
import net.essentuan.erika.framework.db.commands.aggregation.Aggregation

@JvmInline
value class Sorted(
    val value: Aggregation
) {
    /**
     * @see Aggregates.sortByCount
     */
    inline infix fun by(block: Expr.() -> Any?) {
        value+= Aggregates.sortByCount(Expr.run(block))
    }
}

/**
 * @see Aggregates.sort
 */
inline fun Aggregation.sort(block: Sort.() -> Unit) {
    this += Aggregates.sort(Sort().apply(block).build())
}

/**
 * @see Aggregates.sortByCount
 */
val Aggregation.sort: Sorted
    get() = Sorted(this)

