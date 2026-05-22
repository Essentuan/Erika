package net.essentuan.erika.features.content.modifiers.encoding

import net.essentuan.erika.features.content.modifiers.ContentModifier
import net.essentuan.esl.encoding.JsonBasedEncoder
import net.essentuan.esl.json.type.AnyJson
import net.essentuan.esl.model.Model
import net.essentuan.esl.reflections.Reflections
import net.essentuan.esl.reflections.extensions.simpleString
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Type

object ContentModifierElementEncoder : JsonBasedEncoder<ContentModifier.Element>() {
    private val MODIFIERS by lazy {
        Reflections.types
            .subtypesOf(ContentModifier.Element::class)
            .associate { it.simpleString() to lazy {Model.descriptor(it) } }
    }

    override fun encode(
        obj: ContentModifier.Element,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): AnyJson? {
        val name = obj::class.simpleString()
        val encoder = MODIFIERS[name]?.value ?: return null
        val result = encoder.encode(obj, flags, type, element, *typeArgs)

        result["_type"] = name

        return result
    }

    override fun decode(
        obj: AnyJson,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): ContentModifier.Element? {
        val name = obj.getString("_type") ?: return null
        val encoder = MODIFIERS["_type"]?.value ?: return null

        return encoder.decode(obj, flags, type, element, *typeArgs)
    }
}