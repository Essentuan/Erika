package net.essentuan.erika.kord.framework.commands.annotations

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class Blacklist(vararg val value: Long)