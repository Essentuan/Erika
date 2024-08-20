package net.essentuan.erika.framework.db.commands.aggregation

import com.mongodb.reactivestreams.client.AggregatePublisher
import net.essentuan.erika.framework.db.api.table.Table
import net.essentuan.erika.framework.db.populate
import net.essentuan.esl.rx.map
import org.bson.Document
import org.bson.conversions.Bson
import org.reactivestreams.Publisher

interface Aggregation : Iterable<Bson> {
    operator fun plusAssign(bson: Bson)

    class Query<T>(
        var table: Table<*>? = null
    ) : Aggregation {
        private val stages: MutableList<Bson> = mutableListOf()
        lateinit var finally: suspend (Document) -> T

        override operator fun plusAssign(bson: Bson) {
            require(_publisher == null) { "Cannot add stage after publisher has been created!" }

            stages += bson
        }

        val select: Select
            get() = Select(this)

        private var _publisher: AggregatePublisher<Document>? = null
        val publisher: AggregatePublisher<Document>
            get() {
                if (_publisher == null) {
                    requireNotNull(table) { "Must select a table!" }

                    _publisher = table!!.mongo.aggregate(stages)
                }

                return _publisher!!
            }

        fun finally(block: suspend (Document) -> T) {
            finally = block
        }

        override fun iterator(): Iterator<Bson> =
            stages.iterator()
    }
}

@JvmInline
value class Select(val aggregation: Aggregation.Query<*>) {
    infix fun from(table: Table<*>) {
        aggregation.table = table
    }
}

inline fun <T> aggregate(populate: Boolean = true, block: Aggregation.Query<T>.() -> Unit): Publisher<T> {
    return Aggregation.Query<T>()
        .run outer@{
            block()

            publisher.run {
                if (populate)
                    populate()
                else
                    this
            }.map(this@outer.finally)
        }
}
