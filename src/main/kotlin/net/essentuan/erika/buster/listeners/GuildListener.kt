package net.essentuan.erika.buster.listeners

import com.busted_moments.buster.api.GuildType
import com.busted_moments.buster.protocol.clientbound.ClientboundTerritoryAttackedPacket
import com.busted_moments.buster.protocol.serverbound.ServerboundTerritoryAttackedPacket
import com.busted_moments.buster.types.guilds.AttackTimer
import com.google.common.collect.SetMultimap
import net.essentuan.erika.buster.BusterService.socket
import net.essentuan.erika.buster.Listener
import net.essentuan.erika.buster.Socket
import net.essentuan.erika.buster.events.BusterEvent
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
    val guilds = mutableMapOf<UUID, BusterGuild>()

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

        val timer = packet.timer
        if (timer.territory !in TerritoryList) {
            if (timer.trusted || guild.lock {
                    timers.values()
                        .any { it.territory.startsWith(timer.territory) && (it.remaining - timer.remaining).abs() < 1.minutes }
                })
                return

            val territory = TerritoryList.firstOrNull { it.name.startsWith(timer.territory) } ?: return

            guild.lock {
                if (timer !in timers[territory.name])
                    enqueue(
                        timer.copy(
                            territory = territory.name,
                            defense = territory.defense,
                        )
                    )
            }
        } else {
            guild.lock {
                val previous = timers[timer.territory].firstOrNull {
                    it.territory == timer.territory && (it.remaining - timer.remaining).abs() < 1.minutes
                }

                if (previous == null)
                    enqueue(timer)
                else if (!previous.trusted && timer.trusted)
                    enqueue(previous.copy(defense = timer.defense, trusted = true))

            }
        }
    }
}

class BusterGuild(
    override val uuid: UUID,
    timers: Set<AttackTimer> = emptySet()
) : Json.Model, GuildType by Guilds[uuid]!!, MutableMap<UUID, Socket> by mutableMapOf() {
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

    fun enqueue(timer: AttackTimer) {
        lock {
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
}