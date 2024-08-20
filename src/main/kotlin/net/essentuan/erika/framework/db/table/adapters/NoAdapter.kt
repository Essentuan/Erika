package net.essentuan.erika.framework.db.table.adapters

import net.essentuan.erika.framework.db.api.table.Table
import net.essentuan.esl.other.unsupported
import org.bson.Document

class NoAdapter<T> : Table.Adapter<T> {
    override val type: Class<T>
        get() = unsupported()

    override fun read(doc: Document): T = unsupported()

    override fun write(obj: T): Document = unsupported()
}