package net.essentuan.erika.encoding

import net.essentuan.erika.fetch.wynncraft.list.BasicTerritoryResource
import net.essentuan.esl.encoding.StringBasedEncoder
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Type

object BasicResourceEncoder : StringBasedEncoder<BasicTerritoryResource>() {
    override fun encode(
        obj: BasicTerritoryResource,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): String {
        return obj.toString()
    }

    override fun decode(
        obj: String,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): BasicTerritoryResource {
        return BasicTerritoryResource.valueOf(obj)
    }

}