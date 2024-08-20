package net.essentuan.erika.framework.events

import com.google.common.collect.MapMaker
import net.essentuan.erika.framework.events.api.Cancellable
import net.essentuan.erika.framework.events.api.Listener
import net.essentuan.esl.Rating
import net.essentuan.esl.iteration.Iterators
import net.essentuan.esl.other.lock
import net.essentuan.esl.other.stacktrace
import net.essentuan.esl.other.unsupported
import net.essentuan.esl.reflections.extensions.simpleString
import java.lang.reflect.Method
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

typealias KordEventType = dev.kord.core.event.Event

abstract class Event {
    var cancelled: Boolean = false
        protected set(value) {
            if (this !is Cancellable)
                unsupported { "${this::class.simpleString()} does not support cancellation!" }

            field = value
        }

    var phase: Rating = Rating.CRITICAL
        set(value) {
            require(value.ordinal <= field.ordinal) {
                "Attempted to set event phase to $value when already $field"
            }

            field = value
        }


    /**
     * Dispatches the event
     *
     * @return true if the event was cancelled
     */
    fun post(): Boolean = Bus.post(this)

    object Bus : Iterable<Listener.List<*>> {
        private val listeners = mutableMapOf<Class<*>, Listener.List<*>>()

        fun start() {
            Static.load()
            Ref.load()
        }

        @Suppress("UNCHECKED_CAST")
        operator fun <T : Any> get(cls: Class<T>): Listener.List<T> =
            listeners.lock { getOrPut(cls) { Listener.List<Event>(cls) } as Listener.List<T> }

        operator fun <T : Any> get(cls: KClass<T>): Listener.List<T> = get(cls.java)

        /**
         * Dispatches the event
         *
         * @return true if the event was cancelled
         */
        internal fun post(event: Event): Boolean = this[event.javaClass].post(event)

        override fun iterator(): Iterator<Listener.List<*>> = listeners.lock { values.iterator() }
    }

    interface Group : Collection<Listener<*>> {
        operator fun get(id: String): Listener<*>?

        operator fun get(method: Method): Listener<*>?

        operator fun get(func: KFunction<*>): Listener<*>?

        operator fun contains(id: String): Boolean

        operator fun contains(method: Method): Boolean

        operator fun contains(func: KFunction<*>): Boolean

        fun register() {
            for (listener in this)
                listener.listen()
        }

        fun unregister() {
            for (listener in this) {
                listener.close()
            }
        }

        companion object {
            fun register(owner: Any, group: Group) {
                groups.lock { computeIfAbsent(owner) { group } }
            }

            fun remove(owner: Any) {
                if (owner is Static)
                    return

                groups.lock { remove(owner) }
            }
        }

        object Empty : Group {
            override fun get(id: String): Listener<*>? = null
            override fun get(method: Method): Listener<*>? = null
            override fun get(func: KFunction<*>): Listener<*>? = null

            override fun contains(id: String): Boolean = false
            override fun contains(method: Method): Boolean = false

            override fun contains(func: KFunction<*>): Boolean = false
            override fun contains(element: Listener<*>): Boolean = false

            override val size: Int
                get() = 0

            override fun containsAll(elements: Collection<Listener<*>>): Boolean = elements.isEmpty()
            override fun isEmpty(): Boolean = true
            override fun iterator(): Iterator<Listener<*>> = Iterators.empty()

        }
    }
}

internal val groups = MapMaker().weakKeys().makeMap<Any, Event.Group>()

val Any.events: Event.Group
    get() {
        return groups.lock {
            computeIfAbsent(this@events) {
                val group = Ref.Instance(it)

                if (group.isEmpty()) null else group
            }
        } ?: return Event.Group.Empty
    }

fun <T : Event> listen(
    event: KClass<T>,
    priority: Rating = Rating.NORMAL,
    receiveCancelled: Boolean = false,
    filter: KClass<*> = Any::class,
    start: Boolean = true,
    block: (T) -> Unit
): Listener<T> = LambdaListener(
    "lambda-${stacktrace().contentHashCode()}#${Random.nextInt(0, 10000)}",
    event.java,
    priority,
    receiveCancelled,
    filter.java,
    block
).apply { if (start) listen() }

inline fun <reified T : Event> listen(
    priority: Rating = Rating.NORMAL,
    receiveCancelled: Boolean = false,
    filter: KClass<*> = Any::class,
    start: Boolean = true,
    noinline block: (T) -> Unit
): Listener<T> = listen(
    T::class,
    priority,
    receiveCancelled,
    filter,
    start,
    block
)

inline fun <reified T : GenericEvent<TYPE>, reified TYPE : Any> listen(
    priority: Rating = Rating.NORMAL,
    receiveCancelled: Boolean = false,
    start: Boolean = true,
    noinline block: (T) -> Unit
): Listener<T> = listen(
    T::class,
    priority,
    receiveCancelled,
    TYPE::class,
    start,
    block
)
