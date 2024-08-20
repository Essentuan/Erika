package net.essentuan.erika.observers.player.worlds

import com.busted_moments.buster.api.PlayerType
import com.busted_moments.buster.api.Profile
import com.busted_moments.buster.api.World
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.db.`object`.types.Singleton
import net.essentuan.erika.framework.events.Event
import net.essentuan.erika.db.struct.player.ProfileModel
import net.essentuan.erika.db.struct.player.ProfileModel.Table.external
import net.essentuan.erika.db.struct.player.search.invoke
import net.essentuan.erika.fetch.mojang.username
import net.essentuan.erika.fetch.wynncraft.Identifier
import net.essentuan.erika.fetch.wynncraft.list.playerList
import net.essentuan.erika.observers.player.events.PlayerEvent
import net.essentuan.erika.observers.player.events.WorldEvent
import net.essentuan.esl.collections.builders.list
import net.essentuan.esl.collections.synchronized
import net.essentuan.esl.fetch.fetch
import net.essentuan.esl.get
import net.essentuan.esl.isPresent
import net.essentuan.esl.json.json
import net.essentuan.esl.model.annotations.Ignored
import net.essentuan.esl.other.lock
import net.essentuan.esl.rx.filter
import net.essentuan.esl.rx.filterNotNull
import net.essentuan.esl.rx.forEach
import net.essentuan.esl.rx.map
import net.essentuan.esl.rx.merge
import net.essentuan.esl.scheduling.annotations.Every
import net.essentuan.esl.scheduling.annotations.Lifetime
import net.essentuan.esl.time.duration.minutes
import java.util.UUID
import kotlin.collections.set

private val LOGGER by Logging

object WorldList : Singleton(), World.List {
    private var worlds = mutableMapOf<String, WorldImpl>()
        set(value) {
            players.clear()

            synchronized(this@WorldList) {
                for (world in value.values) {
                    for (profile in world)
                        players[profile.uuid] = world
                }
            }

            field = value
        }

    @Ignored
    private val players = mutableMapOf<UUID, World>().synchronized()

    private data class Difference(
        val uuid: UUID,
        val before: String?,
        val after: String?
    ) {
        private fun join(profile: ProfileModel): List<Event> =
            list {
                val world = worlds.computeIfAbsent(after!!) {
                    val world = WorldImpl(it)

                    +WorldEvent.Start(world)

                    world
                }

                world.players[uuid] = profile
                players[uuid] = world

                +PlayerEvent.Join(world, profile)
            }

        private fun swap(profile: ProfileModel): List<Event> =
            list {
                val world = worlds.computeIfAbsent(after!!) {
                    val world = WorldImpl(it)

                    +WorldEvent.Start(world)

                    world
                }

                world.players[uuid] = profile
                players[uuid] = world

                val old = worlds[before]!!
                old.players.remove(uuid)

                if (old.isEmpty() && old.age > 2.minutes) {
                    worlds.remove(old.name)

                    +WorldEvent.Stop(old)
                }

                +PlayerEvent.Swap(old, world, profile)
            }

        private fun leave(profile: ProfileModel): List<Event> = list {
            players.remove(uuid)

            val old = worlds[before]!!
            old.players.remove(uuid)

            if (old.isEmpty() && old.age > 2.minutes) {
                worlds.remove(old.name)

                +WorldEvent.Stop(old)
            }

            +PlayerEvent.Leave(old, profile)
        }

        fun post(profile: Profile) {
            synchronized(WorldList) {
                when {
                    before == null && after != null -> join(profile as ProfileModel)
                    before != null && after != null -> swap(profile as ProfileModel)
                    before != null && after == null -> leave(profile as ProfileModel)
                    else -> null
                }
            }?.forEach(Event::post)
        }
    }

    @Every(seconds = 10.0)
    @Lifetime(minutes = 1.0)
    private suspend fun update() {
        val playerList = fetch {
            playerList(identifier = Identifier.UUID)
        } ?: return

        val players = playerList.keys.asSequence()
            .plus(players.keys)
            .distinct()
            .map { Difference(it, this[it]?.name, playerList[it]) }
            .filterNot { (_, before, after) -> before == after }
            .associateBy { it.uuid }

        if (players.isEmpty())
            return

        Profile {
            for (difference in players.values) {
                val (uuid, before, _) = difference

                if (before == null) {
                    +uuid
                    continue
                }

                val profile = worlds[before]?.players?.get(uuid)

                if (profile != null) {
                    difference.post(profile)
                    continue
                }

                +uuid
            }
        }.map { (_, profile) -> profile }
            .filter { it.isPresent() }
            .map { it.get() }
            .merge {
                (it as ProfileModel).update(
                    fetch {
                        username(it.uuid)
                    } ?: return@merge null
                )

                it
            }
            .filterNotNull()
            .forEach {
                players.lock { get(it.uuid) }?.post(it)
            }

        UpdateEvent().post()
    }

    override val size: Int
        @Synchronized get() = worlds.size

    @Synchronized
    override fun isEmpty(): Boolean = worlds.isEmpty()

    @Synchronized
    override operator fun get(world: String): World? = worlds[world]
    override operator fun get(world: World): World? = this[world.name]


    override operator fun get(uuid: UUID): World? = players[uuid]
    override operator fun get(player: PlayerType): World? = this[player.uuid]

    @Synchronized
    operator fun contains(world: String): Boolean = world in worlds
    override operator fun contains(element: World): Boolean = element.name in this

    operator fun contains(uuid: UUID): Boolean = uuid in players
    operator fun contains(player: PlayerType): Boolean = player.uuid in this

    override fun containsAll(elements: Collection<World>): Boolean {
        for (world in elements)
            if (world !in this)
                return false

        return true
    }

    override fun iterator(): Iterator<World> = worlds.values.iterator()

    fun World.external() = json {
        "name" to name
        "firstSeen" to firstSeen.time
        "players" to json {
            for (profile in this@external)
                profile.uuid.toString() to profile.external()
        }
    }

    fun World.List.external() = json {
        for (world in this@external)
            world.name to world.external()
    }

    class UpdateEvent : Event()
}