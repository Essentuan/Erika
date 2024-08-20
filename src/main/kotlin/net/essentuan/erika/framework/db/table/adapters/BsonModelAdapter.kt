package net.essentuan.erika.framework.db.table.adapters

import net.essentuan.erika.framework.db.api.table.Table
import net.essentuan.erika.framework.db.bson
import net.essentuan.erika.framework.db.`object`.BsonModel
import net.essentuan.erika.framework.db.`object`.Memory.load
import net.essentuan.esl.model.Model
import org.bson.Document

class BsonModelAdapter<T: BsonModel>(override val type: Class<T>) : Table.Adapter<T> {
    val descriptor = Model.descriptor(type)

    override fun read(doc: Document): T = descriptor.load(doc)

    override fun write(obj: T): Document = obj.export(external = false).bson()
}