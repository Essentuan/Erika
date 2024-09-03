package net.essentuan.erika

import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Path

object Resources : Path by try {
    File(ClassLoader.getResource("log4j2.xml")!!.toURI()).toPath().parent
} catch (ex: Exception) {
    val uri = ClassLoader.getResource("log4j2.xml")!!.toURI()
    val fs = FileSystems.newFileSystem(uri, mutableMapOf<String, String>())

    fs.provider().getPath(uri).parent
}