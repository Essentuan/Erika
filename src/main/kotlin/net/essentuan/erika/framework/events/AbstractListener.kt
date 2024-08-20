package net.essentuan.erika.framework.events

import net.essentuan.erika.framework.events.api.Listener
import net.essentuan.esl.Rating
import net.essentuan.esl.reflections.extensions.extends

abstract class AbstractListener<T: Event>(
    override val id: String,
    override val event: Class<T>,
    override val phase: Rating,
    val receiveCancelled: Boolean = false,
    val filter: Class<*> = Any::class.java
) : Listener<T> {
    val list: Listener.List<T> = Event.Bus[event]

    override fun listen() { list+= this }

    override fun close() { list.remove(this) }

    override fun invoke(event: T) {
        if ((!event.cancelled || receiveCancelled) && (event !is GenericEvent<*> || event.type extends filter))
            on(event)
    }

    abstract fun on(event: T)

    override fun hashCode(): Int = id.hashCode()

    override fun equals(other: Any?): Boolean = (other as? Listener<*>)?.id == id
}