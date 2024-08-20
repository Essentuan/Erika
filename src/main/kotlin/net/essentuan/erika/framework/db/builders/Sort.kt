package net.essentuan.erika.framework.db.builders

import net.essentuan.erika.framework.db.eslKey
import org.bson.Document
import org.bson.conversions.Bson
import kotlin.reflect.KProperty

@JvmInline
value class Sort(
    val doc: Document = Document()
) {
    inline operator fun String.invoke(block: Sort.() -> Unit) {
        for ((key, sort) in Sort().apply(block).doc)
            doc["$this.$key"] = sort
    }

    inline operator fun KProperty<*>.invoke(crossinline block: Sort.() -> Unit) {
        eslKey(block)
    }

    /**
     * Sorts with the given key in ascending order
     */
    operator fun String.unaryPlus() {
        doc[this] = 1
    }

    /**
     * Sorts with the given key in ascending order
     */
    operator fun KProperty<*>.unaryPlus() = +eslKey

    /**
     * Sorts with the given key in descending order
     */
    operator fun String.unaryMinus() {
        doc[this] = -1
    }

    /**
     * Sorts with the given key in descending order
     */
    operator fun KProperty<*>.unaryMinus() = -eslKey

    fun build(): Bson =
        doc
}