package net.essentuan.erika.framework.db.commands.aggregation.stages

import com.mongodb.client.model.Aggregates
import net.essentuan.erika.framework.db.builders.Filter
import net.essentuan.erika.framework.db.commands.aggregation.Aggregation

/**
 * @see Aggregates.match
 */
inline fun Aggregation.match(block: Filter.() -> Unit) {
    this += Aggregates.match(Filter().apply(block))
}