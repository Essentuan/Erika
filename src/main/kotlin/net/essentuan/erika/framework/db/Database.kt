package net.essentuan.erika.framework.db

import com.google.gson.internal.LazilyParsedNumber
import com.mongodb.DBRef
import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import com.mongodb.reactivestreams.client.MongoDatabase
import net.essentuan.erika.arg
import net.essentuan.erika.framework.db.api.table.Table
import net.essentuan.erika.framework.db.commands.find
import net.essentuan.erika.framework.db.table.StandardTable
import net.essentuan.erika.framework.db.table.TableImpl
import net.essentuan.erika.framework.db.table.adapters.JsonAdapter
import net.essentuan.erika.inline
import net.essentuan.esl.Result
import net.essentuan.esl.coroutines.await
import net.essentuan.esl.encoding.AbstractEncoder
import net.essentuan.esl.iteration.extensions.iterate
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.json
import net.essentuan.esl.json.type.AnyJson
import net.essentuan.esl.json.type.JsonType
import net.essentuan.esl.model.field.Field
import net.essentuan.esl.model.field.Property
import net.essentuan.esl.reflections.Reflections
import net.essentuan.esl.reflections.Types.Companion.objects
import net.essentuan.esl.reflections.extensions.instance
import net.essentuan.esl.result
import net.essentuan.esl.rx.iterate
import net.essentuan.esl.rx.publisher
import net.essentuan.esl.rx.toList
import net.essentuan.esl.scheduling.annotations.Every
import org.bson.Document
import org.bson.types.ObjectId
import org.reactivestreams.Publisher
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Type
import kotlin.reflect.KProperty

typealias Search<TERM, T> = Pair<TERM, Result<out T>>
typealias Pointer = DBRef

fun <TERM, T> Search(term: TERM): Search<TERM, T> =
    term to Result.empty()

fun <TERM, T> Search(term: TERM, value: T): Search<TERM, T> =
    term to value.result()

infix fun <TERM, T> T.by(term: TERM): Search<TERM, T> =
    Search(term, this)

object Database : Collection<String> {
    val client: MongoClient by lazy {
        MongoClients.create(arg("mongo").value)
    }

    val connection: MongoDatabase by lazy {
        client.getDatabase(arg("db").value)
    }

    val name: String by lazy { connection.name }

    private val collectionList: MutableSet<String> = mutableSetOf()

    private val tables: MutableMap<String, Table<*>> by lazy {
        Reflections.types
            .subtypesOf(StandardTable::class)
            .objects()
            .map { it.instance }
            .filterNotNull()
            .associateByTo(mutableMapOf()) { it.name }
    }

    operator fun get(str: String): Table<*> = tables.computeIfAbsent(str) { TableImpl(it, JsonAdapter) }

    override operator fun contains(element: String): Boolean {
        return synchronized(collectionList) { element in collectionList }
    }

    operator fun contains(table: Table<*>): Boolean = table.name in this

    override val size: Int
        get() = synchronized(collectionList) { collectionList.size }

    override fun isEmpty(): Boolean = synchronized(collectionList) { collectionList.isEmpty() }

    override fun iterator(): Iterator<String> =
        synchronized(collectionList) { collectionList.toTypedArray().iterator() }

    override fun containsAll(elements: Collection<String>): Boolean {
        for (element in elements) {
            if (element !in this)
                return false
        }

        return true
    }

    @Every(seconds = 1.0)
    private suspend fun updateCollections() {
        connection.listCollectionNames()
            .toList()
            .apply {
                synchronized(collectionList) {
                    collectionList.clear()

                    collectionList.addAll(this)
                }
            }
    }

    fun findAll(sequence: Sequence<Pointer>) = publisher {
        await {
            sequence.groupBy(Pointer::getCollectionName)
                .forEach { (coll, ids) ->
                    +inline {
                        find<Json> {
                            select from Database[coll].with(JsonAdapter)

                            where {
                                "_id" in ids
                            }
                        } iterate {
                            yield(it)
                        }
                    }
                }
        }
    }

    fun findAll(iterable: Iterable<Pointer>) =
        findAll(iterable.asSequence())

    fun findAll(vararg pointers: Pointer): Publisher<Json> =
        findAll(pointers.asSequence())

    infix fun ObjectId.via(table: Table<*>) = Pointer(table.name, this)
}

object DocumentAdapter : JsonType.Adapter<Document, Json> {
    @Suppress("UNCHECKED_CAST")
    private fun Any?.convert(): Any? = when (this) {
        is Document -> json { entries iterate { it.key to it.value.convert() } }
        is MutableList<*> -> {
            (this as MutableList<Any?>).listIterator() iterate { set(it.convert()) }
            this
        }

        else -> this
    }

    override fun convert(obj: Document): Json = obj.convert() as Json

    override fun from(): Class<Document> = Document::class.java

    override fun to(): Class<Json> = Json::class.java
}

object IdEncoder : AbstractEncoder<ObjectId, ObjectId>() {
    override fun decode(
        obj: ObjectId,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): ObjectId =
        obj

    override fun encode(
        obj: ObjectId,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): ObjectId =
        obj

    override fun toString(
        obj: ObjectId,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): String =
        obj.toHexString()

    override fun valueOf(
        string: String,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): ObjectId =
        ObjectId(string)
}

@Suppress("UNCHECKED_CAST")
private fun Any.bson(): Any = when (this) {
    is LazilyParsedNumber -> toDouble()
    is AnyJson -> this.bson()
    is MutableList<*> -> {
        (this as MutableList<Any?>).listIterator() iterate { set(it?.bson()) }
        this
    }

    else -> this
}

fun AnyJson.bson(): Document {
    return Document().apply {
        this@bson.entries iterate {
            this@apply[it.key] = it.value?.bson()
        }
    }
}

val AnyJson.id: ObjectId?
    get() = getId("_id")

val Document.id: ObjectId?
    get() = getObjectId("_id")

fun AnyJson.isId(key: String): Boolean = this[key]?.isId() ?: false

fun AnyJson.getId(key: String): ObjectId? = this[key]?.asId()

fun AnyJson.getId(key: String, default: ObjectId): ObjectId = this[key]?.asId() ?: default

fun JsonType.Value.isId(): Boolean = raw is ObjectId

fun JsonType.Value.asId(): ObjectId? = `as`(ObjectId::class)

fun JsonType.Value.asId(default: ObjectId): ObjectId = asId() ?: default

val KProperty<*>.eslProp: Property?
    get() = Field[this] as? Property?

val KProperty<*>.eslKey: String
    get() = eslProp?.key ?: name