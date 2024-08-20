package net.essentuan.erika.framework.db.api

import kotlin.reflect.KProperty

interface Change<IN, OUT> : Collection<Change<OUT, *>> {
    fun applyTo(obj: IN)

    interface Keyed<IN, OUT> : Change<IN, OUT> {
        val kotlin: KProperty<*>
    }
}