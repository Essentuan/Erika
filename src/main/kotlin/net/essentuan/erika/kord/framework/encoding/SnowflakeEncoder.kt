package net.essentuan.erika.kord.framework.encoding

import dev.kord.common.entity.Snowflake
import net.essentuan.esl.encoding.AbstractEncoder
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Type

object SnowflakeEncoder : AbstractEncoder<Snowflake, Long>() {
    override fun encode(
        obj: Snowflake,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): Long? =
        obj.value.toLong()

    override fun decode(
        obj: Long,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): Snowflake? =
        Snowflake(obj)

    override fun valueOf(
        string: String,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): Snowflake =
        Snowflake(string.toLong())

    override fun toString(
        obj: Snowflake,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): String =
        obj.value.toString()
}