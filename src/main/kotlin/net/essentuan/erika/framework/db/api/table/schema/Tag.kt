package net.essentuan.erika.framework.db.api.table.schema

import net.essentuan.erika.framework.db.eslKey
import kotlin.reflect.KProperty

interface Tag {
    val key: String
    val attributes: Set<Attribute>
    val indexes: Collection<Index>

    interface Builder {
        infix operator fun <T> KProperty<T>.invoke(init: Builder.() -> Unit) =
            this.eslKey.invoke(init)

        infix operator fun String.invoke(init: Builder.() -> Unit)

        fun index(init: Index.Builder.() -> Unit)

        operator fun Class<*>.unaryPlus()

        operator fun Attribute.unaryPlus()
    }
}