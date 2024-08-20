package net.essentuan.erika.framework.db.api.table.schema

import net.essentuan.erika.framework.db.eslKey
import kotlin.reflect.KProperty

interface Schema : Map<String, Tag>, Tag, Tag.Builder {
    fun interface Builder {
        infix operator fun String.invoke(init: Tag.Builder.() -> Unit)

        infix operator fun <T> KProperty<T>.invoke(init: Tag.Builder.() -> Unit) =
            this.eslKey.invoke(init)
    }
}

fun Schema.recurse(root: String = ""): Sequence<Pair<String, Tag>> = sequence {
    for ((key, value) in this@recurse) {
        yield("$root$key" to value)

        if (value is Schema)
            yieldAll(value.recurse("$key."))
    }
}