package net.essentuan.erika.framework.db.commands.aggregation.stages

import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Facet
import net.essentuan.erika.framework.db.commands.aggregation.Aggregation
import net.essentuan.erika.framework.db.eslKey
import org.bson.conversions.Bson
import kotlin.reflect.KProperty

@JvmInline
value class Facets(
    val accept: (String, List<Bson>) -> Unit
) {
    inline infix fun String.to(block: Sink.() -> Unit) {
        accept(this, Sink().apply(block))
    }

    inline infix fun KProperty<*>.to(block: Sink.() -> Unit) {
        accept(this.eslKey, Sink().apply(block))
    }

    inline operator fun String.invoke(block: Facets.() -> Unit) {
        Facets { key, facet -> accept("$this.$key", facet) }.apply(block)
    }

    inline operator fun KProperty<*>.invoke(block: Facets.() -> Unit) {
        Facets { key, facet -> accept("${this.eslKey}.$key", facet) }.apply(block)
    }


    class Sink : ArrayList<Bson>(), Aggregation {
        override fun plusAssign(bson: Bson) {
            add(bson)
        }
    }
}

/**
 * @see Aggregates.facet
 */
inline fun Aggregation.facet(block: Facets.() -> Unit) {
    val facets = mutableListOf<Facet>()
    Facets { key, stages -> facets.add(Facet(key, stages)) }.apply(block)

    this+= Aggregates.facet(facets)
}