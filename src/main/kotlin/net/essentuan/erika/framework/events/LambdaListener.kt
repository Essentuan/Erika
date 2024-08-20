package net.essentuan.erika.framework.events

import net.essentuan.esl.Rating

class LambdaListener<T: Event>(
    id: String,
    event: Class<T>,
    phase: Rating,
    receiveCancelled: Boolean,
    filter: Class<*>,
    val listener: (T) -> Unit
) : AbstractListener<T>(id, event, phase, receiveCancelled, filter) {
    override fun on(event: T) = listener(event)
}