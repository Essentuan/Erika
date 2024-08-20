package net.essentuan.erika.framework.db.table.schema.tags

import net.essentuan.erika.framework.db.api.table.schema.Attribute
import net.essentuan.erika.framework.db.api.table.schema.Index
import net.essentuan.erika.framework.db.table.schema.AbstractSchema
import net.essentuan.erika.framework.db.table.schema.IndexImpl
import net.essentuan.esl.other.unsupported

class SchemaImpl(
    override val key: String,
    override val attributes: MutableSet<Attribute>,
    override val indexes: MutableCollection<Index>
) : AbstractSchema() {
    override fun index(init: Index.Builder.() -> Unit) {
        indexes.add(IndexImpl.Builder(key).apply(init).build())
    }

    override fun Class<*>.unaryPlus() = unsupported()

    override fun Attribute.unaryPlus() {
        attributes.add(this)
    }
}