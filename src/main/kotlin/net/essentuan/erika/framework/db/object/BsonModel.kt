package net.essentuan.erika.framework.db.`object`

import net.essentuan.erika.framework.db.Database
import net.essentuan.erika.framework.db.Database.via
import net.essentuan.erika.framework.db.Pointer
import net.essentuan.erika.framework.db.api.table.Table
import net.essentuan.erika.framework.db.`object`.Memory.load
import net.essentuan.esl.Rating
import net.essentuan.esl.coroutines.blocking
import net.essentuan.esl.delegates.lateinit
import net.essentuan.esl.encoding.AbstractEncoder
import net.essentuan.esl.encoding.Encoder
import net.essentuan.esl.encoding.Provider
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.type.AnyJson
import net.essentuan.esl.model.Descriptor
import net.essentuan.esl.model.Model
import net.essentuan.esl.model.Model.Companion.export
import net.essentuan.esl.model.annotations.Override
import net.essentuan.esl.model.annotations.Sorted
import net.essentuan.esl.reflections.extensions.instanceof
import net.essentuan.esl.rx.first
import org.bson.types.ObjectId
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Type

abstract class BsonModel : Json.Model {
    @Override("_id")
    @Sorted(Rating.CRITICAL)
    val id by lateinit(::ObjectId)
    val metadata by lateinit(::Metadata)

    abstract val table: Table<*>

    fun export(external: Boolean = true, vararg flags: Any): Json {
        return export(mutableSetOf(*flags).also {
            if (external)
                it.add(EXTERNAL)
        })
    }

    abstract suspend fun save()

    companion object {
        val EXTERNAL = Any()
    }
}

object BsonProvider : Provider<BsonModel, Any> {
    override val type: Class<BsonModel>
        get() = BsonModel::class.java
    private val known: MutableMap<Class<*>, Known<*>> = mutableMapOf()

    @Synchronized
    @Suppress("UNCHECKED_CAST")
    override fun invoke(
        cls: Class<in BsonModel>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): Encoder<BsonModel, Any> {
        return known.getOrPut(cls) { Known(cls as Class<BsonModel>) } as Encoder<BsonModel, Any>
    }

    @Suppress("UNCHECKED_CAST")
    private class Known<T : BsonModel>(type: Class<T>) :
        AbstractEncoder<Any, Any>(type as Class<Any>, Any::class.java) {
        val descriptor: Descriptor<T, Json> = Model.descriptor(type)

        override fun decode(
            obj: Any,
            flags: Set<Any>,
            type: Class<*>,
            element: AnnotatedElement,
            vararg typeArgs: Type
        ): T = when {
            obj is AnyJson -> descriptor.load(if (obj is Json) obj else Json(obj))
            obj instanceof this.type -> obj as T

            else -> throw IllegalArgumentException()
        }

        override fun encode(
            obj: Any,
            flags: Set<Any>,
            type: Class<*>,
            element: AnnotatedElement,
            vararg typeArgs: Type
        ): Any {
            return if (obj is Pointer)
                obj
            else if (BsonModel.EXTERNAL in flags)
                (obj as BsonModel).export(flags)
            else
                (obj as BsonModel).id via obj.table
        }

        override fun toString(
            obj: Any,
            flags: Set<Any>,
            type: Class<*>,
            element: AnnotatedElement,
            vararg typeArgs: Type
        ): String =
            (obj as BsonModel).run { "${id.toHexString()}|${table.name}" }

        override fun valueOf(
            string: String,
            flags: Set<Any>,
            type: Class<*>,
            element: AnnotatedElement,
            vararg typeArgs: Type
        ): Any {
            return string
                .split('|', limit = 1)
                .run {
                    blocking {
                        Database.findAll(Pointer(this@run[1], ObjectId(this@run[0]))).first()
                    }
                }.run {
                    this@Known.decode(
                        this,
                        flags,
                        type,
                        element,
                        *typeArgs
                    )
                }!!
        }
    }
}

fun BsonModel.touched() {
    metadata.prepare()
}