package net.essentuan.erika.framework.db.commands.aggregation.stages

import com.mongodb.client.model.Aggregates
import net.essentuan.erika.framework.db.builders.Projection
import net.essentuan.erika.framework.db.commands.aggregation.Aggregation

/**
 * @see Aggregates.project
 */
inline fun Aggregation.project(block: Projection.() -> Unit) {
    this += Aggregates.project(Projection().apply(block).build())
}