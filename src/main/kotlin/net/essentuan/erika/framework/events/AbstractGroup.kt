package net.essentuan.erika.framework.events

import net.essentuan.erika.framework.events.api.Listener
import net.essentuan.esl.scheduling.api.id
import java.lang.reflect.Method
import java.util.Collections
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

abstract class AbstractGroup : Event.Group {
    protected val listeners: MutableMap<String, Listener<*>> = Collections.synchronizedMap(mutableMapOf())

    override fun get(id: String): Listener<*>? = listeners[id]

    override fun get(method: Method): Listener<*>? = this[method.id()]

    override fun get(func: KFunction<*>): Listener<*>? {
        return this[func.javaMethod?.id() ?: return null]
    }

    override fun contains(id: String): Boolean = get(id) != null
    override fun contains(method: Method): Boolean = get(method) != null
    override fun contains(func: KFunction<*>): Boolean = get(func) != null

    override fun contains(element: Listener<*>): Boolean = element.id in listeners

    override val size: Int
        get() = listeners.size

    override fun containsAll(elements: Collection<Listener<*>>): Boolean {
        for (listener in elements)
            if (listener !in this)
                return false

        return true
    }

    override fun isEmpty(): Boolean = listeners.isEmpty()

    override fun iterator(): Iterator<Listener<*>> = listeners.values.iterator()
}