package net.essentuan.erika.framework.db.builders

import org.bson.Document

@JvmInline
value class Let(val accept: (String, Any?) -> Unit) {
    fun String.to(value: Any?) =
        accept(this, value)

    inline operator fun String.invoke(block: Let.() -> Unit) {
        val document = Document()
        block(Let(document::set))

        accept(this, document)
    }
}