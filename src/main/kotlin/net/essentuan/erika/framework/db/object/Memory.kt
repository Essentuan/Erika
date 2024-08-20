package net.essentuan.erika.framework.db.`object`

import net.essentuan.erika.ShutdownEvent
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.db.id
import net.essentuan.erika.framework.db.`object`.types.Struct
import net.essentuan.erika.framework.db.`object`.types.Struct.Companion.enqueue
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.esl.Rating
import net.essentuan.esl.collections.mutableSetFrom
import net.essentuan.esl.iteration.extensions.flatMap
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Descriptor
import net.essentuan.esl.other.lock
import net.essentuan.esl.reflections.extensions.visit
import org.bson.Document
import org.bson.types.ObjectId
import java.util.WeakHashMap
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter

fun hash(property: KProperty<*>): Int =
    property.javaField?.hashCode() ?: property.javaGetter!!.hashCode()

object Memory {
    private val storage: MutableMap<Int, Storage<*>> = mutableMapOf()
    private val cache: MutableMap<Class<*>, Set<Storage<*>>> = mutableMapOf()

    val ids: Storage<ObjectId> = Storage(BsonModel::id) { it }

    init {
        storage[hash(BsonModel::id)] = ids
    }

    @Suppress("UNCHECKED_CAST")
    private operator fun <T> get(property: Int): Storage<T>? =
        storage[property] as Storage<T>?

    operator fun <T> get(property: KProperty<T>): Storage<T>? =
        this[hash(property)]

    @Synchronized
    operator fun get(cls: Class<*>): Set<Storage<*>> = cache.computeIfAbsent(cls) { _ ->
        cls.visit()
            .flatMap { it.kotlin.declaredMemberProperties }
            .map {
                storage[hash(it)]
            }.filterNotNull()
            .toSet()
    }

    operator fun get(id: ObjectId?): BsonModel? =
        ids.lock { find(id ?: return null).firstOrNull() }


    fun counts(): Map<Class<*>, Int> = synchronized(ids) {
        val out = mutableMapOf<Class<*>, Int>()

        for (obj in ids)
            out.compute(obj.javaClass) { _, it -> (it ?: 0) + 1 }

        out
    }

    inline fun <reified T : Struct<*>> create(
        locator: Locate.() -> Unit,
        models: (T) -> Array<Struct<*>>,
        factory: () -> T
    ): T {
        var save: Array<Struct<*>>? = null

        return synchronized(Memory) {
            val loaded = locate<T>(locator).firstOrNull()

            if (loaded != null)
                return@synchronized loaded

            val obj = factory()

            register(obj)
            obj.enqueue()

            save = models(obj)
            for (struct in save!!)
                register(struct)

            return@synchronized obj
        }.also {
            save?.forEach { it.enqueue() }
        }

    }

    @Synchronized
    fun register(model: BsonModel) {
        this[model.javaClass].forEach { it.add(model) }
    }

    @Synchronized
    @Suppress("UNCHECKED_CAST")
    fun <T : BsonModel> Descriptor<T, Json>.load(json: Json, flags: Set<Any>): T =
        Memory[json.id] as T? ?: this(json, flags).also { register(it) }

    fun <T : BsonModel> Descriptor<T, Json>.load(json: Json, vararg flags: Any): T =
        load(json, setOf(*flags))

    fun <T : BsonModel> Descriptor<T, Json>.load(doc: Document, flags: Set<Any>): T =
        load(Json(doc), flags)

    fun <T : BsonModel> Descriptor<T, Json>.load(doc: Document, vararg flags: Any): T =
        load(doc, setOf(flags))

    inline fun <T : BsonModel, U> refresh(model: T, vararg modified: KProperty<*>, block: T.() -> U): U =
        synchronized(model) {
            synchronized(Memory) {
                val keys = Array<Storage<*>>(modified.size) { i -> this[modified[i]]!!.also { it.remove(model) } }

                block(model).also {
                    for (key in keys)
                        key.add(model)
                }
            }
        }

    @Subscribe(Rating.CRITICAL)
    private fun ShutdownEvent.on() {
        var count: Int = 0

        synchronized(ids) {
            for (model in ids) {
                if (model is Struct<*>) {
                    model.enqueue()
                    count++
                }
            }
        }

        Logging.info("Enqueuing $count structs")
    }

    @JvmInline
    value class Locate(
        private val result: MutableList<BsonModel>
    ) {
        infix fun <T> KProperty<T>.eq(obj: T) {
            val storage = Memory[this] ?: return

            storage.lock { result += find(obj) }
        }

        operator fun <T> Iterable<T>.contains(property: KProperty<T>): Boolean {
            val storage = get<T>(hash(property)) ?: return false

            for (e in this)
                storage.lock { result += find(e) }

            return true
        }
    }

    @JvmInline
    value class Store(
        val placeholder: Memory
    ) {
        operator fun <T> Pair<KProperty<T>, (Any?) -> Any?>.unaryPlus() {
            synchronized(Memory) {
                storage.computeIfAbsent(hash(this.first)) { _ -> Storage(this.first, this.second) }
            }
        }

        operator fun <T> KProperty<T>.unaryPlus() =
            +this { it }

        operator fun <T> KProperty<T>.invoke(
            mapper: (Any?) -> Any?
        ): Pair<KProperty<T>, (Any?) -> Any?> =
            this to mapper
    }
}

inline fun <reified T : BsonModel> locate(
    block: Memory.Locate.() -> Unit
): Sequence<T> {
    val out = mutableListOf<BsonModel>()

    Memory.Locate(out).block()

    return out.asSequence().filterIsInstance<T>()
}

inline fun store(block: Memory.Store.() -> Unit) =
    Memory.Store(Memory).block()

class Storage<T>(
    private val property: KProperty<T>,
    private val mapper: (Any?) -> Any?
) : Iterable<BsonModel> {
    init {
        property.getter.isAccessible = true
    }

    private val map: MutableMap<Any?, MutableSet<BsonModel>> = mutableMapOf()

    val size: Int
        @Synchronized
        get() =
            map.values.sumOf { it.size }

    fun find(value: T): Iterable<BsonModel> {
        return map[mapper(value)] ?: emptyList()
    }

    fun add(model: BsonModel) {
        val key = mapper(property.getter.call(model))

        synchronized(this) {
            map.computeIfAbsent(key) {
                mutableSetFrom(::WeakHashMap)
            }.add(model)
        }
    }

    @Synchronized
    fun remove(model: BsonModel) {
        val set = map[mapper(property.getter.call(model))] ?: return

        synchronized(this) { set.remove(model) }
    }

    override fun iterator(): Iterator<BsonModel> =
        map.values.iterator().flatMap { it.iterator() }
}