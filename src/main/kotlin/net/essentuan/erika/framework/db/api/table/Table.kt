package net.essentuan.erika.framework.db.api.table

import com.mongodb.client.model.Collation
import com.mongodb.reactivestreams.client.MongoCollection
import net.essentuan.erika.framework.db.api.table.schema.Schema
import net.essentuan.erika.framework.db.`object`.BsonModel
import net.essentuan.erika.framework.db.table.adapters.BsonModelAdapter
import net.essentuan.erika.framework.db.table.adapters.DocumentAdapter
import net.essentuan.erika.framework.db.table.adapters.JsonAdapter
import net.essentuan.erika.framework.db.table.adapters.JsonTypeAdapter
import net.essentuan.erika.framework.db.table.adapters.ModelAdapter
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.type.AnyJson
import net.essentuan.esl.json.type.JsonType
import net.essentuan.esl.model.Model
import net.essentuan.esl.reflections.extensions.extends
import net.essentuan.esl.reflections.extensions.simpleString
import net.essentuan.esl.reflections.extensions.typeInformationOf
import org.bson.Document
import kotlin.reflect.KClass

interface Table<T> {
    val name: String
    val size: Long
    val capacity: Long

    val collation: Collation
    val schema: Schema

    val type: Class<T>
    val adapter: Adapter<T>

    val mongo: MongoCollection<Document>

    fun <U> with(adapter: Adapter<U>): Table<U>

    fun <U> `as`(cls: Class<U>) = with(Adapter(cls))

    infix fun <U : Any> `as`(cls: KClass<U>) = `as`(cls.java)

    interface Adapter<T> {
        val type: Class<T>

        fun read(doc: Document): T

        fun write(obj: T): Document

        companion object {
            @Suppress("UNCHECKED_CAST")
            operator fun <T> invoke(cls: Class<T>): Adapter<T> {
                return when {
                    cls extends Document::class -> DocumentAdapter
                    cls extends Json::class -> JsonAdapter
                    cls extends JsonType::class -> JsonTypeAdapter(cls as Class<AnyJson>)
                    cls extends BsonModel::class -> BsonModelAdapter(cls as Class<BsonModel>)
                    cls extends Model::class -> ModelAdapter(
                        cls as Class<Model<AnyJson>>,
                        cls.typeInformationOf(Model::class)["OUT"]!! as Class<AnyJson>
                    )

                    else -> error("Could not find adapter for ${cls.simpleString()}!")
                } as Adapter<T>
            }
        }
    }
}