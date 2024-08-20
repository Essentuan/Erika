package net.essentuan.erika.framework.db.table

import net.essentuan.erika.framework.db.api.table.Table
import net.essentuan.erika.framework.db.api.table.schema.Schema
import net.essentuan.erika.framework.db.builders.CollationBuilder
import net.essentuan.erika.framework.db.table.adapters.NoAdapter
import net.essentuan.erika.framework.db.table.schema.Root
import net.essentuan.erika.framework.db.table.schema.TagBuilder
import net.essentuan.esl.reflections.extensions.simpleString
import net.essentuan.esl.string.reader.consume

open class StandardTable<T>(adapter: Table.Adapter<T> = NoAdapter()) : TableImpl<T>(
    "",
    adapter
) {
    init {
        this.name = nameOf(this.javaClass)
    }

    protected fun collation(init: CollationBuilder.() -> Unit) {
        collation = CollationBuilder().apply(init).build()
    }

    protected fun schema(init: Schema.Builder.() -> Unit) {
        Schema.Builder {
            (schema as Root).map[this] = TagBuilder(this).apply(it).build()
        }.apply(init)
    }

    companion object {
        private val cache = mutableMapOf<Class<*>, String>()

        fun nameOf(cls: Class<*>): String = cache.computeIfAbsent(cls) { k ->
            val reader = k.simpleString().consume()
            val result = StringBuilder()

            while (reader.canRead()) {
                if (reader.peek() == '_')
                    reader.skip()

                var first = true

                val part = reader.readUntil(map = Char::lowercaseChar, skip = { it == '.' }) {
                    if (first) {
                        first = false
                        return@readUntil false
                    }

                    it.isUpperCase() || it == '_'
                }

                if (part == "table")
                    break

                if (result.isNotEmpty())
                    result.append('_')

                result.append(part)
            }

            return@computeIfAbsent result.toString().removeSuffix("_model")
        }
    }
}