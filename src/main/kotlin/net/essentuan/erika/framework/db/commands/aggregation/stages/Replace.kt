package net.essentuan.erika.framework.db.commands.aggregation.stages

import com.mongodb.client.model.Aggregates
import net.essentuan.erika.framework.db.commands.aggregation.Aggregation

@JvmInline
value class Replace(val aggregation: Aggregation) {
    infix fun with(value: Any?) {
        aggregation+= Aggregates.replaceRoot(value)
    }
}

/**
 * @see Aggregates.replaceRoot
 */
val Aggregation.replace: Replace
    get() = Replace(this)