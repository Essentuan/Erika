package net.essentuan.erika.framework.db.table.adapters

import net.essentuan.erika.framework.db.api.table.Table
import org.bson.Document

object DocumentAdapter : Table.Adapter<Document> {
    override val type: Class<Document>
        get() = Document::class.java

    override fun read(doc: Document): Document = doc

    override fun write(obj: Document): Document = obj
}