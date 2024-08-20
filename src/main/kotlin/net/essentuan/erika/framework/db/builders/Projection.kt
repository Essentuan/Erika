package net.essentuan.erika.framework.db.builders

import net.essentuan.erika.framework.db.bson
import net.essentuan.erika.framework.db.builders.expression.Expr
import net.essentuan.erika.framework.db.eslKey
import net.essentuan.esl.json.Json
import org.bson.conversions.Bson
import kotlin.reflect.KProperty

@JvmInline
value class Projection(
    val json: Json = Json()
) {
    inline fun String.to(block: Expr.() -> Any?) {
        json[this] = Expr.run(block)
    }

    fun String.to(array: Array<Any?>) {
        json[this] = array.toList()
    }

    fun String.to(list: List<Any?>) {
        json[this] = list
    }

    inline operator fun String.invoke(block: Projection.() -> Unit) {
        json[this] = Projection().apply(block)
    }

    operator fun String.unaryPlus() {
        json[this] = 1
    }

    operator fun KProperty<*>.unaryPlus() = +eslKey

    operator fun String.unaryMinus() {
        json[this] = 0
    }

    operator fun KProperty<*>.unaryMinus() = -eslKey

    fun excludeId() = -"_id"

    fun build(): Bson =
        json.bson()
}