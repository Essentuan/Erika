package net.essentuan.erika.framework.db.commands.aggregation.stages

import com.mongodb.client.model.Aggregates
import net.essentuan.erika.framework.db.builders.bson
import net.essentuan.erika.framework.db.commands.aggregation.Aggregation
import net.essentuan.esl.reflections.extensions.simpleString
import org.bson.BsonDocument

@JvmInline
value class Documents(
    val list: MutableList<BsonDocument> = mutableListOf()
) {
    operator fun Any.unaryPlus() {
        val result = bson()
        require(result is BsonDocument) { "${this.javaClass.simpleString()} is not a BsonDocument!" }

        list.add(result)
    }
}

/**
 * @see com.mongodb.client.model.Aggregates.documents
 */
inline fun Aggregation.documents(block: Documents.() -> Unit) {
    this+= Aggregates.documents(Documents().apply(block).list)
}