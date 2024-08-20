package net.essentuan.erika.framework.console

import com.essentuan.acf.core.CommandLoader
import com.essentuan.acf.core.CommandLoader.AbstractBuilder
import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.command.CommandNode
import com.essentuan.acf.core.command.arguments.Argument
import com.essentuan.acf.core.context.BuildContext
import com.essentuan.acf.core.context.CommandSource
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.arguments.StringArgumentType.StringType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.tree.LiteralCommandNode
import net.essentuan.esl.other.thread
import net.essentuan.esl.other.unsupported
import net.essentuan.esl.reflections.Functions.Companion.static
import net.essentuan.esl.reflections.Reflections
import net.essentuan.esl.reflections.extensions.annotatedWith
import net.essentuan.esl.reflections.extensions.isObject
import java.util.Scanner
import kotlin.concurrent.thread
import kotlin.reflect.jvm.javaMethod

private val LOGGER by Logging(Commands::class)
private const val UNKNOWN_COMMAND = "Unknown command at position 0:"

typealias Str = StringType

object Commands : CommandLoader<SystemSource, SystemContext, Commands>(Builder()) {
    private val dispatcher = CommandDispatcher<SystemSource>()

    private val thread: Thread = thread(
        name = "std-in",
        isDaemon = true,
        start = false
    ) {
        val thread = thread()
        val reader = Scanner(System.`in`)

        while (!thread.isInterrupted) {
            if (!reader.hasNextLine()) continue

            val parse = dispatcher.parse(reader.nextLine() ?: continue, SystemSource)

            try {
                dispatcher.execute(parse)
            } catch (ex: CommandSyntaxException) {
                Logging.error(ex.message)
            } catch (ex: Throwable) {
                Logging.error("Error executing '${parse.command}", ex)
            }
        }
    }

    fun start() {
        this.register(dispatcher::register)
        thread.start()
    }

    override fun getBuildContext(): SystemContext = SystemContext

    override fun info(message: String?, vararg args: Any?) =
        LOGGER.info(message, *args)

    override fun debug(message: String?, vararg args: Any?) =
        LOGGER.debug(message, *args)

    override fun error(message: String?, vararg args: Any?) =
        LOGGER.error(message, *args)

    override fun canExecute(
        source: CommandContext<SystemSource>?,
        command: CommandNode<SystemSource, SystemContext, Commands>?
    ): Boolean = true
}

val ParseResults<*>.command: String
    get() = (context
        .nodes
        .firstOrNull()
        ?.node as? LiteralCommandNode<*>)
        ?.literal ?: reader.string

private class Builder : AbstractBuilder<SystemSource, SystemContext, Commands, Builder>() {
    init {
        inPackage {
            Reflections.functions
                .static()
                .map { it.javaMethod?.declaringClass }
                .filter { it != null && it annotatedWith Command::class }
                .distinct()
                .toList()
        }

        withArguments {
            Reflections.types
                .subtypesOf(Argument::class)
                .filterNot { it.isObject }
                .map { it.java }
                .toList()
        }
    }

    override fun build(): Commands = unsupported()
}

object SystemContext : BuildContext<Commands> {
    override fun getLoader(): Commands = Commands
}

object SystemSource : CommandSource<SystemContext> {
    override fun message(message: String) {
        Logging.info(message)
    }

    override fun error(message: String) {
        Logging.error(message)
    }

    override fun getBuildContext(): SystemContext = SystemContext
}