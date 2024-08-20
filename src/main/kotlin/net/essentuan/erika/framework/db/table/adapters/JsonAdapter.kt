package net.essentuan.erika.framework.db.table.adapters

import net.essentuan.erika.framework.db.api.table.Table
import net.essentuan.erika.framework.db.bson
import net.essentuan.esl.json.Json
import org.bson.Document

object JsonAdapter : Table.Adapter<Json> {
    override val type: Class<Json>
        get() = Json::class.java

    override fun read(doc: Document): Json = Json(doc)

    override fun write(obj: Json): Document = obj.bson()
}