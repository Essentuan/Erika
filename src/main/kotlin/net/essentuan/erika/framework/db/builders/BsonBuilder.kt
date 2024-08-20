package net.essentuan.erika.framework.db.builders

import net.essentuan.erika.framework.db.bson
import net.essentuan.erika.framework.db.eslKey
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Model
import net.essentuan.esl.model.Model.Companion.export
import net.essentuan.esl.reflections.extensions.simpleString
import org.bson.BsonArray
import org.bson.BsonBoolean
import org.bson.BsonDateTime
import org.bson.BsonDecimal128
import org.bson.BsonDocument
import org.bson.BsonDouble
import org.bson.BsonInt32
import org.bson.BsonInt64
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.BsonUndefined
import org.bson.BsonValue
import org.bson.conversions.Bson
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import java.util.Date
import java.util.UUID
import kotlin.collections.set
import kotlin.reflect.KProperty

@JvmInline
value class BsonBuilder(
    val doc: BsonDocument = BsonDocument()
) {
    infix fun String.to(value: Any?) {
        doc[this] = value.bson()
    }

    infix fun KProperty<*>.to(value: Any?) {
        doc[this.eslKey] = value.bson()
    }

    inline operator fun String.invoke(block: BsonBuilder.() -> Unit) {
        doc[this] = BsonBuilder().apply(block).doc
    }

    inline operator fun KProperty<*>.invoke(block: BsonBuilder.() -> Unit) {
        doc[this.eslKey] = BsonBuilder().apply(block).doc
    }
}

fun Any?.bson(): BsonValue = when(this) {
    null -> BsonUndefined()
    is BsonValue -> this
    is String -> BsonString(this)
    is Boolean -> BsonBoolean(this)
    is Int -> BsonInt32(this)
    is Long -> BsonInt64(this)
    is Float -> BsonDouble(this.toDouble())
    is Double -> BsonDouble(this)
    is Decimal128 -> BsonDecimal128(this)
    is Bson -> this.toBsonDocument()
    is Json -> this.bson().toBsonDocument()
    is Model<*> -> this.export().run {
        return if (this is Json)
            this.bson().toBsonDocument()
        else
            Json(this).bson().toBsonDocument()
    }
    is Date -> BsonDateTime(this.time)
    is ObjectId -> BsonObjectId(this)
    is Array<*> -> BsonArray(this.map(Any?::bson))
    is Iterable<*> -> BsonArray(this.map(Any?::bson))
    is Iterator<*> -> BsonArray(this.asSequence().map(Any?::bson).toList())
    is Sequence<*> -> BsonArray(this.map(Any?::bson).toList())
    is UUID -> BsonString(toString())
    else -> throw IllegalArgumentException("Could not convert ${this.javaClass.simpleString()} to bson!")
}

inline fun bson(block: BsonBuilder.() -> Unit): Bson =
    BsonBuilder().apply(block).doc