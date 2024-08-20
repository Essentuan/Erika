package net.essentuan.erika.framework.db.commands.aggregation.stages

import com.mongodb.client.model.Aggregates
import net.essentuan.erika.framework.db.commands.aggregation.Aggregation
import net.essentuan.erika.framework.db.eslKey
import kotlin.reflect.KProperty

@JvmInline
value class Count(
    val aggregation: Aggregation
) {
    fun to(key: String) {
        aggregation+= Aggregates.count(key)
    }

    fun to(key: KProperty<*>) {
        aggregation+= Aggregates.count(key.eslKey)
    }
}

/**
 * @see Aggregates.count
 */
val Aggregation.count: Count
    get() = Count(this)