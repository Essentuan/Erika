package net.essentuan.erika.framework

import net.essentuan.erika.ReadyEvent
import net.essentuan.erika.arg
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.framework.events.events
import net.essentuan.esl.Rating
import net.essentuan.esl.reflections.Reflections
import net.essentuan.esl.reflections.Types.Companion.objects
import net.essentuan.esl.reflections.extensions.classOf
import net.essentuan.esl.reflections.extensions.instance
import net.essentuan.esl.reflections.extensions.simpleString
import net.essentuan.esl.scheduling.annotations.Auto
import net.essentuan.esl.scheduling.tasks
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaType

@Auto(false)
abstract class Service {
    val name: String = this::class.simpleString().replace(".", "").removeSuffix("Service")
    private val managed: MutableList<Managed<*>> = mutableListOf()

    var isEnabled: Boolean = false
        set(value) {
            if (field == value)
                return

            field = value

            if (field) doEnable() else doDisable()
        }

    private fun doEnable() {
        managed.forEach(Managed<*>::load)

        tasks.resume()
        events.register()

        enable()
    }

    private fun doDisable() {
        managed.forEach(Managed<*>::reset)

        tasks.suspend(true)
        events.unregister()

        disable()
    }

    protected fun enable() = Unit

    protected fun disable() = Unit

    protected inline operator fun <T> invoke(crossinline initializer: () -> T): Managed<T> = object : Managed<T>() {
        override fun new(): T = initializer()
    }

    abstract inner class Managed<T> : ReadWriteProperty<Any?, T> {
        init { managed+= this }

        private var value: Any? = null
        private var ready: Boolean = false
        private var disposal: T.() -> Unit = {
            when (this) {
                is AutoCloseable -> close()
            }
        }

        protected abstract fun new(): T
        
        fun load() {
            check(this@Service.isEnabled)

            this.value = new()
            this.ready = true
        }

        @Suppress("UNCHECKED_CAST")
        fun reset() {
            check(!this@Service.isEnabled)

            ready = false

            disposal(value as T)
            value = null
        }

        private fun requireReady(property: KProperty<*>) {
            if (!ready)
                throw NoSuchElementException("Cannot access ${
                    this@Service::class.simpleString()
                }#${property.name}\$${property.returnType.javaType.classOf()} before initialization!")
        }

        @Suppress("UNCHECKED_CAST")
        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            requireReady(property)

            return value as T
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            requireReady(property)

            this.value = value
        }

        infix fun finally(disposal: T.() -> Unit): Managed<T> {
            this.disposal = disposal

            return this
        }
    }

    companion object {
        @Subscribe(Rating.LOWEST)
        private fun ReadyEvent.on() {
            val enabled by arg("services", emptySet()) {
                it.split(' ', ',').asSequence()
                    .map { arg -> arg.trim() }
                    .toSet()
            }

            if ("." in enabled) {
                Reflections.types
                    .subtypesOf(Service::class)
                    .objects()
                    .map { it.instance }
                    .filterNotNull()
                    .forEach {
                        it.isEnabled = true
                    }
            } else
                Reflections.types
                    .subtypesOf(Service::class)
                    .objects()
                    .map { it.instance }
                    .filterNotNull()
                    .forEach {
                        if (it.name in enabled)
                            it.isEnabled = true
                    }
        }
    }
}

fun Service.enable() {
    this.isEnabled = true
}

fun Service.disable() {
    this.isEnabled = false
}