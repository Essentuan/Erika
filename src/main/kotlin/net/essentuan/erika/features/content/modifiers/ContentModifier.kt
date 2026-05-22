package net.essentuan.erika.features.content.modifiers

import com.busted_moments.buster.api.Guild
import com.busted_moments.buster.api.GuildType
import net.essentuan.erika.observers.guilds.list.Guilds
import net.essentuan.esl.json.Json
import net.essentuan.esl.model.annotations.Ignored
import java.util.UUID

sealed interface ContentModifier : Iterable<ContentModifier.Element> {
    /**
     * Returns the element with the given [key] from this context or `null`.
     */
    operator fun <E : Element> get(key: Key<E>): E?

    /**
     * Returns true if an element with a given [key] is contained in this modifier.
     */
    operator fun contains(key: Key<*>): Boolean =
        get(key) != null

    /**
     * Returns true if [element] is contained in this modifier.
     */
    operator fun contains(element: Element): Boolean =
        get(element.key()) == element

    /**
     * Returns true if [flag] is contained in this modifier.
     */
    operator fun contains(flag: Flag): Boolean =
        get(flag) != null

    /**
     * Accumulates entries of this modifier starting with [initial] value and applying [operation]
     * from left to right to current accumulator value and each element of this modifier.
     */
    fun <R> fold(initial: R, operation: (R, Element) -> R): R

    /**
     * Returns a modifier containing elements from this modifier and elements from other [modifier].
     * The elements from this modifier with the same key as in the other one are dropped.
     */
    infix fun then(modifier: ContentModifier): ContentModifier =
        if (modifier === EmptyContentModifier) this else
            modifier.fold(this) { acc, element ->
                when (val removed = acc.minusKey(element.key())) {
                    EmptyContentModifier -> element

                    else -> CombinedModifier(removed, element)
                }
            }

    /**
     * Returns a modifier containing elements from this modifier, but without an element with
     * the specified [key].
     */
    fun minusKey(key: Key<*>): ContentModifier

    interface Key<E : Element> {
        fun asString(): String =
            toString()
    }

    interface Element : ContentModifier, Json.Model {
        fun key(): Key<*>

        @Suppress("UNCHECKED_CAST")
        override fun <E : Element> get(key: Key<E>): E? =
            if (key() == key) this as E else null

        override fun contains(key: Key<*>): Boolean =
            key() == key

        override fun contains(element: Element): Boolean =
            this == element

        override fun <R> fold(initial: R, operation: (R, Element) -> R): R =
            operation(initial, this)

        override fun minusKey(key: Key<*>): ContentModifier =
            if (key() == key) EmptyContentModifier else this

        override fun iterator(): Iterator<Element> =
            Itr(this)

        private class Itr(
            private var element: Element?
        ) : Iterator<Element> {
            override fun hasNext(): Boolean =
                element != null

            override fun next(): Element {
                val value = element ?: throw NoSuchElementException()
                element = null

                return value
            }
        }
    }

    interface Flag : Key<Flag>, Element {
        override fun key() = this
    }
}

data class GuildRaidModifier(
    override val uuid: UUID
) : ContentModifier.Element, GuildType by Guilds[uuid] ?: Guilds.UNKOWN {
    override fun key() = Key

    companion object Key : ContentModifier.Key<GuildRaidModifier>
}