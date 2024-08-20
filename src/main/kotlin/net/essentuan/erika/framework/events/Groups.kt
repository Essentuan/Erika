package net.essentuan.erika.framework.events

import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.framework.events.kord.KordListener
import net.essentuan.esl.iteration.extensions.iterate
import net.essentuan.esl.other.lock
import net.essentuan.esl.reflections.Functions.Companion.static
import net.essentuan.esl.reflections.Reflections
import net.essentuan.esl.reflections.Types.Companion.objects
import net.essentuan.esl.reflections.extensions.annotatedWith
import net.essentuan.esl.reflections.extensions.classOf
import net.essentuan.esl.reflections.extensions.extends
import net.essentuan.esl.reflections.extensions.instance
import net.essentuan.esl.reflections.extensions.visit
import net.essentuan.esl.scheduling.annotations.isAutoLoaded
import net.essentuan.esl.scheduling.api.id
import java.lang.ref.WeakReference
import kotlin.collections.set
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.kotlinFunction

object Static : AbstractGroup() {
    private var ready = false

    init {
        groups.lock { this[this@Static] = this@Static }
    }

    internal fun load() {
        if (ready)
            return

        Reflections
            .functions
            .annotatedWith(Subscribe::class)
            .static() iterate { func ->
                val args = func.parameters.filter { it.kind != KParameter.Kind.INSTANCE }
                
                if (args.size != 1)
                    return@iterate
                
                val event = args[0].type.javaType.classOf()
                val id = func.javaMethod?.id() ?: return@iterate
                
                when {
                    event extends Event::class -> {
                        listeners[id] = MethodListener(
                            id,
                            event as Class<Event>,
                            func.javaMethod!!,
                            null
                        ).also {
                            it.listen()
                        }
                    }
                    
                    event extends KordEventType::class -> {
                        listeners[id] = KordListener(
                            id,
                            event as Class<KordEventType>,
                            func,
                            null
                        ).also {
                            it.listen()
                        }
                    }
                }
            }
        
        ready = true
    }
}

object Ref {
    private var ready: Boolean = false

    internal fun load() {
        if (!ready)
            Reflections.types
                .objects()
                .map { it.instance }
                .filterNotNull()
                .forEach {
                    val events = it.events

                    if (events.isNotEmpty() && it.isAutoLoaded)
                        events.register()
                }

        ready = true
    }

    internal class Instance(obj: Any) : AbstractGroup() {
        private val ref = WeakReference(obj)

        init {
            obj.javaClass.visit()
                .flatMap { it.declaredMethods.asIterable() }
                .map { it.kotlinFunction }
                .filterNotNull()
                .filter { it annotatedWith Subscribe::class }
                .iterate { func ->
                    val args = func.parameters.filter { it.kind != KParameter.Kind.INSTANCE }
                
                    if (args.size != 1)
                        return@iterate
                    
                    val event = args[0].type.javaType.classOf()
                    val id = "${func.javaMethod?.id() ?: return@iterate}-${System.identityHashCode(obj)}"
                    
                    when {
                        event extends Event::class -> {
                            listeners[id] = MethodListener(
                                id,
                                event as Class<Event>,
                                func.javaMethod!!,
                                ref
                            ).also {
                                it.listen()
                            }
                        }
                        
                        event extends KordEventType::class -> {
                            listeners[id] = KordListener(
                                id,
                                event as Class<KordEventType>,
                                func,
                                ref
                            ).also {
                                it.listen()
                            }
                        }
                    }
                }
        }
    }
}