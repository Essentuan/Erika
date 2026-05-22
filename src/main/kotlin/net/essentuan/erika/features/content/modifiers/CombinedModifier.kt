package net.essentuan.erika.features.content.modifiers

import net.essentuan.esl.iteration.extensions.iterate

internal class CombinedModifier(
    val left: ContentModifier,
    private val element: ContentModifier.Element
) : ContentModifier {
    override fun <E : ContentModifier.Element> get(key: ContentModifier.Key<E>): E? {
        var cur = this
        while (true) {
            cur.element[key]?.let { return it }
            val next = cur.left
            if (next is CombinedModifier) {
                cur = next
            } else {
                return next[key]
            }
        }
    }

    override fun <R> fold(initial: R, operation: (R, ContentModifier.Element) -> R): R =
        operation(left.fold(initial, operation), element)

    override fun minusKey(key: ContentModifier.Key<*>): ContentModifier {
        element[key]?.let { return left }
        val newLeft = left.minusKey(key)
        return when {
            newLeft === left -> this
            newLeft === EmptyContentModifier -> element
            else -> CombinedModifier(newLeft, element)
        }
    }

    private fun containsAll(context: CombinedModifier): Boolean {
        var cur = context
        while (true) {
            if (!contains(cur.element)) return false
            val next = cur.left
            if (next is CombinedModifier) {
                cur = next
            } else {
                return contains(next as ContentModifier.Element)
            }
        }
    }

    override fun iterator(): Iterator<ContentModifier.Element> =
        Itr(this)

    override fun hashCode(): Int =
        left.hashCode() + element.hashCode()

    override fun equals(other: Any?): Boolean =
        this === other || other is CombinedModifier && other.size == size && other.containsAll(this)
    override fun toString(): String =
        buildString {
            append("ContentModifier(")

            iterate {
                append(it.key(). asString())
                append('=')
                append(it)

                if (hasNext())
                    append(", ")
            }

            append(')')
        }

    private class Itr(
        var value: ContentModifier?
    ) : Iterator<ContentModifier.Element> {
        override fun hasNext(): Boolean =
            value != EmptyContentModifier

        override fun next(): ContentModifier.Element =
            when (val next = value) {
                is CombinedModifier ->
                    next.element.also {
                        value = next.left
                    }

                is ContentModifier.Element ->
                    next.also { value = EmptyContentModifier }

                else ->
                    throw NoSuchElementException()
            }

    }
}
