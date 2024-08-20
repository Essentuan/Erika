package net.essentuan.erika.ktor

import io.ktor.serialization.WebsocketContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.charsets.Charset
import io.ktor.websocket.Frame
import io.ktor.websocket.FrameType
import net.essentuan.erika.framework.db.`object`.BsonModel
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.Model.Companion.export
import net.essentuan.esl.other.unsupported
import org.bson.json.JsonReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

private typealias IJson = Json
private typealias JsonModel = Json.Model

object Content {
    object Json : WebsocketContentConverter {
        override suspend fun serializeNullable(charset: Charset, typeInfo: TypeInfo, value: Any?): Frame {
            return Frame.Text(
                true,
                when(value) {
                        null -> "{}"
                    is Exportable<*> -> (value.external() as IJson).asString()
                    is IJson -> value.asString()
                    is JsonModel -> value.export(BsonModel.EXTERNAL).asString()
                    else -> unsupported()
                }.toByteArray(charset)
            )
        }

        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: Frame): IJson =
            IJson(JsonReader(InputStreamReader(ByteArrayInputStream(content.data), charset)))

        override fun isApplicable(frame: Frame): Boolean =
            frame.frameType == FrameType.TEXT
    }
}

fun interface Exportable<T> {
    fun external(): T
}