package net.essentuan.erika

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

interface Argument<T> : ReadOnlyProperty<Any?, T> {
    val arg: String
    val value: T
    val present: Boolean

    override fun getValue(thisRef: Any?, property: KProperty<*>): T =
        value
}