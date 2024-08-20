package net.essentuan.erika.framework.db.commands.aggregation.stages

import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Field
import net.essentuan.erika.framework.db.commands.aggregation.Accumulator
import net.essentuan.erika.framework.db.commands.aggregation.Aggregation
import net.essentuan.erika.framework.db.eslKey
import kotlin.reflect.KProperty

@JvmInline
value class Fields(
    val accept: (String, Any?) -> Unit
) {
    inline fun String.to(expr: Accumulator.() -> Any?) =
        this.to(Accumulator.run(expr))

    inline fun KProperty<*>.to(expr: Accumulator.() -> Any?) =
        this.eslKey.to(Accumulator.run(expr))

    fun String.to(any: Any?) =
        accept(this, any)

    fun KProperty<*>.to(any: Any?) =
        accept(this.eslKey, any)

    inline operator fun String.invoke(block: Fields.() -> Unit) {
        Fields { key, value -> this@Fields.accept("$this.$key", value) }
    }

    operator fun KProperty<*>.invoke(block: Fields.() -> Unit) {
        Fields { key, value -> this@Fields.accept("${this.eslKey}.$key", value) }
    }
}

/**
 * @see Aggregates.addFields
 */
inline fun Aggregation.add(block: Fields.() -> Unit) {
    val list = mutableListOf<Field<*>>()

    this+= Fields { key, value -> list += Field(key, value) }.run {
        block()

        Aggregates.addFields(list)
    }
}

/**
 * @see Aggregates.set
 */
inline fun Aggregation.set(crossinline block: Fields.() -> Unit) =
    add(block)
