package net.essentuan.erika.framework.db.table.schema

import net.essentuan.erika.framework.db.api.table.schema.Index
import net.essentuan.esl.time.duration.Duration
import java.util.EnumSet

class IndexImpl(
    override val key: String,
    override val types: Set<Index.Type>,
    override val expiry: Duration?
) : Index {
    class Builder(val key: String) : Index.Builder {
        val types: MutableSet<Index.Type> = EnumSet.noneOf(Index.Type::class.java)
        var expiry: Duration? = null

        override val expire: Index.Builder.Expire
            get() = Index.Builder.Expire { expiry = it }

        override fun Index.Type.unaryPlus() {
            types.add(this)
        }

        fun build(): IndexImpl = IndexImpl(key, types, expiry)
    }
}