package net.essentuan.erika.observers.territories

import com.busted_moments.buster.api.GuildType
import net.essentuan.erika.buster.listeners.Submission
import net.essentuan.erika.db.struct.territories.ITerritory
import net.essentuan.erika.db.struct.territories.MapVersion
import net.essentuan.erika.db.struct.territories.ResourceType
import net.essentuan.erika.db.struct.territories.Territory
import net.essentuan.erika.db.struct.territories.TerritoryRating
import net.essentuan.erika.fetch.wynncraft.list.BasicTerritory
import net.essentuan.erika.fetch.wynncraft.list.territoryList
import net.essentuan.erika.framework.annotation.Priority
import net.essentuan.erika.framework.console.Logging
import net.essentuan.erika.framework.db.`object`.types.Singleton
import net.essentuan.erika.framework.db.`object`.types.Struct.Companion.enqueue
import net.essentuan.erika.observers.guilds.list.Guilds
import net.essentuan.erika.observers.player.worlds.WorldList
import net.essentuan.erika.observers.territories.events.MapUpdateEvent
import net.essentuan.erika.observers.territories.events.ResourceUpdateEvent
import net.essentuan.erika.observers.territories.events.TerritoryCapturedEvent
import net.essentuan.esl.Rating
import net.essentuan.esl.coroutines.delay
import net.essentuan.esl.fetch.fetch
import net.essentuan.esl.json.json
import net.essentuan.esl.model.annotations.Ignored
import net.essentuan.esl.scheduling.annotations.Every
import net.essentuan.esl.scheduling.annotations.Lifetime
import net.essentuan.esl.time.duration.seconds
import net.essentuan.esl.time.extensions.timeUntil
import java.util.Date
import java.util.EnumMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.math.max
import kotlin.math.min

typealias ITerritory = com.busted_moments.buster.api.Territory
typealias ITerritoryList = com.busted_moments.buster.api.Territory.List
typealias TerritoryRating = com.busted_moments.buster.api.Territory.Rating

private val LOGGER by Logging

@Priority(Rating.LOWEST)
object TerritoryList : Singleton(), ITerritoryList {
    private var territories: Map<String, Territory.State> = emptyMap()
        set(value) {
            field = value

            guilds = value.values.groupBy { it.owner.uuid }
        }

    override var timestamp: Date = Date(0)
        private set

    lateinit var version: MapVersion
        private set

    @Ignored
    override var guilds: Map<UUID, Collection<ITerritory>> = emptyMap()
        private set

    val keys: Set<String>
        get() = territories.keys

    override val size: Int
        get() = territories.size

    override fun isEmpty(): Boolean =
        territories.isEmpty()

    override fun contains(element: ITerritory): Boolean =
        element.name in territories

    override fun containsAll(elements: Collection<ITerritory>): Boolean {
        for (e in elements)
            if (e !in this)
                return false

        return true
    }

    override fun get(territory: String): ITerritory? =
        territories[territory]

    override fun get(territory: ITerritory): ITerritory? =
        territories[territory.name]

    override fun iterator(): Iterator<ITerritory> =
        territories.values.iterator()

    private fun hasBeenCaptured(now: BasicTerritory, before: Territory.State): Boolean =
        when {
            before.owner.uuid == Guilds.NONE.uuid || now.acquired == null -> before.owner.uuid != now.owner.uuid
            else -> before.acquired != now.acquired || (now.owner.uuid == Guilds.NONE.uuid && before.owner.uuid != now.owner.uuid)
        }

    private fun hasChangedState(now: BasicTerritory, before: Territory.State): Boolean {
        if (now.hq != before.hq) return true
        if (now.defences != before.defense) return true

        val newRes = now.resources.associateBy { it.type.toBuster() }
        val oldRes = before.resources

        for (resource in ResourceType.entries) {
            val new = newRes[resource]
            val old = oldRes[resource]

            if (new?.generation != old?.production) return true
            if (new?.stored != old?.stored) return true
            if (new?.limit != old?.capacity) return true
        }

        return false
    }

    @Every
    @Lifetime(minutes = 1.0)
    private suspend fun update() {
        try {
            val new = fetch { territoryList(priority = Rating.CRITICAL) }
            if (new.isNullOrEmpty()) {
                delay(10.seconds)
                return
            }

            val newVersion = MapVersion(new)

            if (!::version.isInitialized)
                version = newVersion

            if (newVersion.id != version.id ||
                new.any { (name, now) ->
                    val before = territories[name]

                    if (before?.owner?.uuid == Guilds.NONE.uuid) before.owner.uuid != now.owner.uuid else before?.acquired != now.acquired
                }
            ) {
                synchronized(LOGGER) {
                    val map = mutableMapOf<String, Territory.State>()
                    val counts = guilds.mapValuesTo(mutableMapOf()) { (_, it) -> AtomicInteger(it.size) }

                    for ((name, now) in new) {
                        val before = territories[name]
                        val captured = before != null && hasBeenCaptured(now, before)

                        when {
                            before == null || captured || hasChangedState(now, before) -> {
                                map[name] = Territory.State(
                                    name,
                                    now.owner.uuid,
                                    if (now.owner.uuid == Guilds.NONE.uuid) new.metadata.cachedAt!! else (now.acquired
                                        ?: new.metadata.cachedAt ?: Date()),
                                    now.resources.associateBy { it.type.toBuster() }
                                        .mapValues { (_, it) ->
                                            Territory.State.Resource(
                                                it.generation,
                                                it.stored,
                                                it.limit
                                            )
                                        },
                                    now.hq,
                                    now.defences,
                                    newVersion
                                ).also {
                                    it.enqueue()

                                    if (captured) {
                                        TerritoryCapturedEvent(
                                            before,
                                            before?.owner?.uuid?.let { uuid -> counts[uuid]?.decrementAndGet() } ?: 0,
                                            it,
                                            counts.computeIfAbsent(it.owner.uuid) { AtomicInteger(0) }.incrementAndGet()
                                        ).post()
                                    }
                                }
                            }

                            newVersion.id != version.id -> {
                                val same = newVersion.template[name]?.let {
                                    for ((resource, prod) in it.base)
                                        if (version.template[name]?.base?.get(resource) != prod)
                                            return@let false

                                    true
                                } ?: false

                                map[name] = Territory.State(
                                    name,
                                    before.owner.uuid,
                                    before.acquired,
                                    if (same)
                                        before.resources
                                            .mapValues { (_, storage) ->
                                                Territory.State.Resource(
                                                    storage.production,
                                                    storage.stored,
                                                    storage.capacity
                                                )
                                            }
                                    else
                                        emptyMap(),
                                    same && before.hq,
                                    if (same) before.defense else TerritoryRating.VERY_LOW,
                                    newVersion
                                ).also {
                                    it.enqueue()
                                }
                            }

                            else -> map[name] = before
                        }
                    }

                    territories = map
                    version = newVersion
                    timestamp = new.metadata.cachedAt!!
                }

                MapUpdateEvent().post()
            }

            delay(new.metadata.expires?.timeUntil() ?: 10.seconds)
        } catch (ex: Exception) {
            LOGGER.error("Error processing territory list", ex)

            delay(10.seconds)

            return
        }
    }

//    fun submit(submission: Submission) {
//        if (submission.profiles.any { (name, territory) -> name != territory.name })
//            return
//
//        synchronized(LOGGER) {
//            val version = MapVersion(WorldList[submission.world], submission.profiles)
//
//            val new = mutableMapOf<String, Territory.State>()
//
//            for ((name, profile) in submission.profiles) {
//                val territory = territories[name]!!
//
//                if ((territory.owner.uuid != Guilds.NONE.uuid || profile.owner != "No owner") && territory.owner.name != profile.owner)
//                    new[name] = territory
//                else {
//                    new[name] = Territory.State(
//                        name,
//                        territory.owner.uuid,
//                        territory.acquired,
//                        EnumMap<ResourceType, Territory.State.Resource>(ResourceType::class.java).also {
//                            for (resource in ResourceType.entries) {
//                                val resources = profile.resources[resource]!!
//
//                                it[resource] = Territory.State.Resource(
//                                    resources.production,
//                                    resources.stored,
//                                    resources.capacity
//                                )
//                            }
//                        },
//                        profile.hq,
//                        profile.defense,
//                        version
//                    ).also { it.enqueue() }
//                }
//            }
//
//            territories = new
//            TerritoryList.version = version
//            timestamp = Date()
//        }
//
//        ResourceUpdateEvent(submission.world, submission.by, submission.profiles).post()
//        MapUpdateEvent().post()
//    }

    fun ITerritory.external() = json {
        "name" to name
        "acquired" to acquired.time
        "owner" {
            "name" to owner.name
            "tag" to owner.tag
            "uuid" to owner.uuid
        }
        "location" {
            "start" {
                "x" to min(
                    location.start.x,
                    location.end.x
                )
                "z" to min(
                    location.start.z,
                    location.end.z
                )
            }

            "end" {
                "x" to max(
                    location.start.x,
                    location.end.x
                )
                "z" to max(
                    location.start.z,
                    location.end.z
                )
            }
        }

        "resources" {
            for ((resource, storage) in resources) {
                (resource.toString()) {
                    "base" to storage.base.coerceAtLeast(0)
                    "production" to storage.production.coerceAtLeast(0)
                    "stored" to storage.stored.coerceAtLeast(0)
                    "capacity" to storage.capacity.coerceAtLeast(0)
                }
            }
        }

        "defense" to defense.toString()
        "hq" to hq
        "connections" to connections.toList()
    }

    fun ITerritoryList.external() = json {
        "territories" {
            for (territory in this@external)
                territory.name to territory.external()
        }

        "timestamp" to timestamp.time
    }
}

val GuildType.territories: Collection<ITerritory>
    get() = TerritoryList.guilds[this.uuid] ?: emptyList()