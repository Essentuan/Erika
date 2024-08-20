package net.essentuan.erika.framework.db.table.schema

import net.essentuan.erika.framework.db.api.table.schema.Attribute
import net.essentuan.erika.framework.db.api.table.schema.Index
import net.essentuan.esl.other.unsupported

class Root : AbstractSchema() {
    override val key: String
        get() = unsupported()
    override val attributes: Set<Attribute>
        get() = emptySet()
    override val indexes: Collection<Index>
        get() = emptyList()

    override fun index(init: Index.Builder.() -> Unit) = unsupported()

    override fun Class<*>.unaryPlus() = unsupported()

    override fun Attribute.unaryPlus() = unsupported()
}