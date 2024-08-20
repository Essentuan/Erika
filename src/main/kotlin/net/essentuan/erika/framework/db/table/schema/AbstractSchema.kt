package net.essentuan.erika.framework.db.table.schema

import net.essentuan.erika.framework.db.api.table.schema.Schema
import net.essentuan.erika.framework.db.api.table.schema.Tag
import net.essentuan.erika.framework.db.table.schema.tags.SchemaImpl
import net.essentuan.esl.comparing.equals
import net.essentuan.esl.iteration.extensions.mutable.iterate
import net.essentuan.esl.other.Repr
import net.essentuan.esl.other.repr

abstract class AbstractSchema(val map: MutableMap<String, Tag> = mutableMapOf()) : Schema, Map<String, Tag> by map {
    override fun String.invoke(init: Tag.Builder.() -> Unit) {
        map[this] = TagBuilder(this).apply(init).build()
    }

    override fun equals(other: Any?): Boolean = equals<AbstractSchema>(other) {
        +AbstractSchema::map

        if (this@AbstractSchema is SchemaImpl && other is SchemaImpl) {
            +SchemaImpl::key
        }
    }

    override fun hashCode(): Int {
        return map.hashCode()
    }

    override fun toString(): String = repr {
        prefix(Schema::class)

        with { Repr.Type.CURLY_BRACKETS }

        map.entries iterate {
            it.key to it.value
        }
    }
}