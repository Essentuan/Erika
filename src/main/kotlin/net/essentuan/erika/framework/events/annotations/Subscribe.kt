package net.essentuan.erika.framework.events.annotations

import net.essentuan.esl.Rating

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Subscribe(
    val value: Rating = Rating.NORMAL
)
