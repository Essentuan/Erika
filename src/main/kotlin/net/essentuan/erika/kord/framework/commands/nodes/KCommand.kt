package net.essentuan.erika.kord.framework.commands.nodes

import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Default
import com.essentuan.acf.core.annotations.Subcommand
import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Guild
import dev.kord.core.entity.interaction.GroupCommand
import dev.kord.core.entity.interaction.RootCommand
import dev.kord.core.entity.interaction.SubCommand
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.rest.builder.interaction.GroupCommandBuilder
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.group
import dev.kord.rest.builder.interaction.subCommand
import net.essentuan.erika.framework.annotation.Description
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.kord.framework.commands.CommandInteraction
import net.essentuan.erika.kord.framework.commands.CommandType
import net.essentuan.erika.kord.framework.commands.KordArgument
import net.essentuan.erika.kord.framework.commands.annotations.Blacklist
import net.essentuan.erika.kord.framework.commands.annotations.Requires
import net.essentuan.erika.kord.framework.commands.annotations.Whitelist
import net.essentuan.erika.kord.framework.message.embed
import net.essentuan.erika.kord.framework.permissions.Permission
import net.essentuan.erika.kord.framework.permissions.Permissions.missing
import net.essentuan.esl.color.McColor
import net.essentuan.esl.filterNotNull
import net.essentuan.esl.ifPresent
import net.essentuan.esl.iteration.extensions.iterate
import net.essentuan.esl.other.unsupported
import net.essentuan.esl.reflections.extensions.annotatedWith
import net.essentuan.esl.reflections.extensions.classOf
import net.essentuan.esl.reflections.extensions.extends
import net.essentuan.esl.reflections.extensions.get
import net.essentuan.esl.string.reader.consume
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.extensionReceiverParameter
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType

private val LOGGER by Logging

class KCommand(
    private val obj: Any,
    private val children: MutableMap<String, KNode<Any>> = mutableMapOf()
) : KNode<ChatInputCreateBuilder>, Map<String, KNode<Any>> by children {
    override val id: String
    override val name: String
    private val rootPermission: Permission?

    override val description: String =
        obj::class[Description::class]?.value ?: "No Description"

    private lateinit var executor: KFunction<*>
    private var permission: Permission? = null

    private val args = mutableListOf<KordArgument<Any>>()

    override val whitelist: Set<Snowflake> =
        obj::class[Whitelist::class]?.value
            ?.asSequence()
            ?.map { Snowflake(it) }
            ?.toSet() ?: emptySet()

    override val blacklist: Set<Snowflake> =
        obj::class[Blacklist::class]?.value
            ?.asSequence()
            ?.map { Snowflake(it) }
            ?.toSet() ?: emptySet()

    val type: CommandType

    init {
        obj::class[Command::class]!!.value.let {
            id = it.lowercase()
            name = it
        }

        obj::class[Requires::class]?.value.let {
            rootPermission = if (it == null)
                null
            else
                Permission(it)
        }

        obj::class.declaredFunctions
            .asSequence()
            .filter { it annotatedWith Default::class || it annotatedWith Subcommand::class }
            .filter {
                it.extensionReceiverParameter?.type?.javaType?.classOf()?.extends(CommandInteraction::class) == true
            }
            .iterate { func ->
                when {
                    func annotatedWith Default::class && !::executor.isInitialized -> {
                        executor = func

                        for (param in func.parameters) {
                            if (param.kind == KParameter.Kind.VALUE)
                                args += KordArgument(param)
                        }

                        if (func annotatedWith Requires::class)
                            permission = Permission(func[Requires::class]!!.value)
                    }

                    func annotatedWith Subcommand::class -> {
                        val reader = func[Subcommand::class]!!.value.consume()

                        val first = reader.readString()
                        reader.skipWhitespace()

                        if (!reader.canRead()) {
                            val node = Node(first, func)
                            children[node.id] = node
                        } else {
                            val command = reader.readString()
                            val group: KNode<*> = children.computeIfAbsent(first) {
                                @Suppress("UNCHECKED_CAST")
                                Group(first) as KNode<Any>
                            }
                            require(group is Group)

                            val node = Node(command, func)
                            group[node.id] = node
                        }
                    }
                }
            }

        type =
            if (
                whitelist.isNotEmpty() ||
                blacklist.isNotEmpty() ||
                children.any { (_, it) -> it.whitelist.isNotEmpty() || it.blacklist.isNotEmpty() }
            )
                CommandType.GUILD
            else
                CommandType.GLOBAL

        if (::executor.isInitialized)
            executor.isAccessible = true
    }

    override fun ChatInputCreateBuilder.build(guild: Guild?) {
        if ((whitelist.isNotEmpty() && guild?.id !in whitelist) || (blacklist.isNotEmpty() && guild?.id in blacklist))
            return

        for (arg in args)
            arg.apply { create() }

        @Suppress("UNCHECKED_CAST")
        for ((_, child) in children) {
            if (
                (child.whitelist.isEmpty() || guild?.id in child.whitelist) &&
                (child.blacklist.isEmpty() || guild?.id !in child.blacklist)
            ) {
                (child as KNode<ChatInputCreateBuilder>).apply { build(guild) }
            }
        }

        Locale.ALL.forEach { name(it, name) }
    }

    suspend fun CommandInteraction.execute(guild: Guild) {
        try {
            if (rootPermission != null && source missing rootPermission) {
                ephemeral {
                    embed(McColor.DARK_RED) {
                        title = "You do not have the proper permission to use this command!"
                    }
                }

                return
            }

            when (val cmd = command) {
                is RootCommand -> {
                    if (!::executor.isInitialized || (permission != null && source missing permission!!)) {
                        ephemeral {
                            embed(McColor.DARK_RED) {
                                title = "You do not have the proper permission to use this command!"
                            }
                        }

                        return
                    }

                    val out = mutableMapOf<KParameter, Any?>()

                    for (arg in args) {
                        val result = arg.run { value() }

                        result.filterNotNull()
                            .ifPresent {
                                out[arg.param] = it
                            }
                    }

                    for (param in executor.parameters) {
                        when (param.kind) {
                            KParameter.Kind.INSTANCE -> {
                                out[param] = obj
                            }

                            KParameter.Kind.EXTENSION_RECEIVER -> {
                                out[param] = this
                            }

                            KParameter.Kind.VALUE -> Unit
                        }
                    }

                    executor.callSuspendBy(out)
                }

                is GroupCommand -> {
                    run {
                        val group: KNode<*>? = this@KCommand[cmd.groupName]

                        if (group !is Group)
                            return@run

                        val subcommand = group[cmd.name]

                        if (subcommand !is Node)
                            return@run

                        subcommand.apply { execute(guild) }

                        return
                    }

                    ephemeral {
                        embed(McColor.DARK_RED) {
                            title = "You do not have the proper permission to use this command!"
                        }
                    }
                }

                is SubCommand -> {
                    run {
                        val subcommand = this@KCommand[cmd.name]

                        if (subcommand !is Node)
                            return@run

                        subcommand.apply { execute(guild) }

                        return
                    }

                    ephemeral {
                        embed(McColor.DARK_RED) {
                            title = "You do not have the proper permission to use this command!"
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ephemeral {
                embed(McColor.DARK_RED) {
                    title = "Something went wrong trying to execute this command!"
                }
            }

            LOGGER.error("Failed to execute command! Caused by:", ex)
        }
    }

    private inner class Group(
        override val name: String
    ) : KNode<ChatInputCreateBuilder>, MutableMap<String, KNode<Any>> by mutableMapOf() {
        override val id: String = name.lowercase()

        override val description: String
            get() = ""
        override val whitelist: Set<Snowflake>
            get() = emptySet()
        override val blacklist: Set<Snowflake>
            get() = emptySet()

        override fun ChatInputCreateBuilder.build(guild: Guild?) {
            group(id, description) {
                @Suppress("UNCHECKED_CAST")
                for ((_, child) in this@Group)
                    (child as KNode<GroupCommandBuilder>).apply { build(guild) }

                Locale.ALL.forEach { name(it, name) }
            }
        }
    }

    private inner class Node(
        override val name: String,
        private val executor: KFunction<*>,
    ) : KNode<Any> {
        override val id: String = name.lowercase()

        override val description: String =
            executor[Description::class]?.value ?: "No Description"

        override val whitelist: Set<Snowflake> =
            executor[Whitelist::class]?.value
                ?.asSequence()
                ?.map { Snowflake(it) }
                ?.toSet() ?: emptySet()

        override val blacklist: Set<Snowflake> =
            executor[Blacklist::class]?.value
                ?.asSequence()
                ?.map { Snowflake(it) }
                ?.toSet() ?: emptySet()

        private val permission: Permission? =
            executor[Requires::class]?.value.let {
                if (it == null)
                    null
                else
                    Permission(it)
            }

        private val args = mutableListOf<KordArgument<Any>>()

        init {
            for (param in executor.parameters) {
                if (param.kind == KParameter.Kind.VALUE)
                    args += KordArgument(param)
            }

            executor.isAccessible = true
        }

        override fun Any.build(guild: Guild?) {
            when (this) {
                is GroupCommandBuilder -> {
                    subCommand(id, description) {
                        for (arg in args)
                            arg.apply { create() }

                        Locale.ALL.forEach { name(it, name) }
                    }
                }

                is RootInputChatBuilder -> {
                    subCommand(id, description) {
                        for (arg in args)
                            arg.apply { create() }

                        Locale.ALL.forEach { name(it, name) }
                    }
                }

                else -> unsupported()
            }
        }

        suspend fun CommandInteraction.execute(guild: Guild) {
            if (permission != null && source missing permission) {
                ephemeral {
                    embed(McColor.DARK_RED) {
                        title = "You do not have the proper permission to use this command!"
                    }
                }

                return
            }

            val out = mutableMapOf<KParameter, Any?>()

            for (arg in args) {
                val result = arg.run { value() }

                result.filterNotNull()
                    .ifPresent {
                        out[arg.param] = it
                    }
            }

            for (param in executor.parameters) {
                when (param.kind) {
                    KParameter.Kind.INSTANCE -> {
                        out[param] = obj
                    }

                    KParameter.Kind.EXTENSION_RECEIVER -> {
                        out[param] = this
                    }

                    KParameter.Kind.VALUE -> Unit
                }
            }

            executor.callSuspendBy(out)
        }
    }
}