package net.essentuan.erika.framework.db.commands.aggregation.stages

import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Variable
import net.essentuan.erika.framework.db.api.table.Table
import net.essentuan.erika.framework.db.builders.Let
import net.essentuan.erika.framework.db.commands.aggregation.Aggregation
import net.essentuan.erika.framework.db.eslKey
import net.essentuan.erika.framework.db.`object`.types.Struct.Companion.table
import net.essentuan.esl.reflections.extensions.declaringClass
import org.bson.conversions.Bson
import kotlin.reflect.KProperty

data class Out(
    val table: Table<*>,
    val field: String,
    val name: String
) {
    operator fun contains(source: Source): Boolean {
        source.aggregation+= Aggregates.lookup(
            table.name,
            source.key,
            field,
            name
        )

        return false
    }
}

data class Source(
    val aggregation: Aggregation,
    val key: String
)

class PipelineBuilder(
    val let: MutableList<Variable<Any?>>,
    val stages: MutableList<Bson>
) : Aggregation {
    lateinit var table: Table<*>

    val select: Select
        get() = Select(this)

    inline fun let(block: Let.() -> Unit) {
        block(Let { key, value -> let+= Variable(key, value) })
    }

    override fun plusAssign(bson: Bson) {
        stages+= bson
    }

    override fun iterator(): Iterator<Bson> =
        stages.iterator()

    @JvmInline
    value class Select(
        val builder: PipelineBuilder
    ) {
        infix fun from(table: Table<*>) {
            builder.table = table
        }
    }
}

fun Aggregation.lookup(string: String): Source =
    Source(this, string)

fun Aggregation.lookup(property: KProperty<*>): Source =
    Source(this, property.eslKey)

/**
 * @see Aggregates.lookup
 */
inline fun Aggregation.lookup(string: String, block: PipelineBuilder.() -> Unit) {
    val pipeline = PipelineBuilder(
        mutableListOf(),
        mutableListOf()
    ).apply(block)

    this+ Aggregates.lookup(
            pipeline.table.name,
            pipeline.let,
            pipeline.stages,
            string
        )
}

inline fun Aggregation.lookup(property: KProperty<*>, crossinline block: PipelineBuilder.() -> Unit) =
    lookup(property.eslKey, block)

infix fun KProperty<*>.out(string: String): Out = Out(
    declaringClass.table,
    eslKey,
    string
)
