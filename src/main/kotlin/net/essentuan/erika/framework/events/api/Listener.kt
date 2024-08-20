package net.essentuan.erika.framework.events.api

import net.essentuan.erika.framework.events.Event
import net.essentuan.erika.framework.events.KordEventType
import net.essentuan.erika.framework.events.kord.KordListener
import net.essentuan.esl.Rating
import net.essentuan.esl.iteration.extensions.flatMap
import net.essentuan.esl.other.lock
import net.essentuan.esl.other.unsupported
import net.essentuan.esl.reflections.extensions.extends
import net.essentuan.esl.reflections.extensions.simpleString
import net.essentuan.esl.reflections.extensions.visit
import java.util.Collections
import java.util.WeakHashMap

interface Listener<T : Any> {
    val id: String
    val event: Class<T>
    val phase: Rating

    operator fun invoke(event: T)

    fun listen()

    fun close()

    class List<T : Any>(val event: Class<*>) : Iterable<Listener<T>> {
        //Prevents Inherited listeners from being garbage collected
        private val inherited = mutableListOf<Inherited<*>>()

        private val listeners = Array<MutableSet<Listener<T>>>(Rating.entries.size) {
            Collections.newSetFromMap(WeakHashMap())
        }

        init {
            val seen = mutableSetOf<Class<*>>()
            val parents = listOf(event.superclass, *event.interfaces)

            for (parent in parents) parent@ {
                if (parent == null || !(parent extends Event::class || parent extends KordEventType::class))
                    continue

                var seenBefore = false

                val children = parent.visit(true).toList()
                for (child in children) {
                    if (child in seen) {
                        seenBefore = true
                        break
                    }
                }

                if (seenBefore)
                    continue

                for (child in children)
                    seen.add(child)

                for (phase in Rating.entries)
                    @Suppress("UNCHECKED_CAST")
                    this += Inherited(parent as Class<Event>, phase) as Listener<T>
            }
        }

        operator fun get(rating: Rating): MutableSet<Listener<T>> {
            return listeners[rating.ordinal]
        }

        operator fun plusAssign(listener: Listener<T>) {
            if (listener is Inherited<*>)
                inherited += listener

            this[listener.phase].lock { add(listener) }
        }

        fun remove(listener: Listener<T>) {
            this[listener.phase].lock { remove(listener) }
        }

        fun post(event: T): Boolean {
            when (event) {
                is Event -> {
                    event.phase = Rating.CRITICAL

                    while (true) {
                        for (listener in this[event.phase].lock { toList() })
                            listener(event)

                        if (event.phase == Rating.LOWEST)
                            break

                        event.phase = event.phase.next()
                    }

                    return event.cancelled
                }

                else -> throw IllegalArgumentException("Cannot post event of unknown type '${event::class.simpleString()}'!")
            }
        }

        suspend fun call(event: T) {
            when (event) {
                is Event ->
                    post(event)

                is KordEventType -> {
                    for (phase in Rating.entries.asReversed())
                        call(event, phase)
                }

                else -> throw IllegalArgumentException("Cannot post event of unknown type '${event::class.simpleString()}'!")
            }
        }

        private suspend fun call(event: KordEventType, phase: Rating) {
            for (listener in this[phase].lock { toList() }) {
                when (listener) {
                    is KordListener<*> ->
                        listener.call(event)

                    is Inherited<*> -> {
                        listener.list.call(event, phase)
                    }
                }
            }
        }

        override fun iterator(): Iterator<Listener<T>> = Rating.entries
            .iterator()
            .flatMap { this[it].iterator() }

        private class Inherited<U : Any>(
            override val event: Class<U>,
            override val phase: Rating
        ) : Listener<U> {
            override val id: String =
                "ListenerList(event=${event.simpleString()}).Inherited(event=${event.simpleString()}))"
            val list = Event.Bus[event]

            override fun listen() = unsupported()

            override fun close() = unsupported()

            override fun invoke(event: U) {
                for (listener in list[phase].lock { toList() })
                    listener(event)
            }

            override fun hashCode(): Int = id.hashCode()

            override fun equals(other: Any?): Boolean = (other as? Listener<*>)?.id == id
        }
    }
}