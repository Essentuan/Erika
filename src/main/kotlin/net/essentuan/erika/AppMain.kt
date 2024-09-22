package net.essentuan.erika

import com.busted_moments.buster.Buster
import kotlinx.coroutines.*
import net.essentuan.erika.commands.MEMORY_DEBUG_ENABLED
import net.essentuan.erika.framework.console.Commands
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.events.Event
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.observers.guilds.events.GuildEvent
import net.essentuan.erika.observers.player.events.PlayerEvent
import net.essentuan.erika.observers.player.events.WorldEvent
import net.essentuan.erika.observers.territories.events.ResourceUpdateEvent
import net.essentuan.erika.observers.territories.events.TerritoryCapturedEvent
import net.essentuan.esl.future.api.CompletionException
import net.essentuan.esl.future.api.Future
import net.essentuan.esl.other.lock
import net.essentuan.esl.reflections.Reflections
import net.essentuan.esl.scheduling.Scheduler
import net.essentuan.esl.scheduling.annotations.Every
import net.essentuan.esl.scheduling.annotations.Lifetime
import net.essentuan.esl.string.reader.consume
import net.essentuan.esl.time.TimeUnit
import net.essentuan.esl.time.duration.FormatFlag
import net.essentuan.esl.time.duration.minutes
import net.essentuan.esl.time.duration.seconds
import net.essentuan.esl.time.extensions.timeSince
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.util.Date
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.collections.set
import kotlin.concurrent.thread
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.io.path.exists
import kotlin.system.exitProcess

val cache: MutableMap<String, String> = mutableMapOf()
private var ready = false
private val listeners = mutableListOf<Continuation<Unit>>()

val ClassLoader = Argument::class.java.classLoader

@OptIn(DelicateCoroutinesApi::class)
fun main(args: Array<String>) {
    val reader = args.joinToString(separator = " ").consume()

    reader.skipWhile { c -> c != '-' }
    reader.skip()

    while (reader.canRead()) {
        val arg = reader.readUntil { c -> c == ' ' || c == '=' }

        if (reader.canRead() && reader.peek() == '=')
            reader.skip()

        cache[arg] = reader.readUntil { c -> c == '-' }.trim()

        if (reader.canRead() && reader.peek() == '-')
            reader.skip()
    }
    
    Buster

    Commands.start()

    Scheduler.apply {
        capacity = 50
        DISPATCHER = CoroutineScope(newFixedThreadPoolContext(20, "scheduler"))

        this += Logging

        start()
    } finally { exitProcess(0) }

    Event.Bus.start()

    ReadyEvent.post()

    Logging.info("Finished loading!")

    Runtime.getRuntime().addShutdownHook(
        thread(
            name = "Shutdown Hook",
            start = false
        ) {
            Scheduler.shutdown()
            ShutdownEvent.post()
        })

    ready = true
    listeners.forEach { it.resume(Unit) }

    thread(name = "Heartbeat", start = true, isDaemon = true) {
        if (tick.timeSince() > 60.seconds) {
            Logging.error("Scheduler has stopped running tasks!")

            exitProcess(0)
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun scope(name: String, threads: Int): CoroutineScope =
    CoroutineScope(newFixedThreadPoolContext(threads, name))

private var last: Date = Date()

@Every(ms = 500.0)
@Lifetime(seconds = 10.0)
private fun gc() {
    val max = Runtime.getRuntime().maxMemory()
    val used = Runtime.getRuntime().totalMemory()
    val perc = (used / max.toDouble()) * 100

    val left = when (perc) {
        in 0.0..45.0 -> 1.minutes
        in 45.0..60.0 -> 30.seconds
        in 60.0..70.0 -> 15.seconds
        in 70.0..80.0 -> 5.seconds
        else -> 1.seconds
    }

    if (last.timeSince() < left) {
        if (MEMORY_DEBUG_ENABLED)
            Logging.info("Memory: ${used}/${max} (${perc.toInt()}) | ${(left - last.timeSince()).print(FormatFlag.COMPACT)} until next GC!")

        return
    }

    if (MEMORY_DEBUG_ENABLED)
        Logging.info("Running GC")

    Runtime.getRuntime().gc()

    last = Date()
}

private var tick = Date()

@Every(ms = 500.0)
private fun tick() {
    tick = Date()
}

suspend fun awaitReady() {
    if (ready)
        return

    suspendCoroutine<Unit> { listeners.lock { add(it) } }
}

//@Subscribe
//private fun PlayerEvent.Join.listen() {
//    Logging.info(
//        "${profile.name} has joined ${world.name} (${world.age.print(FormatFlag.COMPACT)} old)"
//    )
//}
//
//@Subscribe
//private fun PlayerEvent.Swap.listen() {
//    Logging.info(
//        "${
//            profile.name
//        } has joined ${
//            world.name
//        } (${
//            world.age.print(FormatFlag.COMPACT)
//        } old) from ${old.name} (${old.age.print(FormatFlag.COMPACT)})"
//    )
//}
//
//@Subscribe
//private fun PlayerEvent.Leave.listen() {
//    Logging.info(
//        "${profile.name} has left ${world.name} (${world.age.print(FormatFlag.COMPACT)} old)"
//    )
//}

@Subscribe
private fun PlayerEvent.Update.listen() {
    Logging.info(
        "$old has changed their name to ${profile.name}"
    )
}

@Subscribe
private fun WorldEvent.Start.listen() {
    Logging.info(
        "${world.name} has started!"
    )
}

@Subscribe
private fun WorldEvent.Stop.listen() {
    Logging.info(
        "${world.name} has stopped after ${world.age.print(FormatFlag.COMPACT)}!"
    )
}

@Subscribe
private fun GuildEvent.Created.listen() {
    Logging.info(
        "${guild.name} [${guild.tag}] has been created!"
    )
}

@Subscribe
private fun GuildEvent.Deleted.listen() {
    Logging.info(
        "${guild.name} [${guild.tag}] has been deleted!"
    )
}

@Subscribe
private fun GuildEvent.NameChange.listen() {
    Logging.info(
        "$old [${guild.tag}] name has been changed to ${guild.name}"
    )
}

@Subscribe
private fun GuildEvent.TagChange.listen() {
    Logging.info(
        "${guild.name} [$old] tag has been changed to ${guild.tag}"
    )
}

@Subscribe
private fun GuildEvent.XpGained.listen() {
    Logging.info(
        "${guild.name} [${guild.tag}] has gained $xp xp!"
    )
}

@Subscribe
private fun GuildEvent.LevelUp.listen() {
    Logging.info(
        "${guild.name} [${guild.tag}] has leveled up from $before to $after!"
    )
}

@Subscribe
private fun GuildEvent.Member.Join.listen() {
    Logging.info(
        "${member.profile.name} has joined ${guild.name} [${guild.tag}]!"
    )
}

@Subscribe
private fun GuildEvent.Member.Contributed.listen() {
    Logging.info(
        "${member.profile.name} has contributed ${xp}xp to ${guild.name} [${guild.tag}]!"
    )
}

@Subscribe
private fun GuildEvent.Member.Promoted.listen() {
    Logging.info(
        "${member.profile.name} has been promoted to $after from $before in ${guild.name} [${guild.tag}]!"
    )
}

@Subscribe
private fun GuildEvent.Member.Demoted.listen() {
    Logging.info(
        "${member.profile.name} has been demoted to $after from $before in ${guild.name} [${guild.tag}]!"
    )
}

@Subscribe
private fun GuildEvent.Member.Leave.listen() {
    Logging.info(
        "${member.profile.name} has left ${guild.name} [${guild.tag}]!"
    )
}

@Subscribe
private fun TerritoryCapturedEvent.on() {
    if (before == null)
        return

    Logging.info(
        "$territory | ${
            before.owner.name
        } [${
            before.owner.tag
        }] ($beforeCount) -> ${
            after.owner.name
        } [${
            after.owner.tag
        }] ($afterCount) | Held for ${
            before.acquired.timeSince().print(FormatFlag.COMPACT, TimeUnit.SECONDS)
        }"
    )
}

@Subscribe
private fun ResourceUpdateEvent.on() {
    Logging.info("Resource submission by ${by.joinToString { it.toString() }} on $world")
}

inline fun <T> arg(str: String, default: T, crossinline init: (String) -> T) = arg(str) {
    if (it == null)
        default
    else
        init(it)
}

inline fun <T> arg(str: String, crossinline init: (String?) -> T) = object : Argument<T> {
    val lazy = lazy { init(cache[str]) }

    override val arg: String = str
    override val value: T
        get() = lazy.value
    override val present: Boolean = arg in cache

    override fun toString(): String = "$arg[${if (present) value.toString() else ""}]"
}

fun arg(str: String): Argument<String> = arg(str) { it ?: "" }

inline fun <T> inline(crossinline block: suspend () -> T): Future<T> = Future(block).also {
    it.except { ex -> Logging.error("Exception in future!", CompletionException(it, cause = ex)) }
}
