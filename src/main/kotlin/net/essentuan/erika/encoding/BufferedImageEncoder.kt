package net.essentuan.erika.encoding

import net.essentuan.esl.encoding.AbstractEncoder
import net.essentuan.esl.other.unsupported
import org.bson.types.Binary
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Type
import javax.imageio.ImageIO

object BufferedImageEncoder : AbstractEncoder<BufferedImage, Binary>() {
    override fun decode(
        obj: Binary,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): BufferedImage {
        return ImageIO.read(ByteArrayInputStream(obj.data))
    }

    override fun encode(
        obj: BufferedImage,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): Binary =
        Binary(ByteArrayOutputStream().also {
            ImageIO.write(obj, "png", it)
        }.toByteArray())

    override fun toString(
        obj: BufferedImage,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): String = unsupported()

    override fun valueOf(
        string: String,
        flags: Set<Any>,
        type: Class<*>,
        element: AnnotatedElement,
        vararg typeArgs: Type
    ): BufferedImage = unsupported()
}