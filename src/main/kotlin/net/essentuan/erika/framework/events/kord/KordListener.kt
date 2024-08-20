package net.essentuan.erika.framework.events.kord

import net.essentuan.erika.framework.events.Event
import net.essentuan.erika.framework.events.KordEventType
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.framework.events.api.Listener
import net.essentuan.esl.Rating
import net.essentuan.esl.other.unsupported
import net.essentuan.esl.reflections.extensions.get
import java.lang.ref.WeakReference
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.jvm.isAccessible

class KordListener<T : KordEventType>(
    override val id: String,
    override val event: Class<T>,
    val function: KFunction<*>,
    val ref: WeakReference<Any>?
) : Listener<T> {
    val list: Listener.List<T> = Event.Bus[event]

    init {
        function.isAccessible = true
    }

    override val phase: Rating = function[Subscribe::class]!!.value
    override fun listen() {
        list+= this
    }

    override fun close() {
        list.remove(this)
    }

    override fun invoke(event: T) =
        unsupported()
    
    suspend fun call(event: KordEventType) {
        if (ref == null)
            function.callSuspend(event)
        else {
            val ref = ref.get()
            
            if (ref == null)
                close()
            else
                function.callSuspend(ref, event)
        }
    }
    
    override fun hashCode(): Int = id.hashCode()

    override fun equals(other: Any?): Boolean = (other as? Listener<*>)?.id == id
}