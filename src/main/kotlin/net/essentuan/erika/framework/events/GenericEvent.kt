package net.essentuan.erika.framework.events

abstract class GenericEvent<T: Any>(val type: Class<T>) : Event() {
    constructor(obj: T) : this(obj.javaClass)
}