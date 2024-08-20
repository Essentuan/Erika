package net.essentuan.erika.framework.events

import net.essentuan.erika.framework.events.annotations.ReceiveCanceled
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.esl.reflections.extensions.annotatedWith
import net.essentuan.esl.reflections.extensions.classOf
import net.essentuan.esl.reflections.extensions.get
import net.essentuan.esl.reflections.extensions.tags
import net.essentuan.esl.reflections.extensions.typeArgs
import java.lang.ref.WeakReference
import java.lang.reflect.Method

class MethodListener<T : Event>(
    id: String,
    event: Class<T>,
    val method: Method,
    val ref: WeakReference<Any>?
) : AbstractListener<T>(
    id,
    event,
    method.tags[Subscribe::class]!!.value,
    method annotatedWith ReceiveCanceled::class,
    method.parameters
        .getOrNull(0)
        ?.run {
            this.parameterizedType.typeArgs().getOrNull(0)?.classOf()
        } ?: Any::class.java
) {
    init {
        method.isAccessible = true
    }

    override fun on(event: T) {
        if (ref == null)
            method.invoke(null, event)
        else {
            val ref = ref.get()

            if (ref == null)
                close()
            else
                method.invoke(ref, event)
        }
    }
}