package net.essentuan.erika.framework.db.commands

import com.mongodb.CursorType
import com.mongodb.reactivestreams.client.FindPublisher
import net.essentuan.erika.framework.db.api.table.Table
import net.essentuan.erika.framework.db.builders.CollationBuilder
import net.essentuan.erika.framework.db.builders.Filter
import net.essentuan.erika.framework.db.builders.Let
import net.essentuan.erika.framework.db.builders.Projection
import net.essentuan.erika.framework.db.builders.Sort
import net.essentuan.erika.framework.db.eslKey
import net.essentuan.erika.framework.db.populate
import net.essentuan.esl.rx.map
import net.essentuan.esl.time.NativeUnit
import net.essentuan.esl.time.duration.Duration
import org.bson.Document
import org.reactivestreams.Publisher
import kotlin.reflect.KProperty

typealias Ordering = Pair<String, Int>

class Find<T>(
    var publisher: FindPublisher<Document>? = null,
    var table: Table<T>? = null
) {
    val select
        get() = Select(this)
    val limit
        get() = Limit(this)
    val skip
        get() = Skip(this)
    val expire
        get() = Expire(this)
    val wait
        get() = Wait(this)

    fun publisher(): FindPublisher<Document> = requireNotNull(publisher) {
        "Must select a table!"
    }

    inline fun where(block: Filter.() -> Unit) {
        publisher().filter(Filter().apply(block))
    }

    inline fun project(block: Projection.() -> Unit) {
        publisher().projection(Projection().apply(block).build())
    }
    
    inline fun sort(block: Sort.() -> Unit) {
        publisher().sort(Sort().apply(block).build())
    }

    inline fun let(block: Let.() -> Unit) {
        val variables = Document()
        Let(variables::set).block()

        publisher().let(variables)
    }

    inline fun collation(block: CollationBuilder.() -> Unit) {
        publisher().collation(CollationBuilder().apply(block).build())
    }

    inline fun with(block: With<T>.() -> Unit) {
        With(this).apply(block)
    }

    operator fun String.unaryPlus(): Ordering = Ordering(this, 1)

    operator fun KProperty<*>.unaryPlus() = +eslKey

    operator fun String.unaryMinus(): Ordering = Ordering(this, -1)

    operator fun KProperty<*>.unaryMinus() = -eslKey
}

@JvmInline
value class Limit<T>(val find: Find<T>) {
    infix fun to(int: Int) {
        find.publisher().limit(int)
    }
}

@JvmInline
value class Skip<T>(val find: Find<T>) {
    infix fun past(int: Int) {
        find.publisher().skip(int)
    }
}

@JvmInline
value class Expire<T>(val find: Find<T>) {
    infix fun after(duration: Duration) {
        find.publisher().maxTime(duration.toMills().toLong(), NativeUnit.MILLISECONDS)
    }

}

@JvmInline
value class Wait<T>(val find: Find<T>) {
    infix fun until(duration: Duration) {
        find.publisher().maxAwaitTime(duration.toMills().toLong(), NativeUnit.MILLISECONDS)
    }
}

@JvmInline
value class Select<T>(val find: Find<T>) {
    infix fun from(table: Table<T>) {
        find.table = table

        find.publisher = table.mongo.find()
    }
}

@JvmInline
value class With<T>(val find: Find<T>) {
    fun noCursorTimeout(enabled: Boolean = true) {
        find.publisher().noCursorTimeout(enabled)
    }

    infix fun cursor(cursor: CursorType) {
        find.publisher().cursorType(cursor)
    }

    fun diskUse(enabled: Boolean = true) {
        find.publisher().allowDiskUse(enabled)
    }

    fun batchSize(size: Int) {
        find.publisher().batchSize(size)
    }
}

inline fun <T> find(populate: Boolean = true, block: Find<T>.() -> Unit): Publisher<T> =
    Find<T>()
        .apply(block)
        .run result@{
            publisher()
                .run {
                    if (populate)
                        populate()
                    else
                        this
                }
                .map { this.table!!.adapter.read(it) }
        }