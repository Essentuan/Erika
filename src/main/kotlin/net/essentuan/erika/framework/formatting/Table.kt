package net.essentuan.erika.framework.formatting

import net.essentuan.erika.kord.framework.message.ContentBuilder
import net.essentuan.esl.iteration.extensions.iterate
import kotlin.math.max

const val JUSTIFY_LEFT = 0
const val JUSTIFY_RIGHT = 1

class Table(
    private val cols: MutableList<Column> = mutableListOf<Column>()
) {
    var header: String = ""

    operator fun Column.unaryPlus() {
        cols += this
    }

    inline fun header(block: StringBuilder.() -> Unit) {
        header = buildString(block)
    }

    inline fun column(title: String, justify: Int = JUSTIFY_LEFT, block: Column.() -> Unit) {
        +Column(title, justify).apply(block)
    }

    fun column(title: String, justify: Int = JUSTIFY_LEFT) {
        +Column(title, justify)
    }

    fun row(vararg entries: String) {
        for (i in entries.indices)
            cols.getOrNull(i)?.row(entries[i])
    }

    private fun StringBuilder.fill(c: Char, n: Int) {
        ensureCapacity(length + n)

        for (i in 0..<n)
            append(c)
    }

    private fun StringBuilder.newLine() =
        append('\n')

    override fun toString(): String {
        val rows = cols.maxOf { it.size }

        val out = StringBuilder()

        cols iterate {
            out.append(it.title)

            out.fill(' ', it.width - it.title.length)

            if (hasNext())
                out.append(" | ")
        }

        if (header.isNotEmpty()) {
            out.insert(0, buildString {
                fill(' ', max((out.length - header.length) / 2, 0))

                append(header)
                append('\n')
                append('\n')
            })
        }

        out.newLine()

        cols.forEachIndexed { i, it ->
            out.fill('-', it.width + if (i == 0) 1 else 2)

            if (i != cols.lastIndex)
                out.append('+')
        }

        out.newLine()

        for (i in 0..<rows) {
            cols iterate {
                val row = it.getOrNull(i) ?: ""

                when (it.justify) {
                    JUSTIFY_LEFT -> {
                        out.append(row)

                        out.fill(' ', it.width - row.length)
                    }

                    JUSTIFY_RIGHT -> {
                        out.fill(' ', it.width - row.length)

                        out.append(row)
                    }
                }

                if (hasNext())
                    out.append(" | ")
            }

            if (i != rows - 1)
                out.newLine()
        }

        val footer = cols.any { it.footer.isNotEmpty() }

        if (footer) {
            out.newLine()

            cols.forEachIndexed { i, it ->
                out.fill('-', it.width + if (i == 0) 1 else 2)

                if (i != cols.lastIndex)
                    out.append('+')
            }

            out.newLine()

            cols iterate {
                when (it.justify) {
                    JUSTIFY_LEFT -> {
                        out.append(it.footer)

                        out.fill(' ', it.width - it.footer.length)
                    }

                    JUSTIFY_RIGHT -> {
                        out.fill(' ', it.width - it.footer.length)

                        out.append(it.footer)
                    }
                }

                if (hasNext())
                    out.append(" | ")
            }
        }

        return out.toString()
    }

    class Column(
        val title: String,
        val justify: Int,
        private val rows: MutableList<String> = mutableListOf<String>()
    ) : List<String> by rows {
        var footer: String = ""
            private set

        var footerJustify: Int = JUSTIFY_RIGHT
            private set

        var width: Int = title.length
            private set

        fun row(string: String) {
            rows += string

            width = max(width, string.length)
        }

        inline fun row(block: ContentBuilder.() -> Unit) =
            row(ContentBuilder().apply(block).toString())

        fun footer(string: String, justify: Int = JUSTIFY_RIGHT) {
            footer = string
            footerJustify = justify

            width = max(width, footer.length)
        }

        fun footer(justify: Int = JUSTIFY_RIGHT, block: ContentBuilder.() -> Unit) =
            footer(ContentBuilder().apply(block).toString(), justify)
    }
}

inline fun table(block: Table.() -> Unit): String {
    return Table().apply(block).toString()
}