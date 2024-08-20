package net.essentuan.erika.framework.db.api.table.schema

import com.mongodb.client.model.Indexes
import net.essentuan.esl.time.duration.Duration
import org.bson.conversions.Bson

interface Index {
    val key: String
    val types: Set<Type>
    val expiry: Duration?

    enum class Type(private val function: (String) -> Bson) {
        ASCENDING(Indexes::ascending),
        DESCENDING(Indexes::descending),
        GEO(Indexes::geo2d),
        GEO_SPHERE(Indexes::geo2dsphere),
        HASHED(Indexes::hashed),
        TEXT(Indexes::text);

        operator fun invoke(str: String): Bson = function(str)
    }

    interface Builder {
        val expire: Expire

        operator fun Type.unaryPlus()

        fun interface Expire {
            infix fun after(duration: Duration)
        }
    }
}