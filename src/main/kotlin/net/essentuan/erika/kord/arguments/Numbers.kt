package net.essentuan.erika.kord.arguments

import dev.kord.common.Locale
import dev.kord.rest.builder.interaction.BaseInputChatBuilder
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.number
import net.essentuan.erika.kord.framework.commands.CommandInteraction
import net.essentuan.erika.kord.framework.commands.KordArgument
import net.essentuan.esl.Result
import net.essentuan.esl.reflections.extensions.get
import net.essentuan.esl.result
import kotlin.reflect.KParameter

annotation class IntMin(val value: Int)
annotation class IntMax(val value: Int)

class IntArgument(
    param: KParameter
) : KordArgument<Int>(param) {
    private val min = param[IntMin::class]?.value?.toLong() ?: Int.MIN_VALUE.toLong()
    private val max = param[IntMax::class]?.value?.toLong() ?: Int.MAX_VALUE.toLong()

    override fun BaseInputChatBuilder.create() {
        integer(id, description) {
            required = !param.isOptional

            minValue = min
            maxValue = max

            Locale.ALL.forEach {name(it, name) }
        }
    }

    override suspend fun CommandInteraction.value(): Result<Int?> =
        command.integers[id]?.toInt().result()
}

annotation class LongMin(val value: Long)
annotation class LongMax(val value: Long)

class LongArgument(
    param: KParameter
) : KordArgument<Long>(param) {
    private val min = param[LongMin::class]?.value ?: Long.MIN_VALUE
    private val max = param[LongMax::class]?.value ?: Long.MAX_VALUE

    override fun BaseInputChatBuilder.create() {
        integer(id, description) {
            required = !param.isOptional

            minValue = min
            maxValue = max

            Locale.ALL.forEach {name(it, name) }
        }
    }

    override suspend fun CommandInteraction.value(): Result<Long?> =
        command.integers[id].result()
}

annotation class FloatMin(val value: Float)
annotation class FloatMax(val value: Float)

class FloatArgument(
    param: KParameter
) : KordArgument<Float>(param) {
    private val min = param[FloatMin::class]?.value?.toDouble() ?: Float.MIN_VALUE.toDouble()
    private val max = param[FloatMax::class]?.value?.toDouble() ?: Float.MAX_VALUE.toDouble()

    override fun BaseInputChatBuilder.create() {
        number(id, description) {
            required = !param.isOptional

            minValue = min
            maxValue = max

            Locale.ALL.forEach {name(it, name) }
        }
    }

    override suspend fun CommandInteraction.value(): Result<Float?> =
        command.numbers[id]?.toFloat().result()
}

annotation class DoubleMin(val value: Double)
annotation class DoubleMax(val value: Double)

class DoubleArgument(
    param: KParameter
) : KordArgument<Double>(param) {
    private val min = param[DoubleMin::class]?.value ?: Double.MIN_VALUE
    private val max = param[DoubleMax::class]?.value ?: Double.MAX_VALUE

    override fun BaseInputChatBuilder.create() {
        number(id, description) {
            required = !param.isOptional

            minValue = min
            maxValue = max

            Locale.ALL.forEach {name(it, name) }
        }
    }

    override suspend fun CommandInteraction.value(): Result<Double?> =
        command.numbers[id].result()
}