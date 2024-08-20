package net.essentuan.erika.framework.db.table

import com.mongodb.client.model.Collation
import com.mongodb.client.model.CreateCollectionOptions
import com.mongodb.client.model.IndexModel
import com.mongodb.client.model.IndexOptions
import com.mongodb.reactivestreams.client.MongoCollection
import net.essentuan.erika.framework.db.Database
import net.essentuan.erika.framework.db.api.table.Table
import net.essentuan.erika.framework.db.api.table.schema.Attribute
import net.essentuan.erika.framework.db.api.table.schema.Index
import net.essentuan.erika.framework.db.api.table.schema.Schema
import net.essentuan.erika.framework.db.api.table.schema.recurse
import net.essentuan.erika.framework.db.builders.DEFAULT_COLLATION
import net.essentuan.erika.framework.db.table.adapters.NoAdapter
import net.essentuan.erika.framework.db.table.schema.Root
import net.essentuan.erika.framework.db.table.schema.TagBuilder
import net.essentuan.esl.coroutines.blocking
import net.essentuan.esl.reflections.extensions.typeInformationOf
import net.essentuan.esl.rx.discard
import net.essentuan.esl.time.NativeUnit
import org.bson.Document

open class TableImpl<T>(
    name: String,
    adapter: Table.Adapter<T>,
    size: Long = 0,
    capacity: Long = -1,
    collation: Collation? = null,
    override val schema: Schema = Root()
) : Table<T> {
    final override var name: String = name
        protected set

    final override var size: Long = size
        protected set

    final override var capacity: Long = capacity
        protected set

    override var collation: Collation = collation ?: DEFAULT_COLLATION
        protected set

    override val adapter: Table.Adapter<T> = if (adapter !is NoAdapter) adapter else ImplicitAdapter()

    override val type: Class<T> = this.adapter.type

    override val mongo: MongoCollection<Document> by lazy<MongoCollection<Document>> {
        if (this.name !in Database)
            create()

        Database.connection.getCollection(this.name)
    }

    init {
        (schema as Root).map["_id"] = TagBuilder("_id").apply {
            index {
                +Index.Type.ASCENDING
            }

            +Attribute.UNIQUE
        }.build()
    }

    protected open fun options() = CreateCollectionOptions()

    private fun create() {
        val options = options()

        if (collation != DEFAULT_COLLATION)
            options.collation(collation)

        if (capacity != -1L) {
            options.capped(true)
            options.maxDocuments(capacity)
        }

        blocking {
            Database.connection.createCollection(name, options).discard()

            val indexes = schema.recurse()
                .filterNot { (key, _) -> key == "_id" }
                .flatMap { (key, tag) ->
                    tag.indexes.flatMap { index ->
                        val options = IndexOptions()

                        when {
                            Attribute.UNIQUE in tag.attributes -> options.unique(true)
                            index.expiry != null -> options.expireAfter(
                                index.expiry!!.toSeconds().toLong(),
                                NativeUnit.SECONDS
                            )
                        }

                        index.types.map { IndexModel(it(key), options) }
                    }
                }.toList()

            if (indexes.isNotEmpty())
                Database.connection
                    .getCollection(name)
                    .createIndexes(indexes)
                    .discard()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <U> with(adapter: Table.Adapter<U>): Table<U> {
        if (adapter.type == type)
            return this as Table<U>

        return Adapted(adapter)
    }

    private inner class Adapted<U>(
        override val adapter: Table.Adapter<U>
    ) : Table<U> {
        override val name: String
            get() = this@TableImpl.name
        override val size: Long
            get() = this@TableImpl.size
        override val capacity: Long
            get() = this@TableImpl.capacity
        override val collation: Collation
            get() = this@TableImpl.collation
        override val schema: Schema
            get() = this@TableImpl.schema
        override val mongo: MongoCollection<Document>
            get() = this@TableImpl.mongo

        override val type: Class<U> = adapter.type

        override fun <U> with(adapter: Table.Adapter<U>): Table<U> = this@TableImpl.with(adapter)
    }

    inner class ImplicitAdapter : Table.Adapter<T> {
        @Suppress("UNCHECKED_CAST")
        val adapter: Table.Adapter<T> = Table.Adapter(
            requireNotNull(this@TableImpl.typeInformationOf(Table::class)["T"]) as Class<T>
        )
        override val type: Class<T>
            get() = adapter.type

        override fun read(doc: Document): T =
            adapter.read(doc)

        override fun write(obj: T): Document =
            adapter.write(obj)
    }
}