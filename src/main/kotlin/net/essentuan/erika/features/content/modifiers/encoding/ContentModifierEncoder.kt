package net.essentuan.erika.features.content.modifiers.encoding

import net.essentuan.erika.features.content.modifiers.CombinedModifier
import net.essentuan.erika.features.content.modifiers.ContentModifier
import net.essentuan.erika.features.content.modifiers.EmptyContentModifier
import net.essentuan.esl.encoding.AbstractEncoder
import net.essentuan.esl.json.type.AnyJson
import net.essentuan.esl.other.unsupported
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Type

object ContentModifierEncoder : AbstractEncoder<ContentModifier, List<AnyJson>>() {
    override fun encode(
        obj: ContentModifier,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): List<AnyJson> = when (obj) {
        EmptyContentModifier -> emptyList()
        is ContentModifier.Element -> listOf(
            ContentModifierElementEncoder.encode(
                obj,
                flags,
                obj::class.java,
                element
            )!!
        )

        is CombinedModifier -> buildList {
            for (e in obj)
                ContentModifierElementEncoder.encode(
                    e,
                    flags,
                    e::class.java,
                    element
                )!!
        }
    }

    override fun decode(
        obj: List<AnyJson>,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): ContentModifier? = when(obj.size) {
        0 -> EmptyContentModifier
        1 -> ContentModifierElementEncoder.decode(
            obj[0],
            flags,
            type,
            element,
        )

        else -> obj.fold(EmptyContentModifier) { acc: ContentModifier, it ->
            acc.then(
                ContentModifierElementEncoder.decode(
                    it,
                    flags,
                    type,
                    element,
                )!!
            )
        }
    }

    override fun toString(
        obj: ContentModifier,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): String = unsupported("toString")

    override fun valueOf(
        string: String,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): ContentModifier = unsupported("valueOf")
}