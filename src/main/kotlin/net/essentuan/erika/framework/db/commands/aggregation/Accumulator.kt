package net.essentuan.erika.framework.db.commands.aggregation

import net.essentuan.erika.framework.db.builders.expression.AbstractExpr

object Accumulator : AbstractExpr() {
    val count = Single("\$count")
}