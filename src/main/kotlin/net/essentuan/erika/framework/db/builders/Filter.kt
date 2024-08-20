package net.essentuan.erika.framework.db.builders

import com.mongodb.DBRef
import com.mongodb.client.model.Filters
import com.mongodb.client.model.TextSearchOptions
import net.essentuan.erika.framework.db.builders.expression.AbstractExpr
import net.essentuan.erika.framework.db.builders.expression.Expr
import net.essentuan.erika.framework.db.eslKey
import net.essentuan.erika.framework.db.eslProp
import net.essentuan.esl.encoding.Encoder
import net.essentuan.esl.encoding.encode
import net.essentuan.esl.iteration.extensions.iterable
import net.essentuan.esl.iteration.extensions.map
import net.essentuan.esl.json.type.JsonType
import net.essentuan.esl.reflections.extensions.extends
import net.essentuan.esl.time.span.TimeSpan
import org.bson.BsonDocument
import org.bson.BsonType
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistry
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import java.util.Date
import java.util.regex.Pattern
import java.util.stream.Stream
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class Filter(private val joiner: (Iterable<Bson>) -> Bson = Filters::and) : Bson {
    private val filters: MutableList<Bson> = mutableListOf()

    private fun Any.bson(): Any = when (val obj = this) {
        is DBRef -> bson {
            "\$ref" to obj.collectionName
            "\$id" to obj.id

            if (obj.databaseName != null)
                "\$db" to obj.databaseName
        }
        else -> this
    }

    fun then(filter: () -> Bson) {
        filters.add(filter())
    }

    @Suppress("UNCHECKED_CAST")
    fun KProperty<*>.then(obj: Any, filter: String.(Any) -> Bson) = then filter@{
        val property = eslProp ?: return@filter filter(name, obj.encode()!!.bson())

        return@filter filter(
            property.key ?: name,
            (Encoder(
                property.type.cls,
                property,
                *property.type.args
            ) as Encoder<Any, Any>).encode(
                obj,
                emptySet(),
                property.type.cls,
                property,
                *property.type.args
            )!!.bson()
        )
    }

    fun String.then(obj: Any, filter: String.(Any) -> Bson) = then { filter(this, obj.encode()!!.bson()) }

    infix fun String.eq(obj: Any) = then(obj, Filters::eq)

    infix fun KProperty<*>.eq(obj: Any) = then(obj, Filters::eq)

    infix fun String.neq(obj: Any) = then(obj, Filters::ne)

    infix fun KProperty<*>.neq(obj: Any) = then(obj, Filters::ne)

    infix fun String.gt(obj: Any) = then(obj, Filters::gt)

    infix fun KProperty<*>.gt(obj: Any) = then(obj, Filters::gt)

    infix fun String.gte(obj: Any) = then(obj, Filters::gte)

    infix fun KProperty<*>.gte(obj: Any) = then(obj, Filters::gte)

    infix fun String.lt(obj: Any) = then(obj, Filters::lt)

    infix fun KProperty<*>.lt(obj: Any) = then(obj, Filters::lt)

    infix fun String.lte(obj: Any) = then(obj, Filters::lte)

    infix fun KProperty<*>.lte(obj: Any) = then(obj, Filters::lte)

    operator fun Iterable<*>.contains(str: String): Boolean = then {
        Filters.`in`(str, this.iterator()
            .map { it.encode()?.bson() }
            .iterable())
    } != Unit

    @Suppress("UNCHECKED_CAST")
    operator fun Iterable<*>.contains(prop: KProperty<*>): Boolean {
        val property = prop.eslProp

        val encode: (Any?) -> Any? = run {
            if (property == null)
                return@run { it.encode()?.bson() }

            return@run {
                when (it) {
                    null -> null
                    is ObjectId -> it
                    else -> (Encoder(
                        property.type.cls,
                        property,
                        *property.type.args
                    ) as Encoder<Any, Any>).encode(
                        it,
                        emptySet(),
                        property.type.cls,
                        property,
                        *property.type.args
                    )!!.bson()
                }
            }
        }

        return then {
            Filters.`in`(property?.key ?: prop.name, this.iterator()
                .map { encode(it)?.bson() }
                .iterable())
        } != Unit
    }

    operator fun Iterator<*>.contains(str: String): Boolean = str in this.iterable()

    operator fun Iterator<*>.contains(prop: KProperty<*>): Boolean = prop in this.iterable()


    operator fun Stream<*>.contains(str: String): Boolean = str in this.iterator()

    operator fun Stream<*>.contains(prop: KProperty<*>): Boolean = prop in this.iterable()

    operator fun Sequence<*>.contains(str: String): Boolean = str in this.iterator()

    operator fun Sequence<*>.contains(prop: KProperty<*>): Boolean = prop in this.iterator()

    operator fun TimeSpan.contains(str: String): Boolean = and {
        str gte this@contains.start
        str lte (this@contains.end ?: Date())
    } != Unit

    operator fun TimeSpan.contains(prop: KProperty<*>): Boolean = and {
        prop gte this@contains.start
        prop lte (this@contains.end ?: Date())
    } != Unit

    operator fun ClosedRange<*>.contains(str: String): Boolean = and {
        str gte this@contains.start
        str lte this@contains.endInclusive
    } != Unit

    operator fun ClosedRange<*>.contains(prop: KProperty<*>): Boolean = and {
        prop gte this@contains.start
        prop lte this@contains.endInclusive
    } != Unit

    infix fun String.nin(obj: Any) = then(obj, Filters::nin)

    infix fun KProperty<*>.nin(obj: Any) = then(obj, Filters::nin)

    infix fun String.nin(obj: TimeSpan) = or {
        this@nin lt obj.start
        this@nin gt (obj.end ?: Date())
    } != Unit

    infix fun KProperty<*>.nin(obj: TimeSpan) = or {
        this@nin lt obj.start
        this@nin gt (obj.end ?: Date())
    } != Unit

    infix fun ClosedRange<*>.nin(str: String): Boolean = or {
        str lt this@nin.start
        str gt this@nin.endInclusive
    } != Unit

    infix fun ClosedRange<*>.nin(prop: KProperty<*>): Boolean = or {
        prop lt this@nin.start
        prop gt this@nin.endInclusive
    } != Unit

    fun and(init: Filter.() -> Unit) = then { Filter().apply(init) }

    fun or(init: Filter.() -> Unit) = then { Filter(Filters::or).apply(init) }

    fun not(init: Filter.() -> Unit) = then { Filter { Filters.not(Filters.and(it)) }.apply(init) }

    fun nor(init: Filter.() -> Unit) = then { Filter { Filters.nor(Filters.and(it)) }.apply(init) }

    fun String.exists() = then { Filters.exists(this) }

    fun KProperty<*>.exists() = then { Filters.exists(eslKey) }

    infix fun String.instanceof(type: BsonType) {
        this.instanceof(type)
    }

    fun String.instanceof(vararg types: BsonType) = then {
        if (types.isEmpty())
            Document()
        else if (types.size == 1)
            Filters.type(this, types[0])
        else
            Filters.or(*types.map { Filters.type(this, it) }.toTypedArray())
    }

    infix fun String.instanceof(obj: KClass<*>) {
        this.instanceof(
            *when {
                obj extends String::class -> arrayOf(BsonType.STRING)
                obj extends Number::class -> arrayOf(BsonType.DOUBLE, BsonType.INT32, BsonType.INT64)
                obj extends JsonType::class || obj extends Document::class -> arrayOf(BsonType.DOCUMENT)
                obj extends Array::class || obj extends List::class -> arrayOf(BsonType.ARRAY)
                obj extends ObjectId::class -> arrayOf(BsonType.OBJECT_ID)
                obj extends Boolean::class -> arrayOf(BsonType.BOOLEAN)
                obj extends Date::class -> arrayOf(BsonType.DATE_TIME)
                obj extends Pattern::class -> arrayOf(BsonType.REGULAR_EXPRESSION)
                else -> arrayOf(BsonType.UNDEFINED)
            }
        )
    }

    infix fun KProperty<*>.instanceof(type: BsonType) = eslKey instanceof type

    fun KProperty<*>.instanceof(vararg types: BsonType) = eslKey.instanceof(*types)

    infix fun KProperty<*>.instanceof(obj: KClass<*>) = eslKey instanceof obj

    infix fun String.mod(num: Number): Mod = Mod(this, num.toLong())

    infix fun KProperty<*>.mod(num: Number): Mod = Mod(eslKey, num.toLong())

    infix fun String.matches(regex: String) = then { Filters.regex(this, regex) }

    infix fun String.matches(regex: Pattern) = then { Filters.regex(this, regex.pattern()) }

    infix fun KProperty<*>.matches(regex: String) = eslKey matches regex

    infix fun KProperty<*>.matches(regex: Pattern) = eslKey matches regex

    fun text(init: TextSearch.() -> Unit) = then { TextSearch().apply(init) }

    infix fun String.exec(code: () -> String) = then { Filters.where(code()) }

    fun expr(expr: AbstractExpr.() -> Any?) = then { Filters.expr(Expr.value(Expr.expr(expr))!!) }

    override fun <TDocument : Any?> toBsonDocument(
        documentClass: Class<TDocument>?,
        codecRegistry: CodecRegistry?
    ): BsonDocument = if (filters.isEmpty())
        BsonDocument()
    else if (filters.size == 1)
        filters.first().toBsonDocument(documentClass, codecRegistry)
    else
        joiner(filters).toBsonDocument(documentClass, codecRegistry)

    inner class Mod(val key: String, val div: Long) {
        infix fun eq(other: Number) = then { Filters.mod(key, div, other.toLong()) }
    }

    class TextSearch : Bson {
        private val search = StringBuilder()
        private val options = TextSearchOptions()

        operator fun String.unaryPlus() {
            search.append(this)
        }

        operator fun CharSequence.unaryPlus() {
            search.append(this)
        }

        operator fun Char.unaryPlus() {
            search.append(this)
        }

        operator fun Phrase.unaryPlus() {
            search.append(this.toString())
        }

        fun phrase(init: Phrase.() -> Unit): Phrase = Phrase().apply(init)

        fun language(lang: String) {
            options.language(lang)
        }

        fun caseSensitive(enabled: Boolean = true) {
            options.caseSensitive(enabled)
        }

        fun diacriticSensitive(enabled: Boolean = true) {
            options.diacriticSensitive(enabled)
        }

        class Phrase {
            private val phrase: StringBuilder = StringBuilder().append('"')

            operator fun String.unaryPlus() {
                phrase.append(this)
            }

            operator fun CharSequence.unaryPlus() {
                phrase.append(this)
            }

            operator fun Char.unaryPlus() {
                phrase.append(this)
            }

            override fun toString(): String {
                return phrase.append('"').toString()
            }
        }

        override fun <TDocument : Any?> toBsonDocument(
            documentClass: Class<TDocument>?,
            codecRegistry: CodecRegistry?
        ): BsonDocument = Filters.text(search.toString(), options).toBsonDocument(documentClass, codecRegistry)
    }
}

inline fun filter(block: Filter.() -> Unit): Bson = Filter().apply(block)

/*
Unimplemented
fun <TExpression> expr(expression: TExpression): Bson {
return SimpleEncodingFilter("\$expr", expression)
}

@SafeVarargs
fun <TItem> all(fieldName: String?, vararg values: TItem): Bson {
return all(fieldName, Arrays.asList(*values))
}

fun <TItem> all(fieldName: String?, values: Iterable<TItem>?): Bson {
return IterableOperatorFilter(fieldName, "\$all", values)
}

fun elemMatch(fieldName: String?, filter: Bson): Bson {
return object : Bson {
override fun <TDocument> toBsonDocument(
documentClass: Class<TDocument>,
codecRegistry: CodecRegistry
): BsonDocument {
return BsonDocument(
fieldName,
BsonDocument("\$elemMatch", filter.toBsonDocument(documentClass, codecRegistry))
)
}
}
}

fun size(fieldName: String?, size: Int): Bson {
return OperatorFilter("\$size", fieldName, size)
}

fun bitsAllClear(fieldName: String?, bitmask: Long): Bson {
return OperatorFilter("\$bitsAllClear", fieldName, bitmask)
}

fun bitsAllSet(fieldName: String?, bitmask: Long): Bson {
return OperatorFilter("\$bitsAllSet", fieldName, bitmask)
}

fun bitsAnyClear(fieldName: String?, bitmask: Long): Bson {
return OperatorFilter("\$bitsAnyClear", fieldName, bitmask)
}

fun bitsAnySet(fieldName: String?, bitmask: Long): Bson {
return OperatorFilter("\$bitsAnySet", fieldName, bitmask)
}

fun geoWithin(fieldName: String?, geometry: Geometry): Bson {
return GeometryOperatorFilter("\$geoWithin", fieldName, geometry)
}

fun geoWithin(fieldName: String?, geometry: Bson): Bson {
return GeometryOperatorFilter("\$geoWithin", fieldName, geometry)
}

fun geoWithinBox(
fieldName: String?, lowerLeftX: Double, lowerLeftY: Double, upperRightX: Double,
upperRightY: Double
): Bson {
val box = BsonDocument(
"\$box",
BsonArray(
Arrays.asList(
BsonArray(
Arrays.asList(
BsonDouble(lowerLeftX),
BsonDouble(lowerLeftY)
)
),
BsonArray(
Arrays.asList(
BsonDouble(upperRightX),
BsonDouble(upperRightY)
)
)
)
)
)
return OperatorFilter("\$geoWithin", fieldName, box)
}

fun geoWithinPolygon(fieldName: String?, points: List<List<Double?>>): Bson {
val pointsArray = BsonArray(points.size)
for (point in points) {
pointsArray.add(BsonArray(Arrays.asList(BsonDouble(point[0]!!), BsonDouble(point[1]!!))))
}
val polygon = BsonDocument("\$polygon", pointsArray)
return OperatorFilter("\$geoWithin", fieldName, polygon)
}

fun geoWithinCenter(fieldName: String?, x: Double, y: Double, radius: Double): Bson {
val center = BsonDocument(
"\$center",
BsonArray(
Arrays.asList(
BsonArray(
Arrays.asList(
BsonDouble(x),
BsonDouble(y)
)
),
BsonDouble(radius)
)
)
)
return OperatorFilter("\$geoWithin", fieldName, center)
}

fun geoWithinCenterSphere(fieldName: String?, x: Double, y: Double, radius: Double): Bson {
val centerSphere = BsonDocument(
"\$centerSphere",
BsonArray(
Arrays.asList(
BsonArray(
Arrays.asList(
BsonDouble(x),
BsonDouble(y)
)
),
BsonDouble(radius)
)
)
)
return OperatorFilter("\$geoWithin", fieldName, centerSphere)
}

fun geoIntersects(fieldName: String?, geometry: Bson): Bson {
return GeometryOperatorFilter("\$geoIntersects", fieldName, geometry)
}

fun geoIntersects(fieldName: String?, geometry: Geometry): Bson {
return GeometryOperatorFilter("\$geoIntersects", fieldName, geometry)
}

fun near(
fieldName: String?, geometry: Point, @Nullable maxDistance: Double?,
@Nullable minDistance: Double?
): Bson {
return GeometryOperatorFilter("\$near", fieldName, geometry, maxDistance, minDistance)
}

fun near(
fieldName: String?, geometry: Bson, @Nullable maxDistance: Double?,
@Nullable minDistance: Double?
): Bson {
return GeometryOperatorFilter("\$near", fieldName, geometry, maxDistance, minDistance)
}

fun near(
fieldName: String?, x: Double, y: Double, @Nullable maxDistance: Double?,
@Nullable minDistance: Double?
): Bson {
return Filters.createNearFilterDocument(fieldName, x, y, maxDistance, minDistance, "\$near")
}

fun nearSphere(
fieldName: String?, geometry: Point, @Nullable maxDistance: Double?,
@Nullable minDistance: Double?
): Bson {
return GeometryOperatorFilter("\$nearSphere", fieldName, geometry, maxDistance, minDistance)
}

fun nearSphere(
fieldName: String?, geometry: Bson, @Nullable maxDistance: Double?,
@Nullable minDistance: Double?
): Bson {
return GeometryOperatorFilter("\$nearSphere", fieldName, geometry, maxDistance, minDistance)
}

fun nearSphere(
fieldName: String?, x: Double, y: Double, @Nullable maxDistance: Double?,
@Nullable minDistance: Double?
): Bson {
return Filters.createNearFilterDocument(fieldName, x, y, maxDistance, minDistance, "\$nearSphere")
}

fun jsonSchema(schema: Bson): Bson {
return SimpleEncodingFilter("\$jsonSchema", schema)
}
*/