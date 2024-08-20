@file:OptIn(DelicateCoroutinesApi::class)

package net.essentuan.erika.framework.db

import com.google.common.collect.MapMaker
import com.google.common.collect.Multimap
import kotlinx.coroutines.DelicateCoroutinesApi
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.db.Populator.DocumentSlot.Companion.documentSlots
import net.essentuan.erika.framework.db.Populator.ListSlot.Companion.listSlots
import net.essentuan.erika.framework.db.Populator.Slot.Companion.slots
import net.essentuan.erika.framework.db.commands.find
import net.essentuan.erika.framework.db.table.adapters.DocumentAdapter
import net.essentuan.erika.inline
import net.essentuan.esl.collections.builders.mutableList
import net.essentuan.esl.collections.multimap.Multimaps
import net.essentuan.esl.collections.multimap.hashSetValues
import net.essentuan.esl.coroutines.await
import net.essentuan.esl.rx.Stage
import net.essentuan.esl.rx.gather
import net.essentuan.esl.rx.iterate
import org.bson.Document
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import java.util.IdentityHashMap
import kotlin.collections.indices
import kotlin.collections.iterator
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class Populator(
    upstream: Publisher<Document>,
    downstream: Subscriber<in Document>
) : Stage<Document, Document>(upstream, downstream) {
    private var cont: Continuation<Unit>? = null
    private val roots = Multimaps.keys(::IdentityHashMap).hashSetValues<Document, Pointer>()

    var start: Int = 0

    val size: Int
        @Synchronized
        get() = roots.size()

    override suspend fun generate() {
        for (doc in this) {
            val out = doc.scan()

            if (out.isEmpty())
                yield(doc)
            else
                out.enqueue()
        }

        start = size

        if (size == 0)
            return

        batch()

        suspendCoroutine<Unit> {
            synchronized(this) {
                if (size == 0)
                    it.resume(Unit)
                else
                    cont = it
            }
        }

        cont = null
    }

    private fun Document.scan(): List<Slot> {
        val out = mutableListOf<Slot>()
        slots(this, this@Populator, out)

        return out
    }

    fun List<Slot>.enqueue() {
        synchronized(this@Populator) {
            for (slot in this)
                roots[slot.root]+= slot.pointer
        }

        Companion += this
    }

    fun complete(slot: Slot) {
        val (root, finished) =  synchronized(this) {
            val root = roots[slot.root]
            root.remove(slot.pointer)

            (root.isEmpty()) to (cont != null && size == 0)
        }

        if (root)
            inline {
                yield(slot.root)

                if (finished)
                    cont?.resume(Unit)
            }
        else if (finished)
            cont?.resume(Unit)
    }

    private companion object {
        private val loaded = MapMaker().weakValues().makeMap<Pointer, Any>()
        private val slots = mutableMapOf<String, Multimap<Any, Slot>>()
        private val pending = Multimaps.keys(::IdentityHashMap).hashSetValues<Populator, String>()

        private val fetching = mutableSetOf<Pointer>()

        operator fun plusAssign(slots: List<Slot>) {
            val existing = mutableListOf<Pair<Slot, Any>>()

            synchronized(this) {
                for (slot in slots) {
                    val obj = loaded[slot.pointer]

                    if (obj != null)
                        existing += slot to obj
                    else {
                        this.slots.computeIfAbsent(slot.pointer.collectionName) {
                            Multimaps.hashKeys().hashSetValues()
                        }[slot.pointer.id] += slot

                        pending[slot.owner] += slot.pointer.collectionName
                    }
                }
            }

            existing.forEach { (slot, obj) -> slot.yield(obj) }
        }


        fun Pointer.yield(result: Any?) {
            synchronized(this@Companion) {
                if (result != null)
                    loaded[this] = result

                fetching.remove(this)
                slots[collectionName]?.removeAll(id)
            }?.forEach {
                it.yield(result)
            }
        }

        fun Populator.batch() = inline {
            val objects = synchronized(this@Companion) {
                pending.removeAll(this)
                    .asSequence()
                    .flatMap {
                        slots[it]
                            ?.keys()
                            ?.asSequence()
                            ?.map { id ->
                                Pointer(it, id)
                            }?.filter(fetching::add) ?: emptySequence()
                    }
                    .fold(Multimaps.hashKeys().hashSetValues<String, Pointer>()) { map, ptr ->
                        map[ptr.collectionName]+= ptr

                        map
                    }
            }

            if (objects.isEmpty)
                return@inline

            await {
                for ((coll, ids) in objects.asMap()) {
                    if (ids.isEmpty())
                        continue

                    +inline {
                        find {
                            select from Database[coll].with(DocumentAdapter)

                            where {
                                "_id" in (ids.map { it.id } as Iterable<Any>)
                            }
                        } iterate {
                            val pointer = Pointer(coll, it["_id"] ?: return@iterate)

                            synchronized(objects) {
                                objects.remove(coll, pointer)
                            }

                            pointer.yield(it)
                        }
                    }
                }
            }

            for ((_, pointer) in objects.entries())
                pointer.yield(null)
        }
    }

    abstract class Slot(
        val pointer: Pointer,
        val root: Document,
        val owner: Populator
    ) {
        protected abstract fun set(result: Any?)

        fun yield(result: Any?) {
            set(result)

            owner.complete(this)
        }

        companion object {
            @Suppress("UNCHECKED_CAST")
            fun Any.slots(root: Document, owner: Populator, out: MutableList<Slot>) {
                when (this) {
                    is MutableList<*> -> (this as MutableList<Any?>).listSlots(root, owner, out)
                    is Document -> documentSlots(root, owner, out)
                }
            }
        }
    }

    class DocumentSlot(
        val mutex: Any,
        val entry: MutableMap.MutableEntry<String, Any?>,
        pointer: Pointer,
        root: Document,
        owner: Populator
    ) : Slot(pointer, root, owner) {
        override fun set(result: Any?) {
            synchronized(mutex) {
                entry.setValue(result)
            }
        }

        companion object {
            fun Document.documentSlots(root: Document, owner: Populator, out: MutableList<Slot>) {
                for (entry in this) {
                    when (val value = entry.value ?: continue) {
                        is Pointer ->
                            out.add(DocumentSlot(this, entry, value, root, owner))

                        else ->
                            entry.value.slots(root, owner, out)
                    }
                }
            }
        }
    }

    class ListSlot(
        val list: MutableList<Any?>,
        val index: Int,
        pointer: Pointer,
        root: Document,
        owner: Populator
    ) : Slot(pointer, root, owner) {
        override fun set(result: Any?) {
            synchronized(list) {
                list[index] = result
            }
        }

        companion object {
            fun MutableList<Any?>.listSlots(root: Document, owner: Populator, out: MutableList<Slot>) {
                for (i in indices) {
                    when (val entry = this[i] ?: continue) {
                        is Pointer ->
                            out.add(ListSlot(this, i, entry, root, owner))

                        else ->
                            entry.slots(root, owner, out)
                    }
                }
            }
        }
    }
}

fun Publisher<Document>.populate(): Publisher<Document> =
    gather { Populator(this, it) }
