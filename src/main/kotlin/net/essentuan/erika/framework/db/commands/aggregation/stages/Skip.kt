package net.essentuan.erika.framework.db.commands.aggregation.stages

import com.mongodb.client.model.Aggregates
import net.essentuan.erika.framework.db.commands.aggregation.Aggregation

@JvmInline
value class Skip(val value: Aggregation) {
    /**
     * @see Aggregates.skip
     */
    infix fun past(int: Int) {
        value+= Aggregates.skip(int)
    }
}

/**
 * @see Aggregates.skip
 */
val Aggregation.skip: Skip
    get() = Skip(this)
