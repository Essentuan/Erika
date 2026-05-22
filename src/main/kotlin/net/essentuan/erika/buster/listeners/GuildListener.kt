package net.essentuan.erika.buster.listeners

import com.busted_moments.buster.api.GuildType
import com.busted_moments.buster.protocol.clientbound.ClientboundTerritoryAttackedPacket
import com.busted_moments.buster.protocol.serverbound.ServerboundTerritoryAttackedPacket
import com.busted_moments.buster.types.guilds.AttackTimer
import com.google.common.collect.SetMultimap
import net.essentuan.erika.buster.BusterService
import net.essentuan.erika.buster.BusterService.socket
import net.essentuan.erika.buster.Listener
import net.essentuan.erika.buster.Socket
import net.essentuan.erika.buster.events.BusterEvent
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.db.`object`.types.Singleton
import net.essentuan.erika.framework.events.annotations.Subscribe
import net.essentuan.erika.framework.events.events
import net.essentuan.erika.observers.guilds.events.GuildEvent
import net.essentuan.erika.observers.guilds.list.Guilds
import net.essentuan.erika.observers.territories.TerritoryList
import net.essentuan.erika.observers.territories.events.TerritoryCapturedEvent
import net.essentuan.esl.collections.multimap.Multimaps
import net.essentuan.esl.collections.multimap.hashSetValues
import net.essentuan.esl.iteration.extensions.iterate
import net.essentuan.esl.json.Json
import net.essentuan.esl.json.json
import net.essentuan.esl.model.Model.Companion.export
import net.essentuan.esl.model.annotations.Ignored
import net.essentuan.esl.other.lock
import net.essentuan.esl.scheduling.annotations.Every
import net.essentuan.esl.scheduling.tasks
import net.essentuan.esl.time.duration.minutes
import net.essentuan.esl.time.duration.seconds
import java.util.UUID

object GuildListener : Singleton() {
    var guilds = mutableMapOf<UUID, BusterGuild>()

    val Socket.guild: BusterGuild?
        get() {
            val uuid = account.member.guild?.uuid ?: return null

            return guilds.lock {
                computeIfAbsent(uuid) {
                    BusterGuild(uuid)
                }
            }
        }

    private fun Socket.join() {
        val guild = guild ?: return

        guild.lock {
            this[account.profile.uuid] = this@join

            tasks.resume()
            events.register()
        }

        guild.clean()

        for (timer in guild.lock { timers.values().toList() })
            send(ClientboundTerritoryAttackedPacket(timer))
    }

    @Subscribe
    private fun BusterEvent.Connect.on() {
        socket.join()
    }

    @Subscribe
    private fun GuildEvent.Member.Join.on() {
        member.socket?.join()
    }

    @Listener
    private fun Socket.on(packet: ServerboundTerritoryAttackedPacket) {
        val guild = guild ?: return

        if (packet.timer.remaining < 0.seconds)
            return

        synchronized(guild) {
            val timer = packet.timer
            if (timer.territory !in TerritoryList) {
                Logging.info("No territory found for ${timer.territory}")

                if (timer.trusted || guild.find(timer, false) != null)
                    return

                val territory = TerritoryList.firstOrNull { it.name.startsWith(timer.territory) } ?: return

                if (guild.find(timer, territory = territory.name) == null)
                    guild.enqueue(
                        timer.copy(
                            territory = territory.name,
                            defense = territory.defense,
                        )
                    )
            } else {
                val previous = guild.find(timer)

                if (previous == null)
                    guild.enqueue(timer)
                else if (!previous.trusted && timer.trusted)
                    guild.enqueue(previous.copy(defense = timer.defense, trusted = true))
            }
        }
    }
}

class BusterGuild(
    override val uuid: UUID,
    timers: Set<AttackTimer> = emptySet()
) : Json.Model, GuildType, MutableMap<UUID, Socket> by mutableMapOf() {
    @Ignored
    private val type by lazy { Guilds[uuid] ?: Guilds.UNKOWN }

    @Ignored
    val timers: SetMultimap<String, AttackTimer> =
        Multimaps.hashKeys().hashSetValues()

    init {
        for (timer in timers)
            if (!timer.completed)
                this.timers[timer.territory] += timer
    }

    override fun save(data: Json): Json =
        json(data) {
            "timers" to timers.values().map { it.export() }
        }

    @Synchronized
    fun find(timer: AttackTimer, strict: Boolean = true, territory: String = timer.territory): AttackTimer? {
        return if (strict) {
            timers[territory].firstOrNull { (it.remaining - timer.remaining).abs() < BusterService.marginOfError }
        } else
            timers.values().firstOrNull {
                it.territory.startsWith(territory) && (it.remaining - timer.remaining).abs() < BusterService.marginOfError
            }
    }

    fun enqueue(timer: AttackTimer) {
        lock {
            val duplicate = find(timer)

            if (duplicate != null)
                timers.remove(timer.territory, duplicate)

            timers.put(timer.territory, timer)

            values.toList()
        } iterate {
            it.send(ClientboundTerritoryAttackedPacket(timer))
        }
    }

    @Synchronized
    @Every(seconds = 1.0)
    fun clean() {
        values.removeIf { !it.isOpen }
        timers.values().removeIf { (it.remaining + 1.5.seconds) < 0.seconds }

        if (timers.isEmpty && isEmpty()) {
            GuildListener.guilds.lock { remove(uuid) }

            tasks.close()
            events.unregister()
        }
    }

    @Subscribe
    @Synchronized
    private fun TerritoryCapturedEvent.on() {
        timers.removeAll(territory)
    }

    @Subscribe
    private fun GuildEvent.Member.Leave.on() {
        if (before.uuid != uuid)
            return

        lock { remove(member.uuid) }
    }

    @Subscribe
    @Synchronized
    private fun BusterEvent.Disconnect.on() {
        remove(socket.account.profile.uuid)
    }

    override val name: String
        get() = type.name
    override val tag: String
        get() = type.tag
}