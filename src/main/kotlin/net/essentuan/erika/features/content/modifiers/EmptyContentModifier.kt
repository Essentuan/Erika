package net.essentuan.erika.features.content.modifiers

object EmptyContentModifier : ContentModifier {
    override fun <E : ContentModifier.Element> get(key: ContentModifier.Key<E>): E? =
        null

    override fun <R> fold(initial: R, operation: (R, ContentModifier.Element) -> R): R =
        initial

    override fun minusKey(key: ContentModifier.Key<*>): ContentModifier =
        this

    override fun iterator(): Iterator<ContentModifier.Element> =
        EmptyItr

    private object EmptyItr : Iterator<ContentModifier.Element> {
        override fun hasNext(): Boolean =
            false
        override fun next(): ContentModifier.Element =
            throw NoSuchElementException()
    }
}