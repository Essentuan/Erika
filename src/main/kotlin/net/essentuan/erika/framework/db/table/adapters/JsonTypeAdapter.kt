package net.essentuan.erika.framework.db.table.adapters

import net.essentuan.erika.framework.db.api.table.Table
import net.essentuan.erika.framework.db.bson
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.type.AnyJson
import org.bson.Document

class JsonTypeAdapter<T: AnyJson>(override val type: Class<T>) : Table.Adapter<T> {
    override fun read(doc: Document): T = AnyJson.valueOf(Json(doc), type)

    override fun write(obj: T): Document = Json(obj).bson()
}