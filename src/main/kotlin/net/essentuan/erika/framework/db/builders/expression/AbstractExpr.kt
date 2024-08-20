package net.essentuan.erika.framework.db.builders.expression

import net.essentuan.erika.framework.db.builders.BsonBuilder
import net.essentuan.erika.framework.db.builders.bson
import net.essentuan.erika.framework.db.eslKey
import net.essentuan.esl.Result
import net.essentuan.esl.encoding.encode
import net.essentuan.esl.ifPresent
import net.essentuan.esl.result
import org.bson.conversions.Bson
import kotlin.reflect.KProperty

class Count(val key: String, val count: Int)

abstract class AbstractExpr {
    val abs = Single("\$abs")
    val ceil = Single("\$ceil")
    val floor = Single("\$floor")
    val e = E()
    val ln = Single("\$ln")
    val sqrt = Single("\$sqrt")

    fun first(n: Int = 1) = Count("\$firstN", n)
    fun last(n: Int = 1) = Count("\$lastN", n)
    fun min(n: Int = 1) = Count("\$minN", n)
    fun max(n: Int = 1) = Count("\$maxN", n)

    infix fun Count.of(value: Any?): Bson = bson {
        key {
            "input" to value(value)
            "n" to count
        }
    }

    infix fun Any?.plus(obj: Any?) = sum {
        +this
        +obj
    }

    fun sum(init: Grouping.() -> Unit) = expr("\$add", init)

    infix fun Any?.sub(obj: Any?) = expr("\$subtract", this, obj)

    infix fun Any?.mul(obj: Any?) = expr("\$multiply", this, obj)

    fun mul(init: Grouping.() -> Unit) = expr("\$multiply", init)

    infix fun Any?.div(obj: Any?) = expr("\$divide", this, obj)

    infix fun Any?.log(obj: Any?) = expr("\$log", this, obj)

    infix fun Any?.mod(obj: Any?) = expr("\$mod", this, obj)

    infix fun Any?.raise(obj: Any?) = expr("\$pow", this, obj)

    infix fun Any?.trunc(obj: Any?) = expr("\$trunc", this, obj)

    fun bitAnd(init: Grouping.() -> Unit) = expr("\$bitAnd", init)

    fun bitNot(init: Grouping.() -> Unit) = expr("\$bitNot", init)

    fun bitOr(init: Grouping.() -> Unit) = expr("\$bitOr", init)

    fun xor(init: Grouping.() -> Unit) = expr("\$bitXor", init)

    infix fun Any?.and(obj: Any?) = expr("\$and", this, obj)

    fun and(init: Grouping.() -> Unit) = expr("\$and", init)

    fun or(init: Grouping.() -> Unit) = expr("\$or", init)

    fun not(init: AbstractExpr.() -> Any?): Bson {
        val result = expr(init)

        return expr("\$not", result)
    }

    infix fun Any?.compare(obj: Any?) = expr("\$cmp", this, obj)

    infix fun Any?.eq(obj: Any?) = expr("\$eq", this, obj)

    infix fun Any?.neq(obj: Any?) = expr("\$neq", this, obj)

    infix fun Any?.gt(obj: Any?) = expr("\$gt", this, obj)

    infix fun Any?.gte(obj: Any?) = expr("\$gte", this, obj)

    infix fun Any?.lt(obj: Any?) = expr("\$lt", this, obj)

    infix fun Any?.lte(obj: Any?) = expr("\$lte", this, obj)

    infix fun Any?.cond(init: Cond.() -> Unit) = Cond(this).apply(init).build()

    fun ifNull(init: Grouping.() -> Any?): Bson {
        val group = Grouping()
        val result = init(group)

        return expr("\$ifNull", *group.values.toTypedArray(), result)
    }

    fun switch(init: Switch.() -> Unit) = Switch().apply(init).build()

    fun concat(vararg arrays: Any?) = expr("\$concatArrays", *arrays)

    fun push(value: Any?) =
        expr("\$push", value)

    fun push(block: BsonBuilder.() -> Unit) =
        push(BsonBuilder().apply(block).doc)

    fun Any?.toArray() = expr("\$objectToArray", this)

    @JvmInline
    value class Grouping(val values: MutableList<Any?> = mutableListOf()) {
        operator fun Any?.unaryPlus() {
            values += this
        }
    }

    inner class Single(
        private val key: String,
        private val op: BsonBuilder.(Any?) -> Unit = { key to value(it) }
    ) {
        infix fun of(obj: Any?) = bson { op(this, obj) }
    }

    inner class E {
        infix fun raise(obj: Any?) = expr("\$exp", obj)
    }

    inner class Cond(val cond: Any?) {
        private var trueCase: Any? = null
        private var falseCase: Any? = null

        operator fun Boolean.invoke(init: AbstractExpr.() -> Any?) {
            if (this)
                trueCase = expr(init)
            else
                falseCase = expr(init)
        }

        fun build(): Bson = bson {
            "\$cond" {
                "if" to value(cond)
                "then" to value(trueCase)
                "else" to value(falseCase)
            }
        }
    }

    inner class Switch {
        private val branches = mutableListOf<Bson>()
        private var default: Result<Any?> = Result.empty()

        fun case(init: AbstractExpr.() -> Any?): Case = case(this@AbstractExpr.run(init))

        fun case(obj: Any?): Case = Case(obj)

        fun default(init: AbstractExpr.() -> Any?) {
            default = init(this@AbstractExpr).result()
        }

        inner class Case(val case: Any?) {
            infix fun then(obj: Any?) {
                branches+= bson {
                    "case" to value(case)
                    "then" to value(obj)
                }
            }
        }

        fun build() = bson {
            "\$switch" {
                "branches" to branches

                default.ifPresent {
                    "default" to it
                }
            }
        }
    }

    @JvmInline
    value class ArrayExpr(val array: Any?) {
        infix operator fun get(obj: Any?) = Expr.expr("\$arrayElemAt", array, obj)

        fun toObject() = Expr.expr("\$arrayToObject", array)

        fun concat(vararg arrays: Any?): ArrayExpr = ArrayExpr(Expr.concat(array, *arrays))

        fun filter(init: Filter.() -> Any?): ArrayExpr {
            val filter = Filter(this)

            return ArrayExpr(filter.build(filter.run(init)))
        }

        fun first(obj: Any?) = ArrayExpr(bson {
            "\$firstN" {
                "n" to Expr.value(obj)
                "input" to Expr.value(array)
            }
        })

        fun last(obj: Any?) = ArrayExpr(bson {
            "\$lastN" {
                "n" to Expr.value(obj)
                "input" to Expr.value(array)
            }
        })

        infix fun contains(obj: Any?) = Expr.expr("\$in", obj, array)

        fun indexOf(init: IndexOf.() -> Any?): Any? {
            val indexOf = IndexOf(this)

            return indexOf.build(indexOf.run(init))
        }

        fun map(init: Map.() -> Any?): ArrayExpr {
            val map = Map(this)

            return ArrayExpr(map.build(map.run(init)))
        }

        fun maxN(elements: AbstractExpr.() -> Any?): ArrayExpr = ArrayExpr(bson {
            "\$maxN" {
                "n" to Expr.value(Expr.expr(elements))
                "input" to Expr.value(array)
            }
        })

        fun minN(elements: AbstractExpr.() -> Any?): ArrayExpr = ArrayExpr(bson {
            "\$minN" {
                "n" to Expr.value(Expr.expr(elements))
                "input" to Expr.value(array)
            }
        })

        fun reversed(): ArrayExpr = ArrayExpr(Expr.expr("\$reverseArray", array))

        class Filter(val expr: ArrayExpr) : AbstractExpr() {
            private var limitN: Result<Any?> = Result.empty()
            private lateinit var element: String

            operator fun getValue(value: Any?, property: KProperty<*>): String {
                element = "$\$${property.name}"

                return element
            }

            val limit = Limit()

            fun build(cond: Any?) = bson {
                "\$filter" {
                    "input" to value(expr.array)
                    "as" to element.removePrefix("$$")
                    "cond" to value(cond)

                    limitN.ifPresent {
                        "limit" to it
                    }
                }
            }

            inner class Limit {
                infix fun to(obj: Any?) {
                    limitN = obj.result()
                }
            }
        }

        class Map(val expr: ArrayExpr) : AbstractExpr() {
            private lateinit var element: String

            operator fun getValue(value: Any?, property: KProperty<*>): String {
                element = "$\$${property.name}"

                return element
            }

            fun build(cond: Any?) = bson {
                "\$map" {
                    "input" to value(expr.array)
                    "as" to element.removePrefix("$$")
                    "in" to value(cond)
                }
            }
        }

        class IndexOf(val expr: ArrayExpr) : AbstractExpr() {
            private var startN: Result<Any?> = Result.empty()
            private var endN: Result<Any?> = Result.empty()

            val start = Start()
            val end = End()

            fun build(element: Any?): Any {
                val args = ArrayList<Any?>(4)

                args+= expr.array
                args+= element

                startN.ifPresent {
                    args+= it

                    endN.ifPresent { end ->
                        args+= end
                    }
                }

                return Expr.expr("\$indexOfArray", *args.toTypedArray())
            }

            inner class Start {
                infix fun at(obj: Any?) {
                    startN = obj.result()
                }
            }

            inner class End {
                infix fun at(obj: Any?) {
                    endN = obj.result()
                }
            }
        }

    }

    internal fun expr(key: String, iterable: Iterable<Any?>) = bson {
        key to iterable.map(this@AbstractExpr::value).run { if (this.size == 1) this[0] else this }
    }

    internal fun expr(key: String, vararg values: Any?): Bson = expr(key, values.asIterable())

    internal fun expr(key: String, init: Grouping.() -> Unit): Bson = expr(key, Grouping().apply(init).values)

    val Any?.literal: Bson
        get() = bson {
            "\$literal" to this@literal.encode()
        }

    val Any?.array: ArrayExpr
        get() = ArrayExpr(this)

    internal fun value(obj: Any?): Any? {
        return when (obj) {
            is String -> obj
            is KProperty<*> -> "\$${obj.eslKey}"
            is ArrayExpr -> value(obj.array)
            is Number, is Bson -> obj
            null -> null
            else -> obj.encode()
        }
    }

    fun expr(init: AbstractExpr.() -> Any?): Any? = this@AbstractExpr.run(init)
}