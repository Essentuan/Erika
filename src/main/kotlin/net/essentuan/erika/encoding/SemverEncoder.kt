package net.essentuan.erika.encoding

import net.essentuan.esl.encoding.StringBasedEncoder
import org.semver4j.Semver
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Type

object SemverEncoder : StringBasedEncoder<Semver>() {
    override fun encode(
        obj: Semver,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): String? =
        obj.version

    override fun decode(
        obj: String,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): Semver? = Semver.parse(obj.removePrefix("v"))
}