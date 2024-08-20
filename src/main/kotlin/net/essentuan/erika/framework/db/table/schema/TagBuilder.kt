package net.essentuan.erika.framework.db.table.schema

import net.essentuan.erika.framework.db.api.table.schema.Attribute
import net.essentuan.erika.framework.db.api.table.schema.Index
import net.essentuan.erika.framework.db.api.table.schema.Schema
import net.essentuan.erika.framework.db.api.table.schema.Tag
import net.essentuan.erika.framework.db.eslKey
import net.essentuan.erika.framework.db.table.schema.tags.SchemaImpl
import net.essentuan.erika.framework.db.table.schema.tags.TypeImpl
import java.util.EnumSet
import kotlin.reflect.KProperty

class TagBuilder(val key: String) : Tag.Builder, Schema.Builder {
    val attributes: MutableSet<Attribute> = EnumSet.noneOf(Attribute::class.java)
    val indexes: MutableList<Index> = mutableListOf()
    val schema: SchemaImpl = SchemaImpl(key, attributes, indexes)

    var value: Class<*>? = null

    override fun <T> KProperty<T>.invoke(init: Tag.Builder.() -> Unit) =
        eslKey.invoke(init)

    override fun String.invoke(init: Tag.Builder.() -> Unit) {
        require(value == null) {
            "Cannot declare schema for type!"
        }

        schema.map[this] = TagBuilder(this).apply(init).build()
    }

    override fun index(init: Index.Builder.() -> Unit) {
        indexes.add(IndexImpl.Builder(key).apply(init).build())
    }

    override fun Class<*>.unaryPlus() {
        require(schema.isEmpty()) {
            "Cannot declare type for schema!"
        }

        value = this
    }

    override fun Attribute.unaryPlus() {
        attributes.add(this)
    }

    fun build(): Tag {
        return value?.let { TypeImpl(key, attributes, it, indexes) } ?: schema
    }
}