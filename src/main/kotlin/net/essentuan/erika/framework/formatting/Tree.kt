package net.essentuan.erika.framework.formatting

import kotlinx.coroutines.yield
import net.essentuan.erika.framework.console.AnsiColor
import net.essentuan.esl.Result
import net.essentuan.esl.iteration.extensions.iterate
import java.util.LinkedList
import java.util.stream.Collectors
import kotlin.experimental.ExperimentalTypeInference

private val ROW_INDENT: String = AnsiColor.WHITE + "   |"
private val BRANCH_INDENT: String = AnsiColor.WHITE + "   +- "
private val BLANK_INDENT = ROW_INDENT.replace('|', ' ')

abstract class Tree<T>(private val comparator: Comparator<T>?) {
    private var rows: MutableList<StringBuilder> = ArrayList()

    protected abstract fun write(element: T, sb: StringBuilder)

    protected abstract fun branch(element: T): Iterable<T>

    operator fun plusAssign(node: T) {
        append(node, if (rows.isEmpty()) 0 else rows.size + 1)
    }

    operator fun plusAssign(iterator: Iterator<T>) {
        iterator.forEach(this::plusAssign)
    }

    operator fun plusAssign(iterable: Iterable<T>) {
        plusAssign(iterable.iterator())
    }

    private fun append(node: T, start: Int) {
        var row = start
        write(node, getOrCreate(row))

        branch(node)
            .iterator()
            .takeIf { it.hasNext() }
            ?.asSequence()
            ?.run { if (comparator != null) sortedWith(comparator) else this }
            ?.iterate {
                row += 2

                getOrCreate(row - 1).append(ROW_INDENT)
                getOrCreate(row).append(BRANCH_INDENT)

                val size = countChildren(it) * 2

                for (i in 1..size) {
                    getOrCreate(row + i).append(if (hasNext()) ROW_INDENT else BLANK_INDENT)
                }

                append(it, row)

                row += size
            }
    }

    private fun getOrCreate(row: Int): StringBuilder {
        while (rows.size <= row) {
            rows.add(StringBuilder().append("     "))
        }

        return rows[row]
    }

    override fun toString(): String {
        return rows.stream()
            .map { obj: StringBuilder -> obj.toString() }
            .collect(Collectors.joining("\n"))
    }

    private fun countChildren(element: T): Int {
        var count = 0

        for (children in branch(element))
            count += countChildren(children) + 1

        return count
    }
}


@OverloadResolutionByLambdaReturnType
@OptIn(ExperimentalTypeInference::class)
inline fun <reified T> tree(
    crossinline branch: T.() -> Any,
    vararg elements: Any,
    comparator: Comparator<T>? = null,
    crossinline writer: T.(StringBuilder) -> Unit
): String {
    return object : Tree<T>(comparator) {
        init {
            this += flatten(elements).iterator()
        }

        private fun flatten(vararg array: Any?): Sequence<T> = sequence {
            val queue = LinkedList<Any>(array.asList())

            while (queue.isNotEmpty()) {
                when (val obj = queue.poll()) {
                    is List<*> -> {
                        for (e in obj.asReversed())
                            queue.offerFirst(e)
                    }

                    is Array<*> ->
                        queue.offerFirst(obj.asList())
                    
                    is Iterable<*> ->
                        queue.offerFirst(obj.toList())

                    is Iterator<*> -> {
                        val list = mutableListOf<Any?>()

                        for (e in obj)
                            list += e

                        queue.offerFirst(list)
                    }

                    is Result.Value<*> -> {
                        if (obj.value is T)
                            yield(obj.value as T)
                        else
                            queue.offerFirst(obj.value)
                    }

                    else -> {
                        if (obj is T)
                            yield(obj)
                    }
                }
            }
        }

        override fun branch(element: T): Iterable<T> =
            flatten(branch(element)).asIterable()

        override fun write(element: T, sb: StringBuilder) =
            writer(element, sb)
    }.toString()
}