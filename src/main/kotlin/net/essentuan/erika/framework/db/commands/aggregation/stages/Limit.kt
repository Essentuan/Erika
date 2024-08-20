package net.essentuan.erika.framework.db.commands.aggregation.stages

import com.mongodb.client.model.Aggregates
import net.essentuan.erika.framework.db.commands.aggregation.Aggregation

@JvmInline
value class Limit(val value: Aggregation) {
    /**
     * @see Aggregates.limit
     */
    infix fun to(int: Int) {
        value+= Aggregates.limit(int)
    }
}

/**
 * @see Aggregates.limit
 */
val Aggregation.limit: Limit
    get() = Limit(this)