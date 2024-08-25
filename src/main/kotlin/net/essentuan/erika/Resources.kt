package net.essentuan.erika

import java.io.File
import java.nio.file.Path

object Resources : Path by File(ClassLoader.getResource("log4j2.xml")!!.toURI()).toPath().parent