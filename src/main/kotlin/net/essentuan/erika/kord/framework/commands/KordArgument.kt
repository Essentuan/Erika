package net.essentuan.erika.kord.framework.commands

import com.google.gson.internal.Primitives
import dev.kord.rest.builder.interaction.BaseInputChatBuilder
import net.essentuan.erika.framework.annotation.Description
import net.essentuan.erika.kord.framework.commands.annotations.Named
import net.essentuan.esl.Result
import net.essentuan.esl.collections.maps.registry
import net.essentuan.esl.reflections.Reflections
import net.essentuan.esl.reflections.Types.Companion.concreteTypes
import net.essentuan.esl.reflections.extensions.*
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

abstract class KordArgument<T : Any> protected constructor(
    val param: KParameter
) {
    val name: String = param[Named::class]?.value ?: param.name!!.lowercase()
    val id: String = name.lowercase()

    val description: String = param[Description::class]?.value ?: "No Description"

    val type: Class<*> = this::class.typeInformationOf(KordArgument::class)["T"]!!

    abstract fun BaseInputChatBuilder.create()

    abstract suspend fun CommandInteraction.value(): Result<T?>

    companion object {
        private val args by lazy {
            val registry =
                registry<Class<*>, KClass<KordArgument<*>>> {
                    it.visit().map { c -> Primitives.wrap(c) }
                }

            Reflections.types
                .subtypesOf(KordArgument::class)
                .concreteTypes()
                .forEach {
                    registry[it.typeInformationOf(KordArgument::class)["T"]!!] = it
                }

            registry
        }

        operator fun invoke(parameter: KParameter): KordArgument<Any> {
            val type = parameter.type.javaType.classOf()

            @Suppress("UNCHECKED_CAST")
            return (args[type] ?: throw IllegalArgumentException("Unsupported type '${type.simpleString()}'!"))
                .primaryConstructor!!
                .call(parameter) as KordArgument<Any>
        }
    }
}