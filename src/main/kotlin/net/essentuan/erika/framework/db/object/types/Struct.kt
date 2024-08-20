package net.essentuan.erika.framework.db.`object`.types

import com.google.common.collect.Multimap
import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Updates
import net.essentuan.erika.ShutdownEvent
import net.essentuan.erika.framework.db.Database
import net.essentuan.erika.framework.db.Search
import net.essentuan.erika.framework.db.api.table.Table
import net.essentuan.erika.framework.db.bson
import net.essentuan.erika.framework.db.builders.Filter
import net.essentuan.erika.framework.db.builders.filter
import net.essentuan.erika.framework.db.commands.Find
import net.essentuan.erika.framework.db.commands.find
import net.essentuan.erika.framework.db.`object`.BsonModel
import net.essentuan.erika.framework.db.`object`.Memory
import net.essentuan.erika.framework.db.`object`.Memory.Locate
import net.essentuan.erika.framework.db.`object`.Memory.load
import net.essentuan.erika.framework.db.`object`.locate
import net.essentuan.erika.framework.db.table.StandardTable
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.esl.collections.multimap.Multimaps
import net.essentuan.esl.collections.multimap.hashSetValues
import net.essentuan.esl.collections.mutableSetFrom
import net.essentuan.esl.coroutines.await
import net.essentuan.esl.coroutines.blocking
import net.essentuan.esl.future.api.Future
import net.essentuan.esl.iteration.extensions.iterate
import net.essentuan.esl.json.Json
import net.essentuan.esl.map
import net.essentuan.esl.model.Descriptor
import net.essentuan.esl.orElseGet
import net.essentuan.esl.other.lock
import net.essentuan.esl.reflections.extensions.typeInformationOf
import net.essentuan.esl.result
import net.essentuan.esl.rx.Generator
import net.essentuan.esl.rx.discard
import net.essentuan.esl.rx.distinct
import net.essentuan.esl.rx.findFirst
import net.essentuan.esl.rx.forEach
import net.essentuan.esl.rx.groupBy
import net.essentuan.esl.rx.publish
import net.essentuan.esl.scheduling.annotations.Every
import net.essentuan.esl.scheduling.annotations.Lifetime
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import java.util.IdentityHashMap
import kotlin.reflect.KClass

abstract class Struct<TABLE : Table<*>> : BsonModel() {
    override suspend fun save() = save(this)

    override val table: Table<*>
        get() = this::class.table

    companion object {
        private val tables: MutableMap<Class<*>, Table<*>> = mutableMapOf()
        private var queue = mutableSetFrom<Struct<*>>(::IdentityHashMap)

        @Every(seconds = 1.0)
        @Lifetime(minutes = 1.0)
        private suspend fun empty() {
            synchronized(queue) {
                if (queue.isEmpty())
                    return

                queue.toTypedArray().also { queue.clear() }
            }.publish().saveAll()
        }

        @Subscribe
        private fun ShutdownEvent.listen() {
            blocking { empty() }
        }

        suspend fun Publisher<out Struct<*>>.saveAll() {
            await {
                for ((table, structs) in distinct { it.id }.groupBy { it.table }.asMap())
                    +Future {
                        table.mongo.bulkWrite(
                            structs.map {
                                ReplaceOneModel(
                                    filter { "_id" eq it.id },
                                    it.export(false).bson(),
                                    ReplaceOptions().upsert(true)
                                )
                            },
                            BulkWriteOptions().ordered(false)
                        ).discard()
                    }
            }
        }

        suspend fun Sequence<Struct<*>>.saveAll() =
            publish().saveAll()

        suspend fun Iterable<Struct<*>>.saveAll() =
            publish().saveAll()

        suspend fun saveAll(vararg struct: Struct<*>) =
            struct.publish().saveAll()

        suspend fun save(struct: Struct<*>) = saveAll(struct)

        fun Struct<*>.enqueue() {
            queue.lock { add(this@enqueue) }
        }

        val KClass<*>.table: Table<*>
            get() = tables.lock {
                computeIfAbsent(this@table.java) {
                    Database[StandardTable.nameOf(it.typeInformationOf(Struct::class)["TABLE"] as Class<*>)]
                }
            }
    }

    abstract class Locator<TERM : Any, OUT : Struct<*>>(
        downstream: Subscriber<in Search<TERM, OUT>>,
        protected open val terms: Set<TERM>,
        groups: Int
    ) : Generator<Search<TERM, OUT>>(downstream) {
        private val pending: Array<Multimap<TERM, TERM>> = Array(groups) { Multimaps.hashKeys().hashSetValues() }

        private val total: Int
            get() = pending.sumOf { it.size() }

        private fun flatten(): List<TERM> =
            pending.flatMap { it.lock { it.keySet().toList() } }

        protected abstract fun test(term: TERM): Boolean
        protected open fun test(struct: OUT): Boolean = true
        protected open fun map(term: TERM): TERM = term
        protected abstract fun groupBy(term: TERM): Int
        protected abstract fun Locate.primary(group: Int, terms: Iterable<*>)
        protected abstract fun Filter.primary(group: Int, terms: Iterable<*>)
        protected open fun Find<OUT>.config() = Unit
        protected abstract suspend operator fun invoke(term: TERM): OUT?
        protected abstract fun Locate.secondary(struct: OUT)
        protected abstract fun Filter.secondary(struct: OUT)
        protected abstract fun finish(struct: OUT): Array<TERM>

        protected abstract fun Sequence<Struct<*>>.filter(): Sequence<OUT>

        protected abstract val OUT.children: Array<Struct<*>>
        protected abstract val table: Table<OUT>
        protected abstract val descriptor: Descriptor<OUT, Json>

        private suspend fun complete(parent: TERM?, out: OUT) {
            val result = out.result()

            if (parent != null)
                yield(parent to result)

            for (term in finish(out)) {
                pending[groupBy(term)].lock {
                    removeAll(map(term))
                } iterate {
                    if (term != parent)
                        yield(it to result)
                }
            }
        }

        override suspend fun generate() {
            for (term in terms) {
                if (!test(term)) {
                    yield(Search(term))
                    continue
                }

                pending[groupBy(term)].put(map(term), term)
            }

            if (total == 0)
                return

            locate<Struct<*>> {
                for (group in pending.indices)
                    primary(group, pending[group].values())
            }.filter() iterate { complete(null, it) }

            if (total == 0)
                return

            await {
                find {
                    select from this@Locator.table

                    where {
                        or {
                            for (group in pending.indices)
                                primary(group, pending[group].values())
                        }
                    }

                    config()
                }.forEach {
                    if (test(it))
                        +Future { complete(null, it) }
                }
            }

            if (total == 0)
                return

            await {
                flatten() iterate { term ->
                    +Future {
                        val out = this@Locator(term)

                        if (out == null) {
                            yield(Search(term))
                            return@Future
                        }

                        complete(
                            term,
                            this@Locator.table.mongo.findOneAndUpdate(
                                filter {
                                    secondary(out)
                                },
                                Updates.setOnInsert(out.export(external = false).bson()),
                                FindOneAndUpdateOptions().upsert(true)
                            ).findFirst()
                                .map {
                                    descriptor.load(it)
                                }.orElseGet {
                                    Memory.create<Struct<*>>(
                                        { secondary(out) },
                                        { out.children },
                                        { out }
                                    ) as OUT
                                },
                        )
                    }
                }
            }
        }
    }
}