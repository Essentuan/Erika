package net.essentuan.erika.framework.db.commands.aggregation.stages

import net.essentuan.erika.framework.db.commands.aggregation.Accumulator
import net.essentuan.erika.framework.db.commands.aggregation.Aggregation
import net.essentuan.erika.framework.db.eslKey
import org.bson.Document
import kotlin.reflect.KProperty

@JvmInline
value class Group(
    val doc: Document
) {
    inline fun id(block: Accumulator.() -> Any?) {
        doc["_id"] = Accumulator.block()
    }

    inline fun by(block: Accumulator.() -> Any?) {
        doc["_id"] = Accumulator.block()
    }

    fun String.to(value: Any?) {
        doc[this] = value
    }

    fun KProperty<*>.to(value: Any?) {
        doc[this.eslKey] = value
    }

    inline fun String.to(block: Accumulator.() -> Any?) {
        doc[this] to Accumulator.block()
    }

    inline fun KProperty<*>.to(block: Accumulator.() -> Any?) {
        doc[this.eslKey] to Accumulator.block()
    }

    inline operator fun String.invoke(block: Group.() -> Unit) {
        doc[this] = Group(Document()).apply(block).doc
    }

    inline operator fun KProperty<*>.invoke(block: Group.() -> Unit) {
        doc[this.eslKey] = Group(Document()).apply(block).doc
    }
}

/**
 * @see com.mongodb.client.model.Aggregates.group
 */
inline fun Aggregation.group(block: Group.() -> Unit) {
    this+= Document().apply { set("\$group", Group(Document()).apply(block).doc) }
}