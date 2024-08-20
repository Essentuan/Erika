package net.essentuan.erika.framework.db.table.schema.tags

import net.essentuan.erika.framework.db.api.table.schema.Attribute
import net.essentuan.erika.framework.db.api.table.schema.Index
import net.essentuan.erika.framework.db.api.table.schema.Type

data class TypeImpl(
    override val key: String,
    override val attributes: Set<Attribute>,
    override val value: Class<*>,
    override val indexes: Collection<Index>
) : Type