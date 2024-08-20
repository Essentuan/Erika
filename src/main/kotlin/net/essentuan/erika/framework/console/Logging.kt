package net.essentuan.erika.framework.console

import net.essentuan.esl.reflections.extensions.simpleString
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.util.StackLocatorUtil
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

object Logging : Logger by LogManager.getLogger("System") {
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): Lazy<Logger> {
        return lazy { get(
            Class.forName(
                StackLocatorUtil.getStackTraceElement(4).className
            )
        ) }
    }

    private fun get(cls: Class<*>): Logger {
        return LogManager.getLogger(cls.simpleString().replaceFirst("Kt", ""))
    }

    operator fun invoke(cls: KClass<*>): ReadOnlyProperty<Any?, Logger> {
        return get(cls.java).run { ReadOnlyProperty { _, _ -> this@run } }
    }
}