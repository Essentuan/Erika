package net.essentuan.erika.features.content.type

import net.essentuan.esl.encoding.StringBasedEncoder
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Type

object ContentTypeEncoder : StringBasedEncoder<ContentType>() {
    override fun encode(
        obj: ContentType,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): String = obj.id

    override fun decode(
        obj: String,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): ContentType? = ContentType.valueOf(obj)
}