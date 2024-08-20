package net.essentuan.erika.framework.db.table.adapters

import net.essentuan.erika.framework.db.api.table.Table
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.type.AnyJson
import net.essentuan.esl.model.Model
import net.essentuan.esl.model.Model.Companion.export
import net.essentuan.esl.reflections.extensions.extends
import org.bson.Document

class ModelAdapter<T: Model<DATA>, DATA: AnyJson>(override val type: Class<T>, cls: Class<DATA>) : Table.Adapter<T> {
    val descriptor = Model.descriptor(type)

    @Suppress("UNCHECKED_CAST")
    val adapter: Table.Adapter<DATA> = if (cls extends Json::class.java) JsonAdapter as Table.Adapter<DATA> else JsonTypeAdapter(cls)

    override fun read(doc: Document): T = descriptor(adapter.read(doc), emptySet())

    override fun write(obj: T): Document = adapter.write(obj.export())
}