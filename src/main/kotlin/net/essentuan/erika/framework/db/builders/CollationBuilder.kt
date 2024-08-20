package net.essentuan.erika.framework.db.builders

import com.mongodb.client.model.Collation
import com.mongodb.client.model.CollationAlternate
import com.mongodb.client.model.CollationCaseFirst
import com.mongodb.client.model.CollationStrength

@JvmInline
value class CollationBuilder(
    private val builder: Collation.Builder = Collation.builder()
        .locale("en_US")
        .caseLevel(false)
        .numericOrdering(false)
) {
    fun locale(supplier: () -> String) {
        builder.locale(supplier())
    }

    fun caseSensitive() {
        builder.caseLevel(true)
    }

    fun priority(supplier: () -> CollationCaseFirst) {
        builder.collationCaseFirst(supplier())
    }

    fun strength(supplier: () -> CollationStrength) {
        builder.collationStrength(supplier())
    }

    fun numericOrdering() {
        builder.numericOrdering(true)
    }

    fun style(supplier: () -> CollationAlternate) {
        builder.collationAlternate(supplier())
    }

    fun normalized() {
        builder.normalization(true)
    }

    fun backwards() {
        builder.backwards(true)
    }

    fun build(): Collation = builder.build()
}

val DEFAULT_COLLATION: Collation by lazy {
    Collation.builder().build()
}