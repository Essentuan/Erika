package net.essentuan.erika.kord.framework.commands.annotations

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class Requires(val value: String)
